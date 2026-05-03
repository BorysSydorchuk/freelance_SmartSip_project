SmartSip: A Distributed IoT Hydration Ecosystem 💧
SmartSip is a dual-device IoT solution designed to track, manage, and visualize hydration data in real-time. Developed as part of the Engineering Experience at KU Leuven, this project bridges the gap between hardware sensing and cloud-ready software architecture.

🚀 Overview
The system consists of two primary nodes:

The Smart Bottle: A mobile unit equipped with sensors to measure water consumption.

The Docking Station: A central hub that manages data persistence, provides a visual dashboard, and synchronizes with the bottle.

🛠 Technical Stack
Language: Java (JDK 17+)

Networking: TCP/IP Socket Programming (JSON payloads)

Database: MySQL

UI/UX: JavaFX (MVC Pattern)

Hardware: Raspberry Pi, WS2812B Addressable LEDs, Load Cells (HX711), Multiplexers.

🧠 Key Features & Architecture
1. Real-Time Distributed Sync
The Bottle and Station communicate over a custom TCP/IP protocol. Data is serialized into JSON format, ensuring a lightweight and extensible messaging system. This allows the bottle to push hydration events to the station seamlessly.

2. Precision Hardware Integration
Implemented a 3kg Load Cell with a 24-bit ADC for accurate weight measurement.

Designed a custom low-pass filter and multiplexer logic to handle signal noise and pin limitations on the Raspberry Pi.

Utilized WS2812B LEDs for intuitive user feedback, controlled via a single-wire 800kbps protocol.

3. Full-Stack Data Management
The station runs a MySQL database to log historical data, allowing for long-term health tracking. The JavaFX dashboard uses multi-threading to ensure the UI remains responsive while the background TCP listener waits for incoming data packets.

4. AI-Assisted Development
This project served as an exploration of modern developer workflows. I leveraged AI to:

Refine the JavaFX CSS for a polished, professional UI/UX.

Optimize the integration of hardware drivers with the core database logic.

Accelerate the troubleshooting of complex networking socket issues.

📂 Project Structure
/src/station: Logic for the central hub, database management, and JavaFX controllers.

/src/bottle: Client-side logic for sensor reading and data transmission.

/resources: FXML layouts, CSS styling, and database schemas.

👷 Author
Borys Sydorchuk

Electronics and ICT Engineering Student at KU Leuven.

Passionate about systems engineering, high-tier software architecture, and the intersection of hardware and software.
