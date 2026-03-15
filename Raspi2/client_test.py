import socket

HOST = "172.20.10.3" 
PORT = 5000

client = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
client.connect((HOST, PORT))

client.sendall(b"FULL")

reply = client.recv(1024)
print("Received from server:", reply.decode())

client.close()