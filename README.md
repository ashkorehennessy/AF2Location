<h1>
  <img src="./app/src/main/ic_launcher-playstore.png" alt="AF2Location Icon" height="64" style="border-radius: 16px; vertical-align: middle; margin-right: 8px;">
  AF2Location
</h1>

[**中文**](README_CN.md)

AF2Location is a lightweight bridge utility for Android that forwards flight telemetry data from **Aerofly FS 2** and **Aerofly FS 4** to the Android system's **Mock Location** provider.

This application allows the Android operating system to recognize the flight simulator's virtual coordinates as the device's physical location. Consequently, any location-based application running on the device (navigation software, mapping tools, etc.) will track the simulated aircraft's position in real-time.

## Core Functionality

The application performs four specific tasks:
1.  **UDP Listening**: Listens for `XGPS` telemetry strings broadcasted by Aerofly FS on a specified port.
2.  **Data Parsing**: Extracts latitude, longitude, altitude, heading, and ground speed.
3.  **Signal Processing**: Applies a 1D Kalman Filter to the coordinate data to mitigate the ~11m quantization noise inherent in the simulator's 4-decimal-place output.
4.  **System Injection**: Injects the processed data into the Android `LocationManager` as a test provider, including accurate raw heading data to support crabbing maneuvers.

## Requirements

* **PC**: Aerofly FS 2 or Aerofly FS 4 installed.
* **Android**: Device running Android 13 (Tiramisu) or higher.
* **Network**: PC and Android device must be on the same local network (LAN/WLAN).

## Configuration

### 1. PC Side (Aerofly FS)

Telemetry broadcasting must be enabled within the simulator settings.

1.  Open Aerofly FS.
2.  Navigate to **Settings** -> **Miscellaneous**.
3.  Locate the **Broadcast flight info to IP address** option and toggle it to **ON**.
4.  Verify or configure the following (usually default):
    * **Broadcast IP address**: `192.168.1.255` (Default broadcast address, or use your specific device IP).
    * **Broadcast IP port**: `49002` (Default).

### 2. Android Side

To allow this application to inject GPS data, developer options must be enabled:

1.  Go to **Settings** -> **About Phone**.
2.  Tap **Build Number** 7 times to enable Developer Options.
3.  Go to **System** -> **Developer Options**.
4.  Scroll down to **Select mock location app**.
5.  Select **AF2Location**.

## Usage

1.  Launch AF2Location.
2.  Enter the UDP port configured in Aerofly FS (Default: `49002`).
3.  Tap **Start Service**.
4.  Grant the requested location and notification permissions.
    * *Note: "Allow all the time" or "Allow only while using the app" is required.*
5.  The status will appear in the notification shade. You can now switch to any other application.

To stop the service, open the app and tap **Stop Service**, or use the action button in the notification.

## Build

This project is built using standard Android Studio and Gradle.

* Language: Kotlin
* Min SDK: 33 (Android 13 Tiramisu)
* Target SDK: 36 (Android 16 Baklava)

## License

MIT License

## Disclaimer

This software is intended for **flight simulation use only**. Do not use mock location features for navigation in real-world aviation, driving, or any safety-critical applications.