from gpiozero import LED, Button
from time import sleep

led = LED(2)
button = Button(4)
while True:
    if button.is_pressed:
        led.off()
    else:
        led.on()
    print('kaas')
    print('brood')
    print('spek') 
    print('сир')