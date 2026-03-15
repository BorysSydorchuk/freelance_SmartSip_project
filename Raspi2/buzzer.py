from gpiozero import Buzzer

buzzer = Buzzer(22)

def buzzer_onesecond():
    buzzer.beep(on_time=0.1, off_time=0.1, n=3)

counter = 0
while True:
    if(counter < 1):
        buzzer_onesecond()
        counter = counter + 1

    