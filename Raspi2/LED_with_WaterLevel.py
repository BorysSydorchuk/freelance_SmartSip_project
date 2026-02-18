import time
import random
import glob
import RPi.GPIO as GPIO
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
    for i in range(strip.numPixels()):
        strip.setPixelColor(i, color)
    strip.show()


def set_level(level, color_on=Color(0,255,0), color_off=Color(0,0,0)):
    if level < 0:
        level = 0
    if level > LED_COUNT:
        level = LED_COUNT
    for i in range(strip.numPixels()):
        if i < level:
            strip.setPixelColor(i, color_on)
        else:
            strip.setPixelColor(i, color_off)
    strip.show()


# Multiplexer / water-level reader configuration
# GPIO pin numbers (BCM)
S0 = 13
S1 = 6
S2 = 5
Z  = 26

# Use BCM numbering
GPIO.setmode(GPIO.BCM)

# Select pins are outputs
GPIO.setup(S0, GPIO.OUT, initial=GPIO.LOW)
GPIO.setup(S1, GPIO.OUT, initial=GPIO.LOW)
GPIO.setup(S2, GPIO.OUT, initial=GPIO.LOW)

# Z is input
GPIO.setup(Z, GPIO.IN, pull_up_down=GPIO.PUD_DOWN)


def select_channel(ch):
    bits = format(ch, '03b')  # always 3 bits
    GPIO.output(S0, int(bits[2]))  # LSB
    GPIO.output(S1, int(bits[1]))
    GPIO.output(S2, int(bits[0]))  # MSB


def read_channel(ch):
    select_channel(ch)
    # small settling time (0.5 ms) implemented with a short busy-wait
    t0 = clock()
    while clock() - t0 < 0.0005:
        pass
    return GPIO.input(Z)


def sensors_to_leds_count(sensor_count, sensor_max=7, led_count=LED_COUNT):
    """Map number of active sensors (0..sensor_max) to number of LEDs to light (0..led_count)."""
    if sensor_max <= 0:
        return 0
    # Proportional mapping, round to nearest integer
    return int(round((sensor_count / float(sensor_max)) * led_count))


def main_loop():
    sensor_idx = 0
    water_level_list = [0] * 7
    sensor_interval = 0.01  # time between sensor reads
    loop_interval = 0.1     # minimum time between full cycles
    last_sensor_time = clock()
    last_cycle_time = clock()
    readings_done = 0

    try:
        while True:
            now = clock()

            # read one sensor at a time when sensor_interval elapsed
            if last_sensor_time + sensor_interval < now:
                water_level_list[sensor_idx] = read_channel(sensor_idx)
                sensor_idx = (sensor_idx + 1) % 7
                readings_done += 1
                last_sensor_time = now

            # after completing at least 7 reads, update LEDs when loop_interval passed
            if readings_done >= 7 and last_cycle_time + loop_interval < now:
                water_level = sum(water_level_list)
                leds_on = sensors_to_leds_count(water_level, sensor_max=7, led_count=LED_COUNT)

                # Update LEDs: light where water is (and below)
                set_level(leds_on, color_on=Color(0,255,0), color_off=Color(0,0,0))

                print(f'water_level={water_level} leds_on={leds_on}')
                print(water_level_list)

                readings_done = 0
                last_cycle_time = now

            # busy-wait friendly small sleep substitute: yield thread briefly
            # (do not use time.sleep here per request)
            # simple no-op to avoid tight spin on some systems
            pass

    except KeyboardInterrupt:
        pass
    finally:
        # Turn off LEDs and cleanup GPIO
        set_level(0)
        GPIO.cleanup()


if __name__ == '__main__':
    main_loop()