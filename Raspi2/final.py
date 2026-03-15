import time
import glob
import socket
import RPi.GPIO as GPIO
from rpi_ws281x import PixelStrip, Color

# Network config (Pi 2 server)

SERVER_IP = "172.20.10.3"   
SERVER_PORT = 5000

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

# temp sensor config
base = '/sys/bus/w1/devices/'
devs = glob.glob(base + '28-*')
if not devs:
    raise RuntimeError("No DS18B20 sensor found.")

dev = devs[0]
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


#water-level reader config

S0 = 13
S1 = 6
S2 = 5
Z = 26

GPIO.setmode(GPIO.BCM)
GPIO.setup(S0, GPIO.OUT, initial=GPIO.LOW)
GPIO.setup(S1, GPIO.OUT, initial=GPIO.LOW)
GPIO.setup(S2, GPIO.OUT, initial=GPIO.LOW)
GPIO.setup(Z, GPIO.IN, pull_up_down=GPIO.PUD_DOWN)


def select_channel(ch: int):
    bits = format(ch, '03b')
    GPIO.output(S0, int(bits[2]))   # LSB
    GPIO.output(S1, int(bits[1]))
    GPIO.output(S2, int(bits[0]))   # MSB


def read_channel(ch: int) -> int:
    select_channel(ch)
    t0 = clock()
    while clock() - t0 < 0.0005:
        pass
    return GPIO.input(Z)


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



# TCP client helpers

def send_message_to_server(message: str, timeout: float = 2.0) -> bool:
  
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.settimeout(timeout)
            s.connect((SERVER_IP, SERVER_PORT))
            s.sendall(message.encode("utf-8"))

            # Optional ACK receive
            try:
                reply = s.recv(1024).decode("utf-8").strip()
                print(f"Server reply: {reply}")
            except socket.timeout:
                print("timeout, but message may still be delivered.")

        print(f"Sent: {message}")
        return True

    except Exception as e:
        print(f"Failed to send '{message}' to server: {e}")
        return False


# Main loop

def main_loop():
    sensor_idx = 0
    water_level_list = [0] * 7

    # timing
    sensor_interval = 0.01        # read one sensor every 10 ms
    led_update_interval = 0.1     # update LEDs every 100 ms
    temp_interval = 1.0           # read temp every 1 s
    heartbeat_interval = 5.0      # send heartbeat every 5 s

    last_sensor_time = clock()
    last_led_time = clock()
    last_temp_time = clock()
    last_heartbeat_time = clock()

    readings_done = 0
    last_temp = None
    current_color = Color(0, 255, 0)  # fallback before first temp read

 
    FULL_THRESHOLD = 7

    # Track state changes to avoid spamming messages
    bottle_is_full = False

    try:
        while True:
            now = clock()

            # 1 Read water sensors (one channel per time slice)
            if now - last_sensor_time >= sensor_interval:
                water_level_list[sensor_idx] = read_channel(sensor_idx)
                sensor_idx = (sensor_idx + 1) % 7
                readings_done += 1
                last_sensor_time = now

            # 2 Read temperature periodically
            if now - last_temp_time >= temp_interval:
                t = read_c()
                if t is not None:
                    last_temp = t
                    current_color = get_led_color_by_temp(t)
                    print(f"{t:.2f} °C - {get_temp_description(t)}")
                else:
                    print("Temperature read error")
                last_temp_time = now

            # 3 Update LEDs after one full sensor scan
            if readings_done >= 7 and (now - last_led_time >= led_update_interval):
                water_level = sum(water_level_list)   # 0..7
                leds_on = sensors_to_leds_count(water_level, sensor_max=7, led_count=LED_COUNT)

                set_level(leds_on, color_on=current_color, color_off=Color(0, 0, 0))

                print(f"water_level={water_level}, sensors={water_level_list}, temp={last_temp}")

                # 4) Full bottle event detection
                currently_full = water_level >= FULL_THRESHOLD

                if currently_full and not bottle_is_full:
                    print("Bottle became FULL -> notify Pi 1 to stop filling")
                    send_message_to_server("FULL")
                    bottle_is_full = True

                elif (not currently_full) and bottle_is_full:
                    print("Bottle is no longer full")
                    send_message_to_server("NOT_FULL")
                    bottle_is_full = False

                readings_done = 0
                last_led_time = now

            # 5) Heartbeat
            if now - last_heartbeat_time >= heartbeat_interval:
                hb_message = f"HEARTBEAT temp={last_temp if last_temp is not None else 'NA'} full={int(bottle_is_full)}"
                send_message_to_server(hb_message)
                last_heartbeat_time = now

            time.sleep(0.001)

    except KeyboardInterrupt:
        print("Stopped by user.")

    finally:
        set_level(0, color_on=Color(0, 0, 0))
        GPIO.cleanup()


if __name__ == '__main__':
    main_loop()