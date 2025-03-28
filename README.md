# iBeacon ID Verification App

A simple Android application template that demonstrates how to use an Android device as an iBeacon transmitter. This app allows you to broadcast custom iBeacon packets containing company ID, personal ID, and asset ID information.

![App Screenshot](app_screenshot.jpg)

## What is an iBeacon?

iBeacon is Apple's implementation of Bluetooth Low Energy (BLE) proximity sensing. These small, low-power transmitters can notify nearby devices of their presence and allow apps to perform actions when in close proximity to a beacon.

An iBeacon packet contains:
- A UUID (16 bytes): Typically identifying a company or organization
- A Major value (2 bytes): Often used to identify a group (like a department)
- A Minor value (2 bytes): Often used to identify an individual item
- A measured power value: Used to determine approximate distance

## Features

- **Company ID**: Fixed UUID representing the organization
- **Personal ID**: Fixed Major value identifying the individual
- **Asset ID**: Variable Minor value obtained by scanning an asset QR code
- **iBeacon Broadcasting**: Transmits the above information as an iBeacon advertisement
- **Cross-Version Compatibility**: Works on Android 11 and newer devices
- **Permission Handling**: Properly handles the different Bluetooth permission models across Android versions

## Technical Details

### iBeacon Format

The app follows Apple's iBeacon specification, with an advertisement packet structured as:

```
Prefix: 0x02, 0x15 (iBeacon identifier)
UUID: 16 bytes representing the company/organization
Major: 2 bytes representing personal ID (0xA5B9 in this example)
Minor: 2 bytes representing the asset ID (obtained from QR code)
TX Power: 1 byte of calibrated TX power (-59 in this example)
```

The advertisement is sent using Apple's company identifier (0x004C) in the manufacturer-specific data.

### Bluetooth Permissions

The app handles the different Bluetooth permission models:

- **Android 12+**: Uses the new granular Bluetooth permissions:
  - `BLUETOOTH_SCAN`
  - `BLUETOOTH_CONNECT`
  - `BLUETOOTH_ADVERTISE`

- **Android 11 and older**: Uses the old Bluetooth permissions:
  - `BLUETOOTH`
  - `BLUETOOTH_ADMIN`

### QR Code Scanning

The app uses the ZXing library to scan QR codes. The QR code should contain a valid hexadecimal value that can fit in 2 bytes (0-65535).

## How to Use

1. Launch the app
2. The app will automatically check for Bluetooth and permission requirements
3. Tap "Scan Asset QR Code" and scan a QR code containing a hexadecimal value
4. Tap "Authenticate Me" to start broadcasting the iBeacon advertisement
5. The app will broadcast for 10 seconds and then stop (to conserve battery)

## Requirements

- Android 11 or newer
- Bluetooth Low Energy (BLE) support with advertising capability
- Camera (for QR code scanning)

## Building from Source

1. Clone this repository
2. Open the project in Android Studio
3. Build and run on a compatible device

## Download

Pre-built APK: [app/build/outputs/apk/debug/ibeacon-verification.apk](app/build/outputs/apk/debug/ibeacon-verification.apk)

## Limitations

- Not all Android devices support BLE advertising, even if they support BLE
- Some manufacturers may impose their own limitations on BLE advertising capabilities
- The app does not handle background operation (stops broadcasting when minimized)

## Technical Implementation

The app demonstrates several Android development best practices:

- **View Binding**: Type-safe way to interact with views
- **Permission Management**: Using the Activity Result API for handling permissions
- **ByteBuffer Operations**: Efficient handling of binary data for UUID conversion
- **BLE Advertising**: Setting up and managing Bluetooth LE advertisements
- **Lifecycle Management**: Properly stopping advertising when the app is paused

### Testing iBeacon Reception

To verify that the iBeacon is being broadcast correctly, you can use:

1. **iOS Devices**: Use apps like "Locate Beacon" or "Beacon Scanner"
2. **Android Devices**: Use apps like "nRF Connect"
3. **BLE Sniffers**: Hardware like Ubertooth or software sniffers can capture and analyze the raw BLE advertisements

## License

This project is released under the MIT License - see the LICENSE file for details.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Credits

- The ZXing library for QR code scanning
- Apple for the iBeacon specification

## Disclaimer

This app is provided as a demonstration and learning tool. The iBeacon technology trademark belongs to Apple Inc. 