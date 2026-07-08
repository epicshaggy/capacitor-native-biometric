# Capacitor Native Biometric 
<a href="https://capgo.app/"><img src="https://capgo.app/readme-banner.svg?repo=Cap-go/capacitor-native-biometric" alt="Capgo - Instant updates for Capacitor" /></a>
 
<div align="center">
  <h2><a href="https://capgo.app/?ref=plugin_native_biometric"> ➡️ Get Instant updates for your App with Capgo</a></h2>
  <h2><a href="https://capgo.app/consulting/?ref=plugin_native_biometric"> Missing a feature? We’ll build the plugin for you 💪</a></h2>
</div>


Use biometrics confirm device owner presence or authenticate users. A couple of methods are provided to handle user credentials. These are securely stored using Keychain (iOS) and Keystore (Android).

## Why Native Biometric?

A **free**, **comprehensive** biometric authentication plugin with secure credential storage:

- **All biometric types** - Face ID, Touch ID, Fingerprint, Face Authentication, Iris, and Device Credentials (PIN, pattern, password)
- **Secure credential storage** - Keychain (iOS) and Keystore (Android) integration
- **Flexible fallback** - Optional passcode fallback when biometrics unavailable
- **Customizable UI** - Full control over prompts, titles, descriptions, button text
- **Detailed error codes** - Unified error handling across iOS and Android
- **Resume listener** - Detect biometry availability changes when app returns from background
- **Modern package management** - Supports both Swift Package Manager (SPM) and CocoaPods (SPM-ready for Capacitor 8)

Perfect for banking apps, password managers, authentication flows, and any app requiring secure user verification.

## Documentation

The most complete doc is available here: https://capgo.app/docs/plugins/native-biometric/

## ⚠️ Security Considerations

### Important: verifyIdentity() Can Be Bypassed on Rooted/Jailbroken Devices

The `verifyIdentity()` method **should not be used as the sole authentication mechanism** for sensitive operations. On rooted Android devices or jailbroken iOS devices, attackers can use tools like Frida, Xposed, or similar frameworks to:

- Hook the JavaScript bridge and force `verifyIdentity()` to return success
- Intercept native method calls and bypass biometric authentication
- Modify the app's runtime behavior to skip authentication checks

### Recommended Security Practices

