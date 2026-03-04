#btw voor met die calorimetrie kunnnen we gwn werken met vaste volumes vr koud en warm en als er niet genoeg zit in een tank gwn zeggen "refill this tank"
from gpiozero import MCP3008
from gpiozero import Buzzer
from gpiozero import Button
import RPi.GPIO as GPIO
import time
#initialisering ad converter
WATER_PUMP_GPIO  = 18
adc = MCP3008(channel=0)
button=Button(21)
#initialising water pump
GPIO.setup(WATER_PUMP_GPIO, GPIO.OUT)

#linearisering
ZERO_OFFSET = 0.45     # Voltage at 0 kg
FULL_SCALE_VOLT = 2.40 # Voltage at known mass (maybe use phone?)
KNOWN_MASS = 1000      # phone mass
def get_mass(v):
    return (v - ZERO_OFFSET) * (KNOWN_MASS / (FULL_SCALE_VOLT - ZERO_OFFSET))


def pump_on():
    GPIO.output(WATER_PUMP_GPIO, 1)
    print("Pump ON")

def pump_off():
    GPIO.output(WATER_PUMP_GPIO, 0)
    print("Pump OFF")

def dispenseWeight(massRequested):
    print("press button to dispense"+massRequested)
    button.wait_for_press() #script is blocked till the button is pressed
    clock=time.time #giving the function time.time an alias "clock"
    previous_time=clock()
    scaleInitial = adc.value           # 0.0–1.0 (ratio) its a scale so you can reverse engineer the voltage you have before the adc converter and then you apply linearisation to find the mass 
    voltageInitial = scaleInitial * 3.3       # Convert to volts
    massInitial = get_mass(voltageInitial)
    print(f"Initial voltage and mass are:{voltageInitial:.3f} V → {massInitial:.1f} g")
    pump_is_on=False #this is to set the beginning to false so you dont need to turn on pump every 0.2s so it stays running
    try:
        while True:
            if previous_time+0.2 < clock():
                scale = adc.value           # 0.0–1.0 (ratio) its a scale so you can reverse engineer the voltage you have before the adc converter and then you apply linearisation to find the mass 
                voltage = scale * 3.3       # Convert to volts
                mass = get_mass(voltage)
                massDifference= massInitial-mass
                if(massDifference<massRequested):
                    if not pump_is_on:
                        pump_on()
                        pump_is_on= True
                    print(f"Dispensing, {massRequested-massDifference:.0f} g left") #the 3f formats the voltage to 3 decimals and 1f formats mass to 1 decimal
                else:
                    #pump off the break lets it go to finally so pump stops
                     break           #break stops the nearest loop youre in so here while true, otherewise the loop runs indefinitely and not only when we request
                previous_time=clock()
    finally:
        pump_off() #the finally always gets run so if the code crashes or there is a keyboard interrupt. manually always turning the pump off is a good failsafe




