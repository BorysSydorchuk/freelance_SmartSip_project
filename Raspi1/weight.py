from gpiozero import MCP3008
import time

# Create ADC object on channel 0 (default SPI device 0)
adcRight = MCP3008(channel=0) #left when looking straight at the faucet
adcLeft = MCP3008(channel=1)
#LINEARISATION pump 1
ZERO_OFFSET_Left = 0.750    # Voltage at 0 kg
FULL_SCALE_VOLT_Left =1.550  # Voltage at known mass (maybe use phone?)
KNOWN_MASS_Left = 800      # phone mass
#LINEARISATION pump 2
ZERO_OFFSET_Right = 1.024     # Voltage at 0 kg
FULL_SCALE_VOLT_Right = 1.385 # Voltage at known mass (maybe use phone?)
KNOWN_MASS_Right= 500      # phone mass

def get_massLeft(v):
    return (v - ZERO_OFFSET_Left) * (KNOWN_MASS_Left / (FULL_SCALE_VOLT_Left - ZERO_OFFSET_Left)) #right side creates slope (y2-y1)/(x2-x1) this is a function m(v) that has an output of mass and input off voltage
def get_massRight(v):
    return (v - ZERO_OFFSET_Right) * (KNOWN_MASS_Right / (FULL_SCALE_VOLT_Right - ZERO_OFFSET_Right)) #right side creates slope (y2-y1)/(x2-x1) this is a function m(v) that has an output of mass and input off voltage
clock=time.time #giving the function time.time an alias "clock"
previous_time=clock()
while True:
    if previous_time+1 < clock():
        scaleRight = adcRight.value           # 0.0–1.0 (ratio) its a scale so you can reverse engineer the voltage you have before the adc converter and then you apply linearisation to find the mass 
        voltageRight = scaleRight * 3.3       # Convert to volts
        massRight = get_massRight(voltageRight)
        scaleLeft = adcLeft.value           # 0.0–1.0 (ratio) its a scale so you can reverse engineer the voltage you have before the adc converter and then you apply linearisation to find the mass 
        voltageLeft = scaleLeft * 3.3       # Convert to volts
        massLeft = get_massLeft(voltageLeft)
        print(f"The right tank has:{voltageRight:.3f} V → {massRight:.0f} g The left tank has:{voltageLeft:.3f}V → {massLeft:.0f} g") #the 3f formats the voltage to 3 decimals and 1f formats mass to 1 decimal
        previous_time=clock()





















































        