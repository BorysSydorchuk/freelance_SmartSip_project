import time
import random
import glob
from rpi_ws281x import PixelStrip, Color

#const variables for the LED strip
LED_COUNT = 14        
LED_PIN = 18          
LED_FREQ_HZ = 800000  # LED signal frequency in hertz (usually 800khz)
LED_DMA = 10          # DMA channel to use for generating signal (try 10)
LED_BRIGHTNESS = 100  # Set to 0 for darkest and 255 for brightest
LED_INVERT = False    # True to invert the signal (when using NPN transistor level shift)
LED_CHANNEL = 0       # set to '1' for GPIOs 13, 19, 41, 45 or 53

clock=time.time
previous_time=clock()

# Create NeoPixel object with appropriate configuration.
strip = PixelStrip(LED_COUNT, LED_PIN, LED_FREQ_HZ, LED_DMA, LED_INVERT, LED_BRIGHTNESS, LED_CHANNEL)

# Intialize the library (must be called once before other functions).
strip.begin()

#chaging colors with the different temperature

def change_color(color):
    """Toggle all LED light colors"""
    for i in range(strip.numPixels()):
        strip.setPixelColor(i, color)
    strip.show()

def get_led_color_by_temp(temp):
    if temp < 24:
        # Blue color for temperature below 24°C
        return Color(0, 0, 255)
    elif 24 <= temp <= 26:
        # Orange color for temperature 24-26°C
        return Color(255, 165, 0)
    else:
        # Red color for temperature above 26°C
        return Color(255, 0, 0)

def get_temp_description(temp):
    if temp < 24:
        return "Blue (< 24°C)"
    elif 24 <= temp <= 26:
        return "Orange (24-26°C)"
    else:
        return "Red (> 26°C)"


base = '/sys/bus/w1/devices/'
dev  = glob.glob(base + '28-*')[0]        # first DS18B20 found
file = dev + '/w1_slave'

def read_c():
    with open(file) as f:
        lines = f.read().strip().splitlines()
    # if CRC says YES at end of line 1, parse line 2
    if lines[0].endswith('YES'):
        t_str = lines[1].split('t=')[-1]
        return float(t_str)/1000.0
    else:
        return None


while True:
    if previous_time + 0.1 < clock():
        t = read_c()
        if t is not None:
            print(f'{t:.2f} °C - {get_temp_description(t)}')
            # Change LED color based on temperature
            color = get_led_color_by_temp(t)
            change_color(color)
        else:
            print('read error')
        previous_time=clock()
    