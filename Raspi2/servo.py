from gpiozero import Servo, Button
from signal import pause
from threading import Timer
from gpiozero.pins.pigpio import PiGPIOFactory
#servo settings, range from -1 to 1
L_OPEN = 0.15
L_LOCK = 1

#mirrored
R_OPEN =  -0.15
R_LOCK = -1

#initialising pin factory to reduce servo jitter
factory = PiGPIOFactory()
left_servo  = Servo(12,pin_factory=factory)
right_servo = Servo(13,pin_factory=factory)


locked = False

def set_servos(l_value: float, r_value: float):
    left_servo.value  = l_value
    right_servo.value = r_value

def open_clamp():
    global locked
    set_servos(L_OPEN, R_OPEN)
    locked = False
    Timer(0.4,detach_servos).start()
    print("CLAMP: OPEN")

def detach_servos():
   left_servo.detach()
   right_servo.detach()

def lock_clamp():
    global locked
    set_servos(L_LOCK, R_LOCK)
    locked = True
    Timer(0.4,detach_servos).start()
    print("CLAMP: LOCKED")

def toggle_clamp():
    #works when button is pressed
    if locked:
        open_clamp()
    else:
        lock_clamp()



