from gpiozero import LED, Button
from time import sleep

led1 = LED(5)

while True:
    led1.on()
    sleep(1)
    led1.off()
    sleep(1)
