from gpiozero import LED, Button
from time import sleep

led1 = LED(5)
led2 = LED(6)
led3 = LED(13)
led4 = LED(19)
button = Button(4)

count = 0

def dec_to_bin(dec):
    bit1 = False
    bit2 = False
    bit3 = False
    bit4 = False
    if dec >= 8:
        bit1 = True
        dec = dec-8
    if dec >=4:
        bit2 = True
        dec = dec-4
    if dec >=2:
        bit3 = True
        dec = dec-2
    if dec >=1:
        bit4 = True
        dec = dec-1
    bin = [bit1,bit2,bit3,bit4]
    return (bin)

def bin_to_led (bin):
    if bin[0]:
        led1.on()
    else:
        led1.off()
    if bin[1]:
        led2.on()
    else:
        led2.off()
    if bin[2]:
        led3.on()
    else:
        led3.off()
    if bin[3]:
        led4.on()
    else:
        led4.off()


while True:
    if button.is_pressed:
        count = count +1
        sleep(0.3)
    if count > 15:
        count = 0
    print(dec_to_bin(count))
    bin_to_led(dec_to_bin(count))
    sleep(0.1)