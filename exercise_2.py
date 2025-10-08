from gpiozero import LED, Button
from time import sleep

led = LED(2)
toggle = False
button = Button(4)

while True:
    if button.is_pressed:
        update = toggle
        if update == False:
            led.on()
            toggle = True
            sleep(0.3) #cooldown time for button
        else:
            led.off()
            toggle = False
            sleep(0.3)
    sleep(0.1)
    