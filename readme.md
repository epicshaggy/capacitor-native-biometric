# Capacitor Native Biometric

## Installation

- `npm i capacitor-native-biometric`

## Usage

```ts
import { Plugins } from "@capacitor/core";

const { NativeBiometric } = Plugins;

NativeBiometric.isAvailable().then(
  (result) => {
    const has = result.has;
    const touchId = result.touchId;
    const faceId = result.faceId;
  },
  (error) => {
    //Couldn't check availability
  }
);

NativeBiometric.verify().then(
  () => {
    //Authentication successful
  },
  (error) => {
    //Failed to authenticate
  }
);
```

## Methods

| Method        | Default | Type                        | Description                                     |
| ------------- | ------- | --------------------------- | ----------------------------------------------- |
| isAvailable() |         | `Promise<AvailableOptions>` | Gets available biometrics has / touchI / faceId |
| verify()      |         | `Promise<any>`              | Shows the prompt                                |

## Face ID (iOS)

To use FaceID Make sure to provide a value for NSFaceIDUsageDescription, otherwise your app may crash on iOS devices with FaceID.

This value is just the reason for using FaceID. You can add something like the following example to App/info.plist:

```xml
<key>NSFaceIDUsageDescription</key>
<string>For an easier and faster log in.</string>
```

## Biometric (Android)

To use android's BiometricPrompt api you must add the following permission to your AndroidManifest.xml:

```xml
<uses-permission android:name="android.permission.USE_BIOMETRIC">
```

## Notes

I haven't been able to thoroughly test this plugin on devices running Android.
