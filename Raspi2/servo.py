from gpiozero import Servo, Button
from signal import pause

#servo settings, range from -1 to 1
L_OPEN = 0
L_LOCK = 0.6

#mirrored
R_OPEN =  0
R_LOCK = -0.6


left_servo  = Servo(12)
right_servo = Servo(13)


# locked = False

def set_servos(l_value: float, r_value: float):
    left_servo.value  = l_value
    right_servo.value = r_value

def open_clamp():
    # global locked
    set_servos(L_OPEN, R_OPEN)
    # locked = False
    left_servo.detach()
    right_servo.detach()
    print("CLAMP: OPEN")

def lock_clamp():
    # global locked
    set_servos(L_LOCK, R_LOCK)
    # locked = True
    left_servo.detach()
    right_servo.detach()
    print("CLAMP: LOCKED")

# def toggle_clamp():
#     #works when button is pressed
#     if locked:
#         open_clamp()
#     else:
#         lock_clamp()

