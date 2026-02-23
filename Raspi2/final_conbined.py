import time
import glob
import RPi.GPIO as GPIO
from rpi_ws281x import PixelStrip, Color

# LED strip config
LED_COUNT = 14
LED_PIN = 18
LED_FREQ_HZ = 800000
LED_DMA = 10
LED_BRIGHTNESS = 100
LED_INVERT = False
LED_CHANNEL = 0

strip = PixelStrip(
    LED_COUNT, LED_PIN, LED_FREQ_HZ, LED_DMA,
    LED_INVERT, LED_BRIGHTNESS, LED_CHANNEL
)
strip.begin()

clock = time.time

#DS18B20 temp sensor config
base = '/sys/bus/w1/devices/'
devs = glob.glob(base + '28-*')
if not devs:
    raise RuntimeError("No DS18B20 sensor found under /sys/bus/w1/devices/ (check 1-Wire enabled & wiring).")

dev = devs[0]  # first DS18B20 found
w1_file = dev + '/w1_slave'

def read_c():
    with open(w1_file) as f:
        lines = f.read().strip().splitlines()
    if lines and lines[0].endswith('YES'):
        t_str = lines[1].split('t=')[-1]
        return float(t_str) / 1000.0
    return None

def get_led_color_by_temp(temp):
    if temp < 24:
        return Color(0, 0, 255)       # Blue
    elif 24 <= temp <= 26:
        return Color(255, 165, 0)     # Orange
    else:
        return Color(255, 0, 0)       # Red

def get_temp_description(temp):
    if temp < 24:
        return "Blue (< 24°C)"
    elif 24 <= temp <= 26:
        return "Orange (24–26°C)"
    else:
        return "Red (> 26°C)"

#Multiplexer / water-level reader config
# GPIO pin numbers (BCM)
S0 = 13
S1 = 6
S2 = 5
Z  = 26

GPIO.setmode(GPIO.BCM)
GPIO.setup(S0, GPIO.OUT, initial=GPIO.LOW)
GPIO.setup(S1, GPIO.OUT, initial=GPIO.LOW)
GPIO.setup(S2, GPIO.OUT, initial=GPIO.LOW)
GPIO.setup(Z, GPIO.IN, pull_up_down=GPIO.PUD_DOWN)

def select_channel(ch: int):
    bits = format(ch, '03b')          # always 3 bits, e.g. 0 -> "000"
    GPIO.output(S0, int(bits[2]))     # LSB
    GPIO.output(S1, int(bits[1]))
    GPIO.output(S2, int(bits[0]))     # MSB

def read_channel(ch: int) -> int:
    select_channel(ch)
    # settling time ~0.5ms
    t0 = clock()
    while clock() - t0 < 0.0005:
        pass
    return GPIO.input(Z)  # 0 or 1

def sensors_to_leds_count(sensor_count: int, sensor_max: int = 7, led_count: int = LED_COUNT) -> int:
    if sensor_max <= 0:
        return 0
    n = int(round((sensor_count / float(sensor_max)) * led_count))
    return max(0, min(led_count, n))

def set_level(level: int, color_on: Color, color_off: Color = Color(0, 0, 0)):
    level = max(0, min(LED_COUNT, level))
    for i in range(strip.numPixels()):
        strip.setPixelColor(i, color_on if i < level else color_off)
    strip.show()

#  Main loop
def main_loop():
    sensor_idx = 0
    water_level_list = [0] * 7

    # timing
    sensor_interval = 0.01   # read one sensor every 10ms
    led_update_interval = 0.1  # update LEDs at most every 100ms
    temp_interval = 1.0      # read temperature every 1s

    last_sensor_time = clock()
    last_led_time = clock()
    last_temp_time = clock()

    readings_done = 0
    last_temp = None
    current_color = Color(0, 255, 0)  # fallback color before first temp read

    try:
        while True:
            now = clock()

            # 1 read water sensors (1 channel each time slice)
            if now - last_sensor_time >= sensor_interval:
                water_level_list[sensor_idx] = read_channel(sensor_idx)
                sensor_idx = (sensor_idx + 1) % 7
                readings_done += 1
                last_sensor_time = now

            # 2 read temperature periodically
            if now - last_temp_time >= temp_interval:
                t = read_c()
                if t is not None:
                    last_temp = t
                    current_color = get_led_color_by_temp(t)
                    print(f'{t:.2f} °C - {get_temp_description(t)}')
                else:
                    print('Temperature read error')
                last_temp_time = now

            # 3 update LEDs after we have at least one full sensor scan (>=7 reads)
            if readings_done >= 7 and (now - last_led_time >= led_update_interval):
                water_level = sum(water_level_list)  # how many sensors are "wet" (0..7)
                leds_on = sensors_to_leds_count(water_level, sensor_max=7, led_count=LED_COUNT)

                # LEDs: show water level length, color depends on temperature
                set_level(leds_on, color_on=current_color, color_off=Color(0, 0, 0))

                # print(f'water_level={water_level} leds_on={leds_on} sensors={water_level_list} temp={last_temp}')
                readings_done = 0
                last_led_time = now

    except KeyboardInterrupt:
        pass
    finally:
        set_level(0, color_on=Color(0, 0, 0))
        GPIO.cleanup()

if __name__ == '__main__':
    main_loop()
