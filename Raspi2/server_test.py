import socket

HOST = "0.0.0.0"
PORT = 5000

server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
server.bind((HOST, PORT))
server.listen(5)

print(f"Server listening on {HOST}:{PORT} ...")

while True:
    conn, addr = server.accept()
    print("Connected by:", addr)

    data = conn.recv(1024)
    if not data:
        conn.close()
        continue

    msg = data.decode().strip()
    print("Received from client:", msg)

    if msg == "FULL":
        print("Bottle full -> stop filling")
        conn.sendall(b"ACK_FULL")
    elif msg == "HEARTBEAT":
        print("Heartbeat received")
        conn.sendall(b"ACK_HEARTBEAT")
    else:
        print("Unknown message")
        conn.sendall(b"ACK_UNKNOWN")

    conn.close()