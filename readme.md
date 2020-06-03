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
    const fingerprint = result.fingerprint;
    const faceAuth = result.faceAuth;
    const irisAuth = result.irisAuth;
  },
  (error) => {
    //Couldn't check availability
  }
);

NativeBiometric.verify().then(
  ({
    reason: "For easy log in",
    title: "Log in",
    subtitle: "Maybe add subtitle here?",
    description: "Maybe a description too?",
  }) => {
    //Authentication successful
  },
  (error) => {
    //Failed to authenticate
  }
);
```

## Methods

| Method                             | Default | Type                        | Description               |
| ---------------------------------- | ------- | --------------------------- | ------------------------- |
| isAvailable()                      |         | `Promise<AvailableOptions>` | Gets available biometrics |
| verify(options?: BiometricOptions) |         | `Promise<any>`              | Shows the prompt          |

## Interfaces

AvailableOptions

| Properties  | Default | Type    | Description                                                       |
| ----------- | ------- | ------- | ----------------------------------------------------------------- |
| has         |         | boolean | Specifies if the devices has biometric enrollment                 |
| touchId     |         | boolean | Specifies if the devices has TouchID (iOS)                        |
| faceId      |         | boolean | Specifies if the devices has FaceID (iOS)                         |
| fingerprint |         | boolean | Specifies if the devices has fingerprint authentication (Android) |
| faceAuth    |         | boolean | Specifies if the devices has face authentication (Android)        |
| irisAuth    |         | boolean | Specifies if the devices has iris authentication (Android)        |

BiometricOptions

| Properties   | Default                        | Type   | Description                                                                                               |
| ------------ | ------------------------------ | ------ | --------------------------------------------------------------------------------------------------------- |
| reason?      | "For biometric authentication" | string | Reason for requesting authentication in iOS. Displays in the authentication dialog presented to the user. |
| title?       | "Authenticate"                 | string | Title for the Android prompt                                                                              |
| subtitle?    |                                | string | Subtitle for the Android prompt                                                                           |
| description? |                                | string | Description for the Android prompt                                                                        |

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

And register the plugin by adding it to you MainActivity's onCreate:

```java
import com.example.myapp.EchoPlugin;

public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Initializes the Bridge
    this.init(savedInstanceState, new ArrayList<Class<? extends Plugin>>() {{
      // Additional plugins you've installed go here
      // Ex: add(TotallyAwesomePlugin.class);
      add(NativeBiometric.class);
    }});
  }
}
```

## Notes

I haven't been able to thoroughly test this plugin on devices running Android.
