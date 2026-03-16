import RPi.GPIO as GPIO
import time

GPIO.setmode(GPIO.BCM)

S0 = 13
S1 = 6
S2 = 5
Z  = 26

water_level_list = []

# Select pins are outputs
GPIO.setup(S0, GPIO.OUT, initial=GPIO.LOW)
GPIO.setup(S1, GPIO.OUT, initial=GPIO.LOW)
GPIO.setup(S2, GPIO.OUT, initial=GPIO.LOW)

# Z is input (use pull-up or pull-down depending on your circuit)
# If the mux output can float, you NEED a pull resistor.
GPIO.setup(Z, GPIO.IN, pull_up_down=GPIO.PUD_DOWN)

def select_channel(ch):
    bits = format(ch, '03b')  # always 3 bits

    GPIO.output(S0, int(bits[2]))  # LSB
    GPIO.output(S1, int(bits[1]))
    GPIO.output(S2, int(bits[0]))  # MSB

def read_channel(ch):
    select_channel(ch)
    time.sleep(0.0005)  # small settling time (0.5 ms); can be lower
    return GPIO.input(Z)


while True:
    for i in range(7):
        water_level_list.append(read_channel(i))
        time.sleep(0.01)
    
    water_level = water_level_list.count(1)
    print (water_level)
    print (water_level_list)
    water_level_list = []
    time.sleep(0.1)

    
    
