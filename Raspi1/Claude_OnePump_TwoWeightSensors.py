# btw voor met die calorimetrie kunnnen we gwn werken met vaste volumes vr koud en warm en als er niet genoeg zit in een tank gwn zeggen "refill this tank"
from gpiozero import MCP3008
from gpiozero import Buzzer
from gpiozero import Button
import RPi.GPIO as GPIO
import time
import requests                         # NEW: for DB communication

# initialisering ad converter
WATER_PUMP_GPIO  = 18
adc = MCP3008(channel=0)
button = Button(21)

# initialising water pump
GPIO.setmode(GPIO.BCM)
GPIO.setup(WATER_PUMP_GPIO, GPIO.OUT)

# linearisering
ZERO_OFFSET     = 1.29    # Voltage at 0 kg
FULL_SCALE_VOLT = 1.585   # Voltage at known mass (maybe use phone?)
KNOWN_MASS      = 500     # phone mass

def get_mass(v):
    return (v - ZERO_OFFSET) * (KNOWN_MASS / (FULL_SCALE_VOLT - ZERO_OFFSET))

def pump_on():
    GPIO.output(WATER_PUMP_GPIO, 1)
    print("Pump ON")

def pump_off():
    GPIO.output(WATER_PUMP_GPIO, 0)
    print("Pump OFF")

def dispenseWeight(massRequested):
    # print(f"press button to dispense+{massRequested}")
    # button.wait_for_press()             # script is blocked till the button is pressed
    clock = time.time                   # giving the function time.time an alias "clock"
    previous_time = clock()
    scaleInitial = adc.value            # 0.0–1.0 (ratio) its a scale so you can reverse engineer the voltage you have before the adc converter and then you apply linearisation to find the mass
    voltageInitial = scaleInitial * 3.3 # Convert to volts
    massInitial = get_mass(voltageInitial)
    print(f"Initial voltage and mass are:{voltageInitial:.3f} V → {massInitial:.1f} g")
    pump_is_on = False                  # this is to set the beginning to false so you dont need to turn on pump every 0.2s so it stays running
    try:
        while True:
            if previous_time + 0.2 < clock():
                scale = adc.value       # 0.0–1.0 (ratio) its a scale so you can reverse engineer the voltage you have before the adc converter and then you apply linearisation to find the mass
                voltage = scale * 3.3   # Convert to volts
                mass = get_mass(voltage)
                massDifference = massInitial - mass
                if massDifference < massRequested:
                    if not pump_is_on:
                        pump_on()
                        pump_is_on = True
                    print(f"Dispensing, {massRequested - massDifference:.0f} g left")  # the 3f formats the voltage to 3 decimals and 1f formats mass to 1 decimal
                else:
                    # pump off the break lets it go to finally so pump stops
                    break               # break stops the nearest loop youre in so here while true, otherewise the loop runs indefinitely and not only when we request
                previous_time = clock()
    finally:
        pump_off()                      # the finally always gets run so if the code crashes or there is a keyboard interrupt. manually always turning the pump off is a good failsafe


# ════════════════════════════════════════════════════════════════════════
# NEW: DB communication config
# ════════════════════════════════════════════════════════════════════════
BASE_URL      = "https://studev.groept.be/api/a25EE2team203"
POLL_INTERVAL = 2.0     # check DB every 2 seconds

def get_latest_request():
    """
    NEW: Fetches the latest row from the refill request table.
    Replace 'getRefillStatus' with your actual endpoint name.
    Expected JSON: [{"id": 5, "ml_cold": 250, "ml_warm": 0, "RefillResponse": "pending"}]
    Column names must match exactly what your DB returns.
    """
    try:
        url = f"{BASE_URL}/getRefillStatus"     # replace endpoint name if different
        response = requests.get(url, timeout=5)
        if response.status_code != 200:
            print(f"DB poll failed: HTTP {response.status_code}")
            return None
        data = response.json()
        if len(data) == 0:
            return None
        return data[0]                          # latest row
    except Exception as e:
        print(f"DB poll error: {e}")
        return None

def mark_request_done(request_id):
    """
    NEW: Marks the refill request row as 'done' so the Java app stops waiting.
    Replace 'updateRefillDone' with your actual endpoint name.
    URL format: /updateRefillDone/{id}
    """
    try:
        url = f"{BASE_URL}/setRefillStatus/done"  # replace endpoint name if different
        response = requests.get(url, timeout=5)
        if response.status_code != 200:
            print(f"Failed to mark done: HTTP {response.status_code}")
        else:
            print(f"Request {request_id} marked as done.")
    except Exception as e:
        print(f"Mark done error: {e}")
# ════════════════════════════════════════════════════════════════════════


# ════════════════════════════════════════════════════════════════════════
# NEW: main polling loop — replaces the bare dispenseWeight(1000) call
# at the bottom. Checks DB every POLL_INTERVAL seconds. When it finds
# a "pending" row it calls dispenseWeight with the right amount, then
# marks the request as done so the Java app stops waiting.
# ════════════════════════════════════════════════════════════════════════
def main_loop():
    print("Station ready. Polling for refill requests...")
    while True:
        request = get_latest_request()

        if request is not None and request["RefillResponse"] == "pending":  # replace key name if different
            req_id  = request["ID"]              # replace key name if different
            ml_cold = float(request["VolumeOfColdTank"])  # replace key name if different
            ml_warm = float(request["VolumeOfHotTank"])  # replace key name if different

            print(f"New request #{req_id}: cold={ml_cold:.0f}g  warm={ml_warm:.0f}g")

            # Dispense cold water if requested
            if ml_cold > 0:
                print("Starting cold tank dispense...")
                dispenseWeight(ml_cold)

            # Dispense warm water if requested — uses same pump for now,
            # add second pump here when hardware is ready (see previous version)
            if ml_warm > 0:
                print("Starting warm tank dispense...")
                dispenseWeight(ml_warm)

            # Tell the Java app we are done
            mark_request_done(req_id)
            print("Refill complete.")

        else:
            print("No pending request. Waiting...")

        time.sleep(POLL_INTERVAL)

if __name__ == "__main__":
    main_loop()
# ════════════════════════════════════════════════════════════════════════
