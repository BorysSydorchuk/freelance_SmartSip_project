from gpiozero import MCP3008, Button
import RPi.GPIO as GPIO
import time
import requests
import socket
import threading
import servo

# ── GPIO / ADC init ───────────────────────────────────────────────────────────
WATER_PUMP_GPIO_COLD = 15
WATER_PUMP_GPIO_HOT  = 18
adc    = MCP3008(channel=0)
button = Button(21)

GPIO.setmode(GPIO.BCM)
GPIO.setup(WATER_PUMP_GPIO_COLD, GPIO.OUT)
GPIO.output(WATER_PUMP_GPIO_COLD, 0)
GPIO.setup(WATER_PUMP_GPIO_HOT, GPIO.OUT)
GPIO.output(WATER_PUMP_GPIO_HOT, 0)

# ── Temperature sensor paths ──────────────────────────────────────────────────
SENSOR_MAP = {
    "cold": "/sys/bus/w1/devices/28-00000f030c5d",
    "hot":  "/sys/bus/w1/devices/28-00001070288a",
}

# ── Scale linearisation ───────────────────────────────────────────────────────
ZERO_OFFSET     = 1.024
FULL_SCALE_VOLT = 1.385
KNOWN_MASS      = 500   # grams at FULL_SCALE_VOLT

# ── DB config ─────────────────────────────────────────────────────────────────
BASE_URL      = "https://studev.groept.be/api/a25EE2team203"
POLL_INTERVAL = 2.0  # seconds between DB polls

# ── TCP server config (bottle Pi connects here to send FULL signal) ───────────
HOST = "0.0.0.0"
PORT = 5000

# ── Shared state ──────────────────────────────────────────────────────────────
stop_fill_event   = threading.Event()   # set by TCP when bottle Pi sends "FULL"
currently_dispensing = False
state_lock        = threading.Lock()


# ════════════════════════════════════════════════════════════════════════════════
# TEMPERATURE SENSORS
# ════════════════════════════════════════════════════════════════════════════════

def read_temp_c(device_path: str) -> float | None:
    """Read one DS18B20 sensor. Returns °C or None on error."""
    try:
        with open(device_path + "/w1_slave", "r") as f:
            lines = f.read().strip().splitlines()
    except OSError:
        return None

    if len(lines) < 2 or not lines[0].endswith("YES"):
        return None

    try:
        return float(lines[1].split("t=")[-1]) / 1000.0
    except (ValueError, IndexError):
        return None


def read_tank_temps() -> tuple[float | None, float | None]:
    """Returns (temp_cold, temp_hot) in °C. Either can be None on sensor error."""
    temp_cold = read_temp_c(SENSOR_MAP["cold"])
    temp_hot  = read_temp_c(SENSOR_MAP["hot"])
    print(f"Tank temps → cold: {temp_cold} °C   hot: {temp_hot} °C")
    return temp_cold, temp_hot


# ════════════════════════════════════════════════════════════════════════════════
# CALORIMETRY
# Adapted from the team's calorimetry module.
# All volumes in ml, temperatures in °C.
# Returns mass of cold water needed in grams, or None on error.
# ════════════════════════════════════════════════════════════════════════════════

TOTAL_BOTTLE_VOLUME_ML = 500.0  # ← set this once you measure your bottle

def calculate_cold_mass(
    volume_in_bottle_ml: float,
    temp_in_bottle: float,
    temp_cold_tank: float,
    temp_hot_tank: float,
    desired_temp: float,
) -> float | None:
    """
    Calorimetry for a partially filled bottle.
    Returns grams of cold water to add, or None if the target is unreachable.

    Derivation: energy balance
        m_already * T_bottle + m_cold * T_cold + m_hot * T_hot
            = (m_already + m_cold + m_hot) * T_desired
    with the constraint:
        m_cold + m_hot = m_to_add  (= total capacity - current volume, in grams)
    """
    if not (temp_cold_tank <= desired_temp <= temp_hot_tank):
        print(f"[CALORIMETRY] Target {desired_temp}°C is outside tank range "
              f"({temp_cold_tank}–{temp_hot_tank} °C)")
        return None

    mass_full_bottle  = TOTAL_BOTTLE_VOLUME_ML        # 1 ml water ≈ 1 g
    mass_in_bottle    = volume_in_bottle_ml
    mass_to_add       = mass_full_bottle - mass_in_bottle

    if mass_to_add <= 0:
        print("[CALORIMETRY] Bottle already full.")
        return None

    # Energy balance → solve for mass_cold
    # mass_cold = (mass_full * T_desired - mass_in_bottle * T_bottle - mass_to_add * T_hot)
    #             / (T_cold - T_hot)
    numerator   = (mass_full_bottle * desired_temp
                   - mass_in_bottle * temp_in_bottle
                   - mass_to_add    * temp_hot_tank)
    denominator = temp_cold_tank - temp_hot_tank   # always negative

    mass_cold = numerator / denominator

    if mass_cold < 0:
        print("[CALORIMETRY] Too much hot water already in bottle to reach target.")
        return None
    if mass_cold > mass_to_add:
        print("[CALORIMETRY] Too much cold water already in bottle to reach target.")
        return None

    return mass_cold