1. **Use Root/Jailbreak Detection**: Protect your app by detecting compromised devices. We recommend using the **[@capgo/capacitor-is-root](https://github.com/Cap-go/capacitor-is-root)** plugin to detect rooted/jailbroken devices:

```typescript
import { IsRoot } from '@capgo/capacitor-is-root';

async function checkDeviceSecurity() {
  const { result } = await IsRoot.isRooted();
  
  if (result) {
    // Handle rooted device - show warning, restrict features, or block access
    console.warn('Device security compromised');
    return false;
  }
  return true;
}
```

2. **Never Store Sensitive Data Client-Side**: Don't rely on locally stored credentials for critical authentication. Use `verifyIdentity()` as a convenience feature, not a security boundary.

3. **Server-Side Verification**: Always validate authentication on your backend server. Biometric authentication should be used for user convenience, with the real authentication happening server-side.

4. **Implement Additional Security Layers**:
   - Use certificate pinning for API calls
   - Implement server-side session management
   - Use short-lived tokens that expire after biometric auth
   - Add anti-tampering checks

### Secure Usage Pattern

```typescript
import { NativeBiometric } from "@capgo/capacitor-native-biometric";
import { IsRoot } from '@capgo/capacitor-is-root';

async function secureAuthentication() {
  // 1. Check device security first
  const { result } = await IsRoot.isRooted();
  if (result) {
    // Handle rooted device appropriately
    // Example: showSecurityWarning() could display an alert to the user
    showSecurityWarning();
    // Optionally: disable biometric login, require re-authentication, etc.
  }

  // 2. Perform biometric authentication
  try {
    await NativeBiometric.verifyIdentity({
      reason: "Authenticate to access your account",
      title: "Biometric Login",
    });
  } catch (error) {
    console.error("Biometric authentication failed");
    return false;
  }

  // 3. Get stored credentials (if needed for convenience)
  const credentials = await NativeBiometric.getCredentials({
    server: "www.example.com",
  });

  // 4. CRITICAL: Validate credentials with your backend server
  // Example: validateWithServer() should send credentials to your API
  // and verify them server-side before granting access
  const isValid = await validateWithServer(credentials.username, credentials.password);
  
  return isValid;
}
```

### What This Plugin Provides

This plugin provides:
- ✅ Convenient local biometric authentication UI
- ✅ Secure credential storage using Keychain (iOS) and Keystore (Android)
- ✅ Protection against casual unauthorized access

This plugin does NOT provide:
- ❌ Protection against determined attackers on compromised devices
- ❌ Server-side authentication or validation
- ❌ Root/jailbreak detection (use [@capgo/capacitor-is-root](https://github.com/Cap-go/capacitor-is-root))

### Recent Security Improvements (v8.2.0+)

**Android Encryption Enhancement**: The Android implementation now uses properly randomized Initialization Vectors (IVs) for AES-GCM encryption of stored credentials. Previous versions used a fixed IV, which is a cryptographic vulnerability. 

**Automatic Migration**: The plugin automatically handles credentials encrypted with the older method:
- When reading credentials, it first attempts the new secure format, then falls back to the legacy format if needed
- When saving credentials, they are always encrypted using the new secure format
- No action required from users - migration happens transparently on first credential save after update

**Recommendation**: After updating to v8.2.0+, users should re-save their credentials to ensure they're encrypted with the improved format. This happens automatically when users authenticate and save credentials again.

## Compatibility

| Plugin version | Capacitor compatibility | Maintained |
| -------------- | ----------------------- | ---------- |
| v8.\*.\*       | v8.\*.\*                | ✅          |
| v7.\*.\*       | v7.\*.\*                | On demand   |
| v6.\*.\*       | v6.\*.\*                | ❌          |
| v5.\*.\*       | v5.\*.\*                | ❌          |

> **Note:** The major version of this plugin follows the major version of Capacitor. Use the version that matches your Capacitor installation (e.g., plugin v8 for Capacitor 8). Only the latest major version is actively maintained.

## Installation (Only supports Capacitor 7)

You can use our AI-Assisted Setup to install the plugin. Add the Capgo skills to your AI tool using the following command:

```bash
npx skills add https://github.com/cap-go/capacitor-skills --skill capacitor-plugins
```

Then use the following prompt:

```text
Use the `capacitor-plugins` skill from `cap-go/capacitor-skills` to install the `@capgo/capacitor-native-biometric` plugin in my project.
```

If you prefer Manual Setup, install the plugin by running the following commands and follow the platform-specific instructions below:

- `npm i @capgo/capacitor-native-biometric`

## Usage

⚠️ **Important**: Before implementing biometric authentication, review the [Security Considerations](#️-security-considerations) section to understand limitations and best practices for secure implementation.

```ts
import { NativeBiometric, BiometryType } from "@capgo/capacitor-native-biometric";

async performBiometricVerification(){
  const result = await NativeBiometric.isAvailable();

  if(!result.isAvailable) return;

  // Check the biometry type for display purposes
  // IMPORTANT: Always use isAvailable for logic decisions, not biometryType
  const isFaceID = result.biometryType == BiometryType.FACE_ID;

  // Check if device has PIN/pattern/password set
  console.log('Device is secure:', result.deviceIsSecure);

  // Check if strong biometry (Face ID, Touch ID, fingerprint) is available
  console.log('Strong biometry available:', result.strongBiometryIsAvailable);

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
  
  // IMPORTANT: Always validate credentials with your backend server
  // Do not trust client-side verification alone
}

// Save user's credentials
NativeBiometric.setCredentials({
  username: "username",
  password: "password",
  server: "www.example.com",
}).then();

// Check if credentials are already saved
const isSaved = await NativeBiometric.isCredentialsSaved({
  server: "www.example.com",
});
console.log('Credentials saved:', isSaved.isSaved);

// Delete user's credentials
NativeBiometric.deleteCredentials({
  server: "www.example.com",
}).then();

// Listen for biometry availability changes when app resumes from background
const handle = await NativeBiometric.addListener('biometryChange', (result) => {
  console.log('Biometry availability changed:', result.isAvailable);
  console.log('Biometry type:', result.biometryType);
});

// To remove the listener when no longer needed:
// await handle.remove();
```

### Complete Login Flow Example

This example shows how to use `isCredentialsSaved()` to check if credentials are already saved before showing a "save credentials" popup:

```ts
// After successful login
async handleLoginSuccess(username: string, password: string) {
  // Check if biometric authentication is available
  const result = await NativeBiometric.isAvailable({ useFallback: true });
  
  if (!result.isAvailable) {
    // Biometrics not available - go to home page directly
    this.navigateToHome();
    return;
  }
  
  // Check if credentials are already saved
  const checkCredentials = await NativeBiometric.isCredentialsSaved({
    server: "www.example.com"
  });
  
  if (checkCredentials.isSaved) {
    // Credentials already saved - go to home page
    this.navigateToHome();
  } else {
    // No credentials saved - show save credentials popup
    this.showSaveCredentialsPopup(username, password);
  }
}

// Save credentials when user confirms
async saveCredentials(username: string, password: string) {
  await NativeBiometric.setCredentials({
    username: username,
    password: password,
    server: "www.example.com",
  });
  this.navigateToHome();
}
```

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

<docgen-index>

* [`isAvailable(...)`](#isavailable)
* [`addListener('biometryChange', ...)`](#addlistenerbiometrychange-)
* [`verifyIdentity(...)`](#verifyidentity)
* [`getCredentials(...)`](#getcredentials)
* [`setCredentials(...)`](#setcredentials)
* [`deleteCredentials(...)`](#deletecredentials)
* [`getSecureCredentials(...)`](#getsecurecredentials)
* [`isCredentialsSaved(...)`](#iscredentialssaved)
* [`getPluginVersion()`](#getpluginversion)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)
* [Enums](#enums)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### isAvailable(...)

```typescript
isAvailable(options?: IsAvailableOptions | undefined) => Promise<AvailableResult>
```

Checks if biometric authentication hardware is available.

| Param         | Type                                                              |
| ------------- | ----------------------------------------------------------------- |
| **`options`** | <code><a href="#isavailableoptions">IsAvailableOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#availableresult">AvailableResult</a>&gt;</code>

**Since:** 1.0.0

--------------------


### addListener('biometryChange', ...)

```typescript
addListener(eventName: 'biometryChange', listener: BiometryChangeListener) => Promise<PluginListenerHandle>
```

Adds a listener that is called when the app resumes from background.
This is useful to detect if biometry availability has changed while
the app was in the background (e.g., user enrolled/unenrolled biometrics).

| Param           | Type                                                                      | Description                                                                                  |
| --------------- | ------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| **`eventName`** | <code>'biometryChange'</code>                                             | - Must be 'biometryChange'                                                                   |
| **`listener`**  | <code><a href="#biometrychangelistener">BiometryChangeListener</a></code> | - Callback function that receives the updated <a href="#availableresult">AvailableResult</a> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

**Since:** 7.6.0

--------------------


### verifyIdentity(...)

```typescript
verifyIdentity(options?: BiometricOptions | undefined) => Promise<void>
```

Prompts the user to authenticate with biometrics.

| Param         | Type                                                          |
| ------------- | ------------------------------------------------------------- |
| **`options`** | <code><a href="#biometricoptions">BiometricOptions</a></code> |

**Since:** 1.0.0

--------------------


### getCredentials(...)

```typescript
getCredentials(options: GetCredentialOptions) => Promise<Credentials>
```

Gets the stored credentials for a given server.

| Param         | Type                                                                  |
| ------------- | --------------------------------------------------------------------- |
| **`options`** | <code><a href="#getcredentialoptions">GetCredentialOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#credentials">Credentials</a>&gt;</code>

**Since:** 1.0.0

--------------------


### setCredentials(...)

```typescript
setCredentials(options: SetCredentialOptions) => Promise<void>
```

Stores the given credentials for a given server.

| Param         | Type                                                                  |
| ------------- | --------------------------------------------------------------------- |
| **`options`** | <code><a href="#setcredentialoptions">SetCredentialOptions</a></code> |

**Since:** 1.0.0

--------------------


### deleteCredentials(...)

```typescript
deleteCredentials(options: DeleteCredentialOptions) => Promise<void>
```

Deletes the stored credentials for a given server.

| Param         | Type                                                                        |
| ------------- | --------------------------------------------------------------------------- |
| **`options`** | <code><a href="#deletecredentialoptions">DeleteCredentialOptions</a></code> |

**Since:** 1.0.0

--------------------


### getSecureCredentials(...)

```typescript
getSecureCredentials(options: GetSecureCredentialsOptions) => Promise<Credentials>
```

Gets the stored credentials for a given server, requiring biometric authentication.
Credentials must have been stored with accessControl set to BIOMETRY_CURRENT_SET or BIOMETRY_ANY.

On iOS, the system automatically shows the biometric prompt when accessing the protected Keychain item.
On Android, BiometricPrompt is shown with a CryptoObject bound to the credential decryption key.

| Param         | Type                                                                                |
| ------------- | ----------------------------------------------------------------------------------- |
| **`options`** | <code><a href="#getsecurecredentialsoptions">GetSecureCredentialsOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#credentials">Credentials</a>&gt;</code>

**Since:** 8.4.0

--------------------


### isCredentialsSaved(...)

```typescript
isCredentialsSaved(options: IsCredentialsSavedOptions) => Promise<IsCredentialsSavedResult>
```

Checks if credentials are already saved for a given server.

| Param         | Type                                                                            |
| ------------- | ------------------------------------------------------------------------------- |
| **`options`** | <code><a href="#iscredentialssavedoptions">IsCredentialsSavedOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#iscredentialssavedresult">IsCredentialsSavedResult</a>&gt;</code>

**Since:** 7.3.0

--------------------


### getPluginVersion()

```typescript
getPluginVersion() => Promise<{ version: string; }>
```

Get the native Capacitor plugin version.

**Returns:** <code>Promise&lt;{ version: string; }&gt;</code>

**Since:** 1.0.0

--------------------


### Interfaces


#### AvailableResult

Result from isAvailable() method indicating biometric authentication availability.

| Prop                            | Type                                                                      | Description                                                                                                                                                                                                                                                                              |
| ------------------------------- | ------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`isAvailable`**               | <code>boolean</code>                                                      | Whether authentication is available. On Android, weak-only biometrics (such as some face unlock implementations) are available for `verifyIdentity()` when no `allowedBiometryTypes` filter excludes them. If `useFallback` is true, PIN/pattern/password can also make this value true. |
| **`authenticationStrength`**    | <code><a href="#authenticationstrength">AuthenticationStrength</a></code> | The strength of available authentication method (STRONG, WEAK, or NONE)                                                                                                                                                                                                                  |
| **`biometryType`**              | <code><a href="#biometrytype">BiometryType</a></code>                     | The primary biometry type available on the device. On Android devices with multiple biometry types, this returns MULTIPLE. Use this for display purposes only - always use isAvailable for logic decisions.                                                                              |
| **`deviceIsSecure`**            | <code>boolean</code>                                                      | Whether the device has a secure lock screen (PIN, pattern, or password). This is independent of biometric enrollment.                                                                                                                                                                    |
| **`strongBiometryIsAvailable`** | <code>boolean</code>                                                      | Whether strong biometry (Face ID, Touch ID, or fingerprint on devices that consider it strong) is specifically available, separate from weak biometry or device credentials.                                                                                                             |
| **`errorCode`**                 | <code><a href="#biometricautherror">BiometricAuthError</a></code>         | Error code from <a href="#biometricautherror">BiometricAuthError</a> enum. Only present when isAvailable is false. Indicates why biometric authentication is not available.                                                                                                              |


#### IsAvailableOptions

| Prop              | Type                 | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| ----------------- | -------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`useFallback`** | <code>boolean</code> | Whether passcode or device credentials should count toward biometric availability when no biometric is enrolled or available. - On iOS, this affects both `isAvailable()` and `verifyIdentity()`. - On Android, this is honored by `isAvailable()` only — the native check computes `fallbackAvailable = useFallback && deviceIsSecure` and reports availability accordingly. The `verifyIdentity()` flow ignores this option on Android due to BiometricPrompt API constraints (DEVICE_CREDENTIAL authenticator and negative button are mutually exclusive); use <a href="#biometricoptions">`BiometricOptions.useFallback`</a> (iOS-only) to control the auth-dialog fallback there. |


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


#### BiometricOptions

| Prop                       | Type                        | Description                                                                                                                                                                                                                                                                                                                                                                                                                         | Default        |
| -------------------------- | --------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------- |
| **`reason`**               | <code>string</code>         |                                                                                                                                                                                                                                                                                                                                                                                                                                     |                |
| **`title`**                | <code>string</code>         |                                                                                                                                                                                                                                                                                                                                                                                                                                     |                |
| **`subtitle`**             | <code>string</code>         |                                                                                                                                                                                                                                                                                                                                                                                                                                     |                |
| **`description`**          | <code>string</code>         |                                                                                                                                                                                                                                                                                                                                                                                                                                     |                |
| **`negativeButtonText`**   | <code>string</code>         |                                                                                                                                                                                                                                                                                                                                                                                                                                     |                |
| **`useFallback`**          | <code>boolean</code>        | Only for iOS. Specifies if should fallback to passcode authentication if biometric authentication fails. On Android, this parameter is ignored due to BiometricPrompt API constraints: DEVICE_CREDENTIAL authenticator and negative button (cancel) are mutually exclusive.                                                                                                                                                         |                |
| **`fallbackTitle`**        | <code>string</code>         | Only for iOS. Set the text for the fallback button in the authentication dialog. If this property is not specified, the default text is set by the system.                                                                                                                                                                                                                                                                          |                |
| **`maxAttempts`**          | <code>number</code>         | Only for Android. Set a maximum number of attempts for biometric authentication. The maximum allowed by android is 5.                                                                                                                                                                                                                                                                                                               | <code>1</code> |
| **`allowedBiometryTypes`** | <code>BiometryType[]</code> | Only for Android. Specify which biometry types are allowed for authentication. If not specified, all enrolled biometric classes (strong and weak) are allowed. On Android, face unlock is often classified as weak biometrics — include `BiometryType.FACE_AUTHENTICATION` or omit this option to allow it. Use <a href="#biometrytype">`BiometryType.DEVICE_CREDENTIAL`</a> for PIN/pattern/password (disables the cancel button). |                |


#### Credentials

| Prop           | Type                |
| -------------- | ------------------- |
| **`username`** | <code>string</code> |
| **`password`** | <code>string</code> |


#### GetCredentialOptions

| Prop         | Type                |
| ------------ | ------------------- |
| **`server`** | <code>string</code> |


#### SetCredentialOptions

| Prop                       | Type                                                    | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | Default                            | Since  |
| -------------------------- | ------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------- | ------ |
| **`username`**             | <code>string</code>                                     |                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |                                    |        |
| **`password`**             | <code>string</code>                                     |                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |                                    |        |
| **`server`**               | <code>string</code>                                     |                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |                                    |        |
| **`accessControl`**        | <code><a href="#accesscontrol">AccessControl</a></code> | Access control level for the stored credentials. When set to BIOMETRY_CURRENT_SET or BIOMETRY_ANY, the credentials are hardware-protected and require biometric authentication to access. On iOS, this adds SecAccessControl to the Keychain item. On Android, this creates a biometric-protected Keystore key and requires BiometricPrompt authentication for both storing and retrieving credentials.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | <code>AccessControl.NONE</code>    | 8.4.0  |
| **`authValidityDuration`** | <code>number</code>                                     | Only for Android. Ignored on iOS and web. Only meaningful together with `accessControl` set to BIOMETRY_CURRENT_SET or BIOMETRY_ANY. Number of seconds a successful biometric authentication remains valid for Keystore key use. When `0` or omitted (the default), the Keystore key requires a fresh authentication for every operation — each `getSecureCredentials()` call shows a `BiometricPrompt` cryptographically bound to that specific read via a `CryptoObject`. This is the strongest mode: it guarantees no code path can use the key without a live biometric check. When set to a value greater than `0`, one successful biometric authentication authorizes Keystore key use for that many seconds. Subsequent `getSecureCredentials()` calls within the window succeed without showing a prompt. This trades security for convenience: any in-app code that can reach the decryption call — not just the code path that triggered the prompt — can silently read the credentials for the remainder of the window. | <code>0</code>                     | 8.5.0  |
| **`title`**                | <code>string</code>                                     | Title for the biometric prompt shown while protecting credentials. Only for Android.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | <code>"Protect Credentials"</code> | 8.4.14 |
| **`negativeButtonText`**   | <code>string</code>                                     | Text for the negative/cancel button in the biometric prompt. Only for Android.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | <code>"Cancel"</code>              | 8.4.14 |


#### DeleteCredentialOptions

| Prop         | Type                |
| ------------ | ------------------- |
| **`server`** | <code>string</code> |


#### GetSecureCredentialsOptions

| Prop                     | Type                | Description                                                                                                |
| ------------------------ | ------------------- | ---------------------------------------------------------------------------------------------------------- |
| **`server`**             | <code>string</code> |                                                                                                            |
| **`reason`**             | <code>string</code> | Reason for requesting biometric authentication. Displayed in the biometric prompt on both iOS and Android. |
| **`title`**              | <code>string</code> | Title for the biometric prompt. Only for Android.                                                          |
| **`subtitle`**           | <code>string</code> | Subtitle for the biometric prompt. Only for Android.                                                       |
| **`description`**        | <code>string</code> | Description for the biometric prompt. Only for Android.                                                    |
| **`negativeButtonText`** | <code>string</code> | Text for the negative/cancel button. Only for Android.                                                     |


#### IsCredentialsSavedResult

| Prop          | Type                 |
| ------------- | -------------------- |
| **`isSaved`** | <code>boolean</code> |


#### IsCredentialsSavedOptions

| Prop         | Type                |
| ------------ | ------------------- |
| **`server`** | <code>string</code> |


### Type Aliases


#### BiometryChangeListener

Callback type for biometry change listener

<code>(result: <a href="#availableresult">AvailableResult</a>): void</code>


### Enums


#### AuthenticationStrength

| Members      | Value          | Description                                                                                                                                                                                      |
| ------------ | -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **`NONE`**   | <code>0</code> | No authentication available, even if PIN is available but useFallback = false                                                                                                                    |
| **`STRONG`** | <code>1</code> | Strong authentication: Face ID on iOS, fingerprints on devices that consider fingerprints strong (Android). Note: PIN/pattern/password is NEVER considered STRONG, even when useFallback = true. |
| **`WEAK`**   | <code>2</code> | Weak authentication: Face authentication on Android devices that consider face weak, or PIN/pattern/password if useFallback = true (PIN is always WEAK, never STRONG).                           |


#### BiometryType

| Members                   | Value          |
| ------------------------- | -------------- |
| **`NONE`**                | <code>0</code> |
| **`TOUCH_ID`**            | <code>1</code> |
| **`FACE_ID`**             | <code>2</code> |
| **`FINGERPRINT`**         | <code>3</code> |
| **`FACE_AUTHENTICATION`** | <code>4</code> |
| **`IRIS_AUTHENTICATION`** | <code>5</code> |
| **`MULTIPLE`**            | <code>6</code> |
| **`DEVICE_CREDENTIAL`**   | <code>7</code> |


#### BiometricAuthError

| Members                       | Value           | Description                                                                           |
| ----------------------------- | --------------- | ------------------------------------------------------------------------------------- |
| **`UNKNOWN_ERROR`**           | <code>0</code>  | Unknown error occurred                                                                |
| **`BIOMETRICS_UNAVAILABLE`**  | <code>1</code>  | Biometrics are unavailable (no hardware or hardware error) Platform: Android, iOS     |
| **`USER_LOCKOUT`**            | <code>2</code>  | User has been locked out due to too many failed attempts Platform: Android, iOS       |
| **`BIOMETRICS_NOT_ENROLLED`** | <code>3</code>  | No biometrics are enrolled on the device Platform: Android, iOS                       |
| **`USER_TEMPORARY_LOCKOUT`**  | <code>4</code>  | User is temporarily locked out (Android: 30 second lockout) Platform: Android         |
| **`AUTHENTICATION_FAILED`**   | <code>10</code> | Authentication failed (user did not authenticate successfully) Platform: Android, iOS |
| **`APP_CANCEL`**              | <code>11</code> | App canceled the authentication (iOS only) Platform: iOS                              |
| **`INVALID_CONTEXT`**         | <code>12</code> | Invalid context (iOS only) Platform: iOS                                              |
| **`NOT_INTERACTIVE`**         | <code>13</code> | Authentication was not interactive (iOS only) Platform: iOS                           |
| **`PASSCODE_NOT_SET`**        | <code>14</code> | Passcode/PIN is not set on the device Platform: Android, iOS                          |
| **`SYSTEM_CANCEL`**           | <code>15</code> | System canceled the authentication (e.g., due to screen lock) Platform: Android, iOS  |
| **`USER_CANCEL`**             | <code>16</code> | User canceled the authentication Platform: Android, iOS                               |
| **`USER_FALLBACK`**           | <code>17</code> | User chose to use fallback authentication method Platform: Android, iOS               |


#### AccessControl

| Members                    | Value          | Description                                                                                                                                                                                                                   |
| -------------------------- | -------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`NONE`**                 | <code>0</code> | No biometric protection. <a href="#credentials">Credentials</a> are accessible without authentication. This is the default behavior for backward compatibility.                                                               |
| **`BIOMETRY_CURRENT_SET`** | <code>1</code> | Biometric authentication required for credential access. Credentials are invalidated if biometrics change (e.g., new fingerprint enrolled). More secure but credentials are lost if user modifies their biometric enrollment. |
| **`BIOMETRY_ANY`**         | <code>2</code> | Biometric authentication required for credential access. Credentials survive new biometric enrollment (e.g., adding a new fingerprint). More lenient — recommended for most apps.                                             |

</docgen-api>
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

### Important Note About biometryType on Android

The `biometryType` field indicates what biometric hardware is present, but **hardware presence does not guarantee availability**. Some Android devices report face authentication hardware but don't make it available to apps.

**Always use `isAvailable` for logic decisions**, not `biometryType`. The `biometryType` field should only be used for display purposes (e.g., showing "Use Face ID" vs "Use Fingerprint" in your UI).

## Web Platform

This plugin provides a dummy implementation for in-browser development and testing. On web:
- `isAvailable()` returns `{ isAvailable: true, ... }` simulating biometric availability
- `addListener()` returns a no-op handle
- `verifyIdentity()` always succeeds (no actual authentication)
- Credential methods use in-memory storage (credentials stored in a Map, cleared on page refresh)

This allows you to develop and test your app in the browser without errors. Note that real biometric authentication is only available on iOS and Android platforms.

## Contributors

[Jonthia](https://github.com/jonthia)
[QliQ.dev](https://github.com/qliqdev)
[Brian Weasner](https://github.com/brian-weasner)
[Mohamed Diarra](https://github.com/mohdiarra)
### Want to Contribute?

Learn about contributing [HERE](./CONTRIBUTING.md)

## Notes

Hasn't been tested on Android API level 22 or lower.
