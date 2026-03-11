import time

SENSOR_MAP = {
    "tank1": "/sys/bus/w1/devices/28-00000f030c5d", #When DS18B20 sensors are connected to the Raspberry Pi, each one appears in Linux as a folder with a unique ID
    "tank2": "/sys/bus/w1/devices/28-00001070288a",
}


def read_temp_c(device_path: str) -> float | None:
    w1_slave_file = device_path + "/w1_slave"

    try:
        with open(w1_slave_file, "r") as f:
            lines = f.read().strip().splitlines()
    except OSError:
        return None

    if len(lines) < 2 or not lines[0].endswith("YES"):
        return None

    try:
        t_str = lines[1].split("t=")[-1]
        return float(t_str) / 1000.0
    except (ValueError, IndexError):
        return None


def read_all_tanks(sensor_map: dict[str, str]) -> dict[str, float | None]:
    readings = {}   
    for tank, path in sensor_map.items():
        readings[tank] = read_temp_c(path)
    print_temp_tank(readings)
    return readings
            
        




def print_temp_tank(readings):
    for tank, temp in readings.items():
            if temp is None:
                print(f"{tank}: read error")
            else:
                print(f"{tank}: {temp:.2f} °C")
    print("-" * 30)

# while True:
#     now = clock()

#     if now - previous_time >= interval:
#         previous_time = now

#         readings = read_all_tanks(SENSOR_MAP)

#         for tank, temp in readings.items():
#             if temp is None:
#                 print(f"{tank}: read error")
#             else:
#                 print(f"{tank}: {temp:.2f} °C")

#         print("-" * 30)
read_all_tanks(SENSOR_MAP)