# ════════════════════════════════════════════════════════════════════════════════
# PUMP HELPERS
# ════════════════════════════════════════════════════════════════════════════════

def get_mass_from_adc() -> float:
    voltage = adc.value * 3.3
    return (voltage - ZERO_OFFSET) * (KNOWN_MASS / (FULL_SCALE_VOLT - ZERO_OFFSET))


def pump_on(gpio):
    GPIO.output(gpio, 1)
    print(f"Pump ON (GPIO {gpio})")


def pump_off(gpio):
    GPIO.output(gpio, 0)
    print(f"Pump OFF (GPIO {gpio})")


# ════════════════════════════════════════════════════════════════════════════════
# DISPENSE FUNCTIONS
# ════════════════════════════════════════════════════════════════════════════════

def dispense_cold_by_weight(mass_requested_g: float):
    """
    Run the cold pump until the scale registers mass_requested_g grams dispensed.
    Uses the weight sensor (ADC) for precise volume control.
    """
    global currently_dispensing

    stop_fill_event.clear()
    clock        = time.time
    prev_time    = clock()
    mass_initial = get_mass_from_adc()
    pump_is_on   = False

    print(f"[COLD] Dispensing {mass_requested_g:.0f} g  (initial scale: {mass_initial:.1f} g)")

    with state_lock:
        currently_dispensing = True

    try:
        while True:
            if prev_time + 0.2 < clock():
                mass_now       = get_mass_from_adc()
                mass_dispensed = mass_initial - mass_now   # scale goes down as bottle fills

                if mass_dispensed < mass_requested_g:
                    if not pump_is_on:
                        servo.lock_clamp()
                        pump_on(WATER_PUMP_GPIO_COLD)
                        pump_is_on = True
                    print(f"[COLD] {mass_requested_g - mass_dispensed:.0f} g remaining")
                else:
                    print("[COLD] Target mass reached.")
                    break

                prev_time = clock()
    finally:
        servo.open_clamp()
        pump_off(WATER_PUMP_GPIO_COLD)
        with state_lock:
            currently_dispensing = False


def dispense_hot_until_full():
    """
    Run the hot pump until the bottle Pi sends a "FULL" TCP message.
    stop_fill_event is set by the TCP handler when "FULL" is received.
    """
    global currently_dispensing

    stop_fill_event.clear()
    pump_is_on = False

    print("[HOT] Running hot pump until bottle full signal...")

    with state_lock:
        currently_dispensing = True

    try:
        while not stop_fill_event.is_set():
            if not pump_is_on:
                servo.lock_clamp()
                pump_on(WATER_PUMP_GPIO_HOT)
                pump_is_on = True
            time.sleep(0.05)   # tight loop, event-driven stop
        print("[HOT] FULL signal received — stopping hot pump.")
    finally:
        pump_off(WATER_PUMP_GPIO_HOT)
        servo.open_clamp()
        with state_lock:
            currently_dispensing = False


# ════════════════════════════════════════════════════════════════════════════════
# TCP SERVER  (receives FULL / HEARTBEAT / NOT_FULL from bottle Pi)
# ════════════════════════════════════════════════════════════════════════════════

def process_tcp_message(msg: str) -> str:
    msg = msg.strip()
    print(f"[TCP] Received: {msg}")

    if msg == "FULL":
        stop_fill_event.set()
        return "ACK_FULL"
    elif msg == "HEARTBEAT":
        return "ACK_HEARTBEAT"
    elif msg == "NOT_FULL":
        return "ACK_NOT_FULL"
    else:
        return "ACK_UNKNOWN"


def handle_tcp_client(conn, addr):
    try:
        data = conn.recv(1024)
        if data:
            reply = process_tcp_message(data.decode("utf-8"))
            conn.sendall(reply.encode("utf-8"))
    except Exception as e:
        print(f"[TCP] Client error: {e}")
    finally:
        conn.close()


def tcp_server_loop():
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind((HOST, PORT))
    server.listen(5)
    print(f"[TCP] Server listening on {HOST}:{PORT}")
    while True:
        conn, addr = server.accept()
        threading.Thread(target=handle_tcp_client, args=(conn, addr), daemon=True).start()


# ════════════════════════════════════════════════════════════════════════════════
# DB HELPERS
# ════════════════════════════════════════════════════════════════════════════════

def get_latest_request() -> dict | None:
    """
    Fetches the latest row from the refill request table.
    Expected JSON:
    [{
        "ID":                    5,
        "bottle_volume_current": 200.0,   ← ml currently in bottle
        "bottle_temp":           18.5,    ← °C current water temp
        "target_temp":           25.0,    ← °C desired (0.0 if cold/hot mode)
        "tank_mode":             "mix",   ← "cold" | "hot" | "mix"
        "RefillResponse":        "pending"
    }]
    """
    try:
        response = requests.get(f"{BASE_URL}/getRefillStatus", timeout=5)
        if response.status_code != 200:
            print(f"[DB] Poll failed: HTTP {response.status_code}")
            return None
        data = response.json()
        return data[0] if data else None
    except Exception as e:
        print(f"[DB] Poll error: {e}")
        return None


