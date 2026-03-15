from gpiozero import MCP3008
from gpiozero import Buzzer
from gpiozero import Button
import RPi.GPIO as GPIO
import time

#initialisering ad converter
WATER_PUMP_GPIO_Right = 15
Water_PUMP_GPIO_Left = 18
adcRight = MCP3008(channel=0)  # left when looking straight at the faucet
adcLeft = MCP3008(channel=1)
button = Button(21)
#buzzer

#initialising water pump
GPIO.setmode(GPIO.BCM)
GPIO.setup(WATER_PUMP_GPIO_Right, GPIO.OUT)
GPIO.setup(Water_PUMP_GPIO_Left, GPIO.OUT)
GPIO.output(WATER_PUMP_GPIO_Right, 0)
GPIO.output(Water_PUMP_GPIO_Left, 0)

#LINEARISATION pump 1
ZERO_OFFSET_Left = 0.750
FULL_SCALE_VOLT_Left = 1.550
KNOWN_MASS_Left = 800

#LINEARISATION pump 2
ZERO_OFFSET_Right = 1.024
FULL_SCALE_VOLT_Right = 1.385
KNOWN_MASS_Right = 500

def get_massLeft(v):
    return (v - ZERO_OFFSET_Left) * (KNOWN_MASS_Left / (FULL_SCALE_VOLT_Left - ZERO_OFFSET_Left))

def get_massRight(v):
    return (v - ZERO_OFFSET_Right) * (KNOWN_MASS_Right / (FULL_SCALE_VOLT_Right - ZERO_OFFSET_Right))

def pump_on_right():
    GPIO.output(WATER_PUMP_GPIO_Right, 1)
    print("Right Pump ON")

def pump_off_right():
    GPIO.output(WATER_PUMP_GPIO_Right, 0)
    print("Right Pump OFF")

def pump_on_Left():
    GPIO.output(Water_PUMP_GPIO_Left, 1)
    print("Left Pump ON")

def pump_off_Left():
    GPIO.output(Water_PUMP_GPIO_Left, 0)
    print("Left Pump OFF")

def dispenseWeight(massRequested, hotFraction):
    print(f"press button to dispense {massRequested}")
    button.wait_for_press()  # script is blocked till the button is pressed

    clock = time.time
    previous_time = clock()

    scaleInitialRight = adcRight.value
    voltageInitialRight = scaleInitialRight * 3.3
    massInitialRight = get_massRight(voltageInitialRight)

    scaleInitialLeft = adcLeft.value
    voltageInitialLeft = scaleInitialLeft * 3.3
    massInitialLeft = get_massLeft(voltageInitialLeft)

    print(f"Initial voltage and mass of cold tank are: {voltageInitialRight:.3f} V → {massInitialRight:.1f} g and Initial voltage and mass of hot tank are: {voltageInitialLeft:.3f} V → {massInitialLeft:.1f} g")

    requiredRight = (1 - hotFraction) * massRequested
    requiredLeft = hotFraction * massRequested

    # refill check
    if massInitialRight < requiredRight:
        print("refill cold tank")
        return

    if massInitialLeft < requiredLeft:
        print("refill hot tank")
        return

    pump_left_is_on = False
    pump_right_is_on = False

    try:
        while True:
            if previous_time + 0.2 < clock():
                scaleRight = adcRight.value
                voltageRight = scaleRight * 3.3
                massRight = get_massRight(voltageRight)
                massDifferenceRight = massInitialRight - massRight

                scaleLeft = adcLeft.value
                voltageLeft = scaleLeft * 3.3
                massLeft = get_massLeft(voltageLeft)
                massDifferenceLeft = massInitialLeft - massLeft

                if massDifferenceRight < requiredRight:
                    if not pump_right_is_on:
                        pump_on_right()
                        pump_right_is_on = True
                else:
                    if pump_right_is_on:
                        pump_off_right()
                        pump_right_is_on = False

                if massDifferenceLeft < requiredLeft:
                    if not pump_left_is_on:
                        pump_on_Left()
                        pump_left_is_on = True
                else:
                    if pump_left_is_on:
                        pump_off_Left()
                        pump_left_is_on = False

                total_dispensed = massDifferenceRight + massDifferenceLeft
                total_left = massRequested - total_dispensed
                if total_left < 0:
                    total_left = 0

                print(f"Dispensing, {total_left:.0f} g left")

                if massDifferenceRight >= requiredRight and massDifferenceLeft >= requiredLeft:
                    break

                previous_time = clock()

    finally:
        pump_off_right()
        pump_off_Left()

dispenseWeight(100, 0.6)