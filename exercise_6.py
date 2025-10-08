from gpiozero import LED, Button
from time import sleep

led = LED(2)
button = Button(4)

pwm = 0

def drive_led (duty_cycle):
    period = 0.01
    time_on = duty_cycle*period
    time_off = (1-duty_cycle)*period
    led.on()
    sleep(time_on)
    led.off()
    sleep(time_off)

while True:
    if button.is_pressed:
        pwm = pwm + 0.1
        if pwm > 1:
            pwm = 0
        sleep(0.3)
    drive_led(pwm)
    print(pwm)