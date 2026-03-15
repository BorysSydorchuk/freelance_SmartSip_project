import time, glob

base = '/sys/bus/w1/devices/'
dev  = glob.glob(base + '28-*')[0]        # first DS18B20 found
file = dev + '/w1_slave'

def read_c():
    with open(file) as f:
        lines = f.read().strip().splitlines()
    # if CRC says YES at end of line 1, parse line 2
    if lines[0].endswith('YES'):
        t_str = lines[1].split('t=')[-1]
        return float(t_str)/1000.0
    else:
        return None

clock=time.time
previous_time=clock()

while True:
    if previous_time + 0.5 < clock():
        t = read_c()
        if t is not None:
            print(f'{t:.2f} °C')
        else:
            print('read error')
        previous_time=clock()
    
    