def mark_request_done(request_id: int):
    """Marks the refill request as done so the Java app stops polling."""
    try:
        response = requests.get(f"{BASE_URL}/setRefillStatus/done", timeout=5)
        if response.status_code != 200:
            print(f"[DB] Failed to mark done: HTTP {response.status_code}")
        else:
            print(f"[DB] Request {request_id} marked as done.")
    except Exception as e:
        print(f"[DB] Mark done error: {e}")


def log_refill_history(ml_dispensed: float, tank_mode: str):
    """
    Writes a row to the refill history table for usage tracking.
    Table columns: timestamp (auto), ml_dispensed, tank_mode
    Replace 'insertRefillHistory' with your actual endpoint name.
    """
    try:
        url = (f"{BASE_URL}/insertRefillHistory"
               f"/{ml_dispensed:.0f}"
               f"/{tank_mode}")
        response = requests.get(url, timeout=5)
        if response.status_code != 200:
            print(f"[DB] History log failed: HTTP {response.status_code}")
        else:
            print(f"[DB] History logged: {ml_dispensed:.0f} ml  mode={tank_mode}")
    except Exception as e:
        print(f"[DB] History log error: {e}")


# ════════════════════════════════════════════════════════════════════════════════
# MAIN LOOP
# ════════════════════════════════════════════════════════════════════════════════

def handle_request(request: dict):
    """
    Decide what to dispense based on tank_mode, then execute.

    cold → dispense (FULL_VOLUME - current_volume) g from cold tank by weight
    hot  → run hot pump until bottle Pi sends FULL
    mix  → calorimetry to find cold mass, dispense cold by weight, then hot until FULL
    """
    req_id               = request["ID"]
    bottle_volume_current = float(request["bottle_volume_current"])  # ml
    bottle_temp          = float(request["bottle_temp"])             # °C
    target_temp          = float(request["target_temp"])             # °C (0 if not mix)
    tank_mode            = request["tank_mode"]                      # "cold"|"hot"|"mix"

    ml_to_fill = TOTAL_BOTTLE_VOLUME_ML - bottle_volume_current

    print(f"\n[REQUEST #{req_id}] mode={tank_mode}  bottle={bottle_volume_current:.0f}/{TOTAL_BOTTLE_VOLUME_ML:.0f} ml  "
          f"bottleTemp={bottle_temp:.1f}°C  targetTemp={target_temp:.1f}°C")

    stop_fill_event.clear()

    if tank_mode == "cold":
        # Fill entirely with cold water by weight
        dispense_cold_by_weight(ml_to_fill)
        log_refill_history(ml_to_fill, tank_mode)

    elif tank_mode == "hot":
        # Run hot pump until bottle Pi signals full
        dispense_hot_until_full()
        log_refill_history(ml_to_fill, tank_mode)

    elif tank_mode == "mix":
        temp_cold, temp_hot = read_tank_temps()

        if temp_cold is None or temp_hot is None:
            print("[MIX] Sensor read failed — aborting refill.")
            mark_request_done(req_id)
            return

        mass_cold = calculate_cold_mass(
            volume_in_bottle_ml = bottle_volume_current,
            temp_in_bottle      = bottle_temp,
            temp_cold_tank      = temp_cold,
            temp_hot_tank       = temp_hot,
            desired_temp        = target_temp,
        )

        if mass_cold is None:
            print("[MIX] Calorimetry failed — cannot reach target temp. Aborting.")
            mark_request_done(req_id)
            return

        print(f"[MIX] Calorimetry → cold: {mass_cold:.0f} g   hot: {ml_to_fill - mass_cold:.0f} g")

        # Step 1: dispense cold water precisely by weight
        dispense_cold_by_weight(mass_cold)

        # Step 2: top up with hot water until bottle Pi says full
        dispense_hot_until_full()

        log_refill_history(ml_to_fill, tank_mode)

    else:
        print(f"[REQUEST] Unknown tank_mode '{tank_mode}' — skipping.")

    mark_request_done(req_id)
    print("[REQUEST] Refill complete.\n")


def main_loop():
    print("Station ready. Polling for refill requests...")
    while True:
        request = get_latest_request()

        if request is not None and request.get("RefillResponse") == "pending":
            handle_request(request)
        else:
            print("No pending request. Waiting...")

        time.sleep(POLL_INTERVAL)


# ════════════════════════════════════════════════════════════════════════════════
# ENTRY POINT
# ════════════════════════════════════════════════════════════════════════════════

if __name__ == "__main__":
    try:
        threading.Thread(target=tcp_server_loop, daemon=True).start()
        main_loop()
    except KeyboardInterrupt:
        print("Shutting down...")
    finally:
        pump_off(WATER_PUMP_GPIO_COLD)
        pump_off(WATER_PUMP_GPIO_HOT)
        GPIO.cleanup()
