from gpiozero import MCP3008
from gpiozero import Button
import RPi.GPIO as GPIO
import time
import requests
import socket
import threading

# GPIO / ADC init
WATER_PUMP_GPIO_COLD = 15
WATER_PUMP_GPIO_HOT=18
adc = MCP3008(channel=0)
button = Button(21)
#initialise water pump
GPIO.setmode(GPIO.BCM)
GPIO.setup(WATER_PUMP_GPIO_COLD, GPIO.OUT) #cold is right tank when you look at faucet
GPIO.output(WATER_PUMP_GPIO_COLD, 0)
GPIO.setup(WATER_PUMP_GPIO_HOT,GPIO.OUT)
GPIO.output(WATER_PUMP_GPIO_HOT,0)

# TCP server config (Pi 2 = server)
HOST = "0.0.0.0"
PORT = 5000


# Linearisation
ZERO_OFFSET = 1.024
FULL_SCALE_VOLT = 1.385
KNOWN_MASS = 500


# DB communication config
BASE_URL = "https://studev.groept.be/api/a25EE2team203"
POLL_INTERVAL = 2.0


# Shared state
stop_fill_event = threading.Event()
currently_dispensing = False
state_lock = threading.Lock()


def get_mass(v):
    return (v - ZERO_OFFSET) * (KNOWN_MASS / (FULL_SCALE_VOLT - ZERO_OFFSET))


def pump_on(Pump_GPIO):
    GPIO.output(Pump_GPIO, 1)
    print("Pump ON")


def pump_off(Pump_GPIO):
    GPIO.output(Pump_GPIO, 0)
    print("Pump OFF")



# TCP message handling

def process_client_message(msg: str) -> str:
    global currently_dispensing

    msg = msg.strip()
    print(f"[TCP] Received message: {msg}")

    if msg == "FULL":
        print("FULL received -> stopping pump immediately")
        stop_fill_event.set()
        return "ACK_FULL"

    elif msg == "HEARTBEAT":
        return "ACK_HEARTBEAT"

    elif msg == "NOT_FULL":
        return "ACK_NOT_FULL"

    else:
        return "ACK_UNKNOWN"


def handle_client(conn, addr):
    try:
        data = conn.recv(1024)
        if not data:
            return

        msg = data.decode("utf-8").strip()
        print(f"Connected by {addr}")
        reply = process_client_message(msg)
        conn.sendall(reply.encode("utf-8"))

    except Exception as e:
        print(f"Client handler error: {e}")

    finally:
        conn.close()


def tcp_server_loop():
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind((HOST, PORT))
    server.listen(5)

    print(f"Server listening on {HOST}:{PORT} ...")

    while True:
        conn, addr = server.accept()
        threading.Thread(target=handle_client, args=(conn, addr), daemon=True).start()


# Dispense logic with stop support

def dispenseWeight(massRequested):
    global currently_dispensing

    clock = time.time
    previous_time = clock()

    scaleInitial = adc.value
    voltageInitial = scaleInitial * 3.3
    massInitial = get_mass(voltageInitial)

    print(f"Initial voltage and mass are: {voltageInitial:.3f} V -> {massInitial:.1f} g")

    pump_is_on = False
    stop_fill_event.clear()

    with state_lock:
        currently_dispensing = True

    try:
        while True:


            # 2) keep your original sampling interval
            if previous_time + 0.2 < clock():
                scale = adc.value
                voltage = scale * 3.3
                mass = get_mass(voltage)

                massDifference = massInitial - mass

                if massDifference < massRequested:
                    if not pump_is_on:
                        pump_on(WATER_PUMP_GPIO_COLD)
                        pump_is_on = True

                    print(f"Dispensing, {massRequested - massDifference:.0f} g left")
                else:
                    print("Requested amount reached.")
                    break

                previous_time = clock()

    finally:
        pump_off(WATER_PUMP_GPIO_COLD)
        with state_lock:
            currently_dispensing = False

def dispenseWeightHot():
    global currently_dispensing
    pump_is_on = False
    with state_lock:
        currently_dispensing = True
    try:
        while True:
            # 1) stop immediately if bottle Pi says FULL
            if stop_fill_event.is_set():
                print("[SAFE STOP] Stop event detected, stopping dispense now.")
                break
            if  not pump_is_on:
                pump_on(WATER_PUMP_GPIO_HOT)
                pump_is_on= True
    finally:
        pump_off(WATER_PUMP_GPIO_HOT)
        with state_lock:
            currently_dispensing = False            
            
# DB helpers

def get_latest_request():
    """
    Expected JSON example:
    [{
        "ID": 5,
        "VolumeOfColdTank": 250,
        "VolumeOfHotTank": 0,
        "RefillResponse": "pending"
    }]
    """
    try:
        url = f"{BASE_URL}/getRefillStatus"
        response = requests.get(url, timeout=5)

        if response.status_code != 200:
            print(f"DB poll failed: HTTP {response.status_code}")
            return None

        data = response.json()
        if len(data) == 0:
            return None

        return data[0]

    except Exception as e:
        print(f"DB poll error: {e}")
        return None


def mark_request_done(request_id):
    """
    Replace with your real endpoint if needed.
    """
    try:
        url = f"{BASE_URL}/setRefillStatus/done"
        response = requests.get(url, timeout=5)

        if response.status_code != 200:
            print(f"Failed to mark done: HTTP {response.status_code}")
        else:
            print(f"Request {request_id} marked as done.")

    except Exception as e:
        print(f"Mark done error: {e}")



# Main polling loop

def main_loop():
    print("Station ready. Polling for refill requests...")

    while True:
        request = get_latest_request()

        if request is not None and request["RefillResponse"] == "pending":
            req_id = request["ID"]
            ml_cold = float(request["VolumeOfColdTank"])
            ml_warm = float(request["VolumeOfHotTank"])

            print(f"New request #{req_id}: cold={ml_cold:.0f}g  warm={ml_warm:.0f}g")

            # clear any stale stop signal before new job
            stop_fill_event.clear()

            if ml_cold > 0:
                print("Starting cold tank dispense...")
                dispenseWeight(ml_cold)

            if ml_warm > 0:
                print("Starting warm tank dispense...")
                dispenseWeightHot()

            mark_request_done(req_id)
            print("Refill complete.")

        else:
            print("No pending request. Waiting...")

        time.sleep(POLL_INTERVAL)


# Main

if __name__ == "__main__":
    try:
        # Start TCP server in background
        server_thread = threading.Thread(target=tcp_server_loop, daemon=True)
        server_thread.start()

        # Run DB polling loop in main thread
        main_loop()

    except KeyboardInterrupt:
        print("Shutting down...")

    finally:
        pump_off()
        GPIO.cleanup()