# Capacitor Native Biometric

Not maintained anymore use [Capgo fork](https://github.com/Cap-go/capacitor-native-biometric)

Use biometrics confirm device owner presence or authenticate users. A couple of methods are provided to handle user credentials. These are securely stored using Keychain (iOS) and Keystore (Android).

## Installation (Only supports Capacitor 3 and 4)

- `npm i capacitor-native-biometric`

## Usage

```ts
import { NativeBiometric } from "capacitor-native-biometric";

async performBiometricVerificatin(){
  const result = await NativeBiometric.isAvailable();

  if(!result.isAvailable) return;

  const isFaceID = result.biometryType == BiometryType.FACE_ID;

  const verified = await NativeBiometric.verifyIdentity({
    reason: "For easy log in",
    title: "Log in",
    subtitle: "Maybe add subtitle here?",
    description: "Maybe a description too?",
  })
    .then(() => true)
    .catch(() => false);

  if(!verified) return;

  const credentials = await NativeBiometric.getCredentials({
    server: "www.example.com",
  });
}

// Save user's credentials
NativeBiometric.setCredentials({
  username: "username",
  password: "password",
  server: "www.example.com",
}).then();

// Delete user's credentials
NativeBiometric.deleteCredentials({
  server: "www.example.com",
}).then();
```

## Methods

| Method                                                | Default | Type                       | Description                                                                                   |
| ----------------------------------------------------- | ------- | -------------------------- | --------------------------------------------------------------------------------------------- |
| `isAvailable(options?: IsAvailableOptions)`           |         | `Promise<AvailableResult>` | Gets available biometrics                                                                     |
| `verifyIdentity(options?: BiometricOptions)`          |         | `Promise<void>`            | Shows biometric prompt                                                                        |
| `setCredentials(options: SetCredentialOptions)`       |         | `Promise<void>`            | Securely stores user's credentials in Keychain (iOS) or encypts them using Keystore (Android) |
| `getCredentials(options: GetCredentialOptions)`       |         | `Promise<Credentials>`     | Retrieves user's credentials if any                                                           |
| `deleteCredentials(options: DeleteCredentialOptions)` |         | `Promise<void>`            | Removes user's credentials if any                                                             |

## Interfaces

### IsAvailableOptions

| Properties     | Default | Type      | Description                                                               |
| -------------- | ------- | --------- | ------------------------------------------------------------------------- |
| `useFallback?` |         | `boolean` | Specifies if the device should fallback to using passcode authentication. |

### AvailableResult

| Properties     | Default | Type           | Description                                              |
| -------------- | ------- | -------------- | -------------------------------------------------------- |
| `isAvailable`  |         | `boolean`      | Specifies if the devices has biometric enrollment        |
| `biometryType` |         | `BiometryType` | Specifies the available biometric hardware on the device |
| `errorCode?`   |         | `number`       | Biometric Auth Error Code                                |

### BiometryType - enum

| Properties            | Description                                                                                                                   |
| --------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| `NONE`                | There is no biometry available                                                                                                |
| `TOUCH_ID`            | TouchID is available (iOS)                                                                                                    |
| `FACE_ID`             | FaceID is available (iOS)                                                                                                     |
| `FINGERPRINT`         | Fingerprint is available (Android)                                                                                            |
| `FACE_AUTHENTICATION` | Face Authentication is available (Android)                                                                                    |
| `IRIS_AUTHENTICATION` | Iris Authentication is available (Android)                                                                                    |
| `MULTIPLE`            | Returned when device has multiple biometric features. Currently there is no way of knowing which one is being used. (Android) |

### BiometricOptions

| Properties            | Default                        | Type      | Description                                                                                               |
| --------------------- | ------------------------------ | --------- | --------------------------------------------------------------------------------------------------------- |
| `reason?`             | "For biometric authentication" | `string`  | Reason for requesting authentication in iOS. Displays in the authentication dialog presented to the user. |
| `title?`              | "Authenticate"                 | `string`  | Title for the Android prompt                                                                              |
| `subtitle?`           |                                | `string`  | Subtitle for the Android prompt                                                                           |
| `description?`        |                                | `string`  | Description for the Android prompt                                                                        |
| `negativeButtonText?` | "Cancel"                       | `string`  | Text for the negative button displayed on Android                                                         |
| `maxAttempts?`        | 1                              | `number`  | Limit the number of attempts a user can perform biometric authentication. (Android - Max 5)               |
| `useFallback?`        | `false`                        | `boolean` | Specifies if the device should fallback to using passcode authentication.                                 |

### Biometric Auth Errors

This is a plugin specific list of error codes that can be thrown on verifyIdentity failure, or set as a part of isAvailable. It consolidates Android and iOS specific Authentication Error codes into one combined error list.

| Code | Description                 | Platform                     |
| ---- | --------------------------- | ---------------------------- |
| 0    | Unknown Error               | Android, iOS                 |
| 1    | Biometrics Unavailable      | Android, iOS                 |
| 2    | User Lockout                | Android, iOS                 |
| 3    | Biometrics Not Enrolled     | Android, iOS                 |
| 4    | User Temporary Lockout      | Android (Lockout for 30sec)  |
| 10   | Authentication Failed       | Android, iOS                 |
| 11   | App Cancel                  | iOS                          |
| 12   | Invalid Context             | iOS                          |
| 13   | Not Interactive             | iOS                          |
| 14   | Passcode Not Set            | Android, iOS                 |
| 15   | System Cancel               | Android, iOS                 |
| 16   | User Cancel                 | Android, iOS                 |
| 17   | User Fallback               | Android, iOS                 |

### SetCredentialOptions

| Properties | Default | Type     | Description                                                                                                                                                             |
| ---------- | ------- | -------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `username` |         | `string` | The string used as the alias at the time of loggin in. It doesn't have to be a username. For example if you're using email to log in your users then provide the email. |
| `password` |         | `string` | The users' password                                                                                                                                                     |
| `server`   |         | `string` | Any string to identify the credentials object with                                                                                                                      |

### GetCredentialOptions

| Properties | Default | Type     | Description                                                                      |
| ---------- | ------- | -------- | -------------------------------------------------------------------------------- |
| `server`   |         | `string` | The string used to identify the credentials object when setting the credentials. |

### DeleteCredentialOptions

| Properties | Default | Type     | Description                                                                      |
| ---------- | ------- | -------- | -------------------------------------------------------------------------------- |
| `server`   |         | `string` | The string used to identify the credentials object when setting the credentials. |

### Credentials

| Properties | Default | Type     | Description                                                                 |
| ---------- | ------- | -------- | --------------------------------------------------------------------------- |
| `username` |         | `string` | The username returned from `getCredentials(options: GetCredentialOptions)`. |
| `password` |         | `string` | The password returned from `getCredentials(options: GetCredentialOptions)`. |

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

And register the plugin by adding it to you MainActivity's onCreate (Not needed for Capacitor 3):

```java
import com.epicshaggy.biometric.NativeBiometric;

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

## Contributors

[Jonthia](https://github.com/jonthia)
[One Click Web Studio](https://github.com/oneclickwebstudio)
[Brian Weasner](https://github.com/brian-weasner)
[Mohamed Diarra](https://github.com/mohdiarra)
### Want to Contribute?

Learn about contributing [HERE](./CONTRIBUTING.md)

## Notes

Hasn't been tested on Android API level 22 or lower.
