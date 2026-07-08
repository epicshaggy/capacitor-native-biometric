import type { PluginListenerHandle } from '@capacitor/core';

export enum BiometryType {
  // Android, iOS
  NONE = 0,
  // iOS
  TOUCH_ID = 1,
  // iOS
  FACE_ID = 2,
  // Android
  FINGERPRINT = 3,
  // Android
  FACE_AUTHENTICATION = 4,
  // Android
  IRIS_AUTHENTICATION = 5,
  // Android
  MULTIPLE = 6,
  // Android - Device credentials (PIN, pattern, or password)
  DEVICE_CREDENTIAL = 7,
}

export enum AuthenticationStrength {
  /**
   * No authentication available, even if PIN is available but useFallback = false
   */
  NONE = 0,
  /**
   * Strong authentication: Face ID on iOS, fingerprints on devices that consider fingerprints strong (Android).
   * Note: PIN/pattern/password is NEVER considered STRONG, even when useFallback = true.
   */
  STRONG = 1,
  /**
   * Weak authentication: Face authentication on Android devices that consider face weak,
   * or PIN/pattern/password if useFallback = true (PIN is always WEAK, never STRONG).
   */
  WEAK = 2,
}

export enum AccessControl {
  /**
   * No biometric protection. Credentials are accessible without authentication.
   * This is the default behavior for backward compatibility.
   */
  NONE = 0,
  /**
   * Biometric authentication required for credential access.
   * Credentials are invalidated if biometrics change (e.g., new fingerprint enrolled).
   * More secure but credentials are lost if user modifies their biometric enrollment.
   */
  BIOMETRY_CURRENT_SET = 1,
  /**
   * Biometric authentication required for credential access.
   * Credentials survive new biometric enrollment (e.g., adding a new fingerprint).
   * More lenient — recommended for most apps.
   */
  BIOMETRY_ANY = 2,
}

export interface Credentials {
  username: string;
  password: string;
}

export interface IsAvailableOptions {
  /**
   * Whether passcode or device credentials should count toward biometric availability
   * when no biometric is enrolled or available.
   *
   * - On iOS, this affects both `isAvailable()` and `verifyIdentity()`.
   * - On Android, this is honored by `isAvailable()` only — the native check computes
   *   `fallbackAvailable = useFallback && deviceIsSecure` and reports availability
   *   accordingly. The `verifyIdentity()` flow ignores this option on Android due to
   *   BiometricPrompt API constraints (DEVICE_CREDENTIAL authenticator and negative
   *   button are mutually exclusive); use `BiometricOptions.useFallback` (iOS-only) to
   *   control the auth-dialog fallback there.
   */
  useFallback: boolean;
}

/**
 * Result from isAvailable() method indicating biometric authentication availability.
 */
export interface AvailableResult {
  /**
   * Whether authentication is available.
   *
   * On Android, weak-only biometrics (such as some face unlock implementations)
   * are available for `verifyIdentity()` when no `allowedBiometryTypes` filter
   * excludes them. If `useFallback` is true, PIN/pattern/password can also make
   * this value true.
   */
  isAvailable: boolean;
  /**
   * The strength of available authentication method (STRONG, WEAK, or NONE)
   */
  authenticationStrength: AuthenticationStrength;
  /**
   * The primary biometry type available on the device.
   * On Android devices with multiple biometry types, this returns MULTIPLE.
   * Use this for display purposes only - always use isAvailable for logic decisions.
   */
  biometryType: BiometryType;
  /**
   * Whether the device has a secure lock screen (PIN, pattern, or password).
   * This is independent of biometric enrollment.
   */
  deviceIsSecure: boolean;
  /**
   * Whether strong biometry (Face ID, Touch ID, or fingerprint on devices that consider it strong)
   * is specifically available, separate from weak biometry or device credentials.
   */
  strongBiometryIsAvailable: boolean;
  /**
   * Error code from BiometricAuthError enum. Only present when isAvailable is false.
   * Indicates why biometric authentication is not available.
   * @see BiometricAuthError
   */
  errorCode?: BiometricAuthError;
}

export interface BiometricOptions {
  reason?: string;
  title?: string;
  subtitle?: string;
  description?: string;
  negativeButtonText?: string;
  /**
   * Only for iOS.
   * Specifies if should fallback to passcode authentication if biometric authentication fails.
   * On Android, this parameter is ignored due to BiometricPrompt API constraints:
   * DEVICE_CREDENTIAL authenticator and negative button (cancel) are mutually exclusive.
   */
  useFallback?: boolean;
  /**
   * Only for iOS.
   * Set the text for the fallback button in the authentication dialog.
   * If this property is not specified, the default text is set by the system.
   */
  fallbackTitle?: string;
  /**
   * Only for Android.
   * Set a maximum number of attempts for biometric authentication. The maximum allowed by android is 5.
   * @default 1
   */
  maxAttempts?: number;
  /**
   * Only for Android.
   * Specify which biometry types are allowed for authentication.
   * If not specified, all enrolled biometric classes (strong and weak) are allowed.
   * On Android, face unlock is often classified as weak biometrics — include
   * `BiometryType.FACE_AUTHENTICATION` or omit this option to allow it.
   * Use `BiometryType.DEVICE_CREDENTIAL` for PIN/pattern/password (disables the cancel button).
   * @example [BiometryType.FINGERPRINT, BiometryType.FACE_AUTHENTICATION]
   */
  allowedBiometryTypes?: BiometryType[];
}

export interface GetCredentialOptions {
  server: string;
}

export interface SetCredentialOptions {
  username: string;
  password: string;
  server: string;
  /**
   * Access control level for the stored credentials.
   * When set to BIOMETRY_CURRENT_SET or BIOMETRY_ANY, the credentials are
   * hardware-protected and require biometric authentication to access.
   *
   * On iOS, this adds SecAccessControl to the Keychain item.
   * On Android, this creates a biometric-protected Keystore key and requires
   * BiometricPrompt authentication for both storing and retrieving credentials.
   *
   * @default AccessControl.NONE
   * @since 8.4.0
   */
  accessControl?: AccessControl;
  /**
   * Only for Android. Ignored on iOS and web.
   * Only meaningful together with `accessControl` set to BIOMETRY_CURRENT_SET or BIOMETRY_ANY.
   *
   * Number of seconds a successful biometric authentication remains valid for
   * Keystore key use. When `0` or omitted (the default), the Keystore key requires
   * a fresh authentication for every operation — each `getSecureCredentials()` call
   * shows a `BiometricPrompt` cryptographically bound to that specific read via a
   * `CryptoObject`. This is the strongest mode: it guarantees no code path can use
   * the key without a live biometric check.
   *
   * When set to a value greater than `0`, one successful biometric authentication
   * authorizes Keystore key use for that many seconds. Subsequent
   * `getSecureCredentials()` calls within the window succeed without showing a
   * prompt. This trades security for convenience: any in-app code that can reach
   * the decryption call — not just the code path that triggered the prompt — can
   * silently read the credentials for the remainder of the window.
   *
   * @default 0
   * @since 8.5.0
   */
  authValidityDuration?: number;
  /**
   * Title for the biometric prompt shown while protecting credentials.
   * Only for Android.
   *
   * @default "Protect Credentials"
   * @since 8.4.14
   */
  title?: string;
  /**
   * Text for the negative/cancel button in the biometric prompt.
   * Only for Android.
   *
   * @default "Cancel"
   * @since 8.4.14
   */
  negativeButtonText?: string;
}

export interface GetSecureCredentialsOptions {
  server: string;
  /**
   * Reason for requesting biometric authentication.
   * Displayed in the biometric prompt on both iOS and Android.
   */
  reason?: string;
  /**
   * Title for the biometric prompt.
   * Only for Android.
   */
  title?: string;
  /**
   * Subtitle for the biometric prompt.
   * Only for Android.
   */
  subtitle?: string;
  /**
   * Description for the biometric prompt.
   * Only for Android.
   */
  description?: string;
  /**
   * Text for the negative/cancel button.
   * Only for Android.
   */
  negativeButtonText?: string;
}

export interface DeleteCredentialOptions {
  server: string;
}

export interface IsCredentialsSavedOptions {
  server: string;
}

export interface IsCredentialsSavedResult {
  isSaved: boolean;
}

/**
 * Biometric authentication error codes.
 * These error codes are used in both isAvailable() and verifyIdentity() methods.
 *
 * Keep this in sync with BiometricAuthError in README.md
 * Update whenever `convertToPluginErrorCode` functions are modified
 */
export enum BiometricAuthError {
  /**
   * Unknown error occurred
   */
  UNKNOWN_ERROR = 0,
  /**
   * Biometrics are unavailable (no hardware or hardware error)
   * Platform: Android, iOS
   */
  BIOMETRICS_UNAVAILABLE = 1,
  /**
   * User has been locked out due to too many failed attempts
   * Platform: Android, iOS
   */
  USER_LOCKOUT = 2,
  /**
   * No biometrics are enrolled on the device
   * Platform: Android, iOS
   */
  BIOMETRICS_NOT_ENROLLED = 3,
  /**
   * User is temporarily locked out (Android: 30 second lockout)
   * Platform: Android
   */
  USER_TEMPORARY_LOCKOUT = 4,
  /**
   * Authentication failed (user did not authenticate successfully)
   * Platform: Android, iOS
   */
  AUTHENTICATION_FAILED = 10,
  /**
   * App canceled the authentication (iOS only)
   * Platform: iOS
   */
  APP_CANCEL = 11,
  /**
   * Invalid context (iOS only)
   * Platform: iOS
   */
  INVALID_CONTEXT = 12,
  /**
   * Authentication was not interactive (iOS only)
   * Platform: iOS
   */
  NOT_INTERACTIVE = 13,
  /**
   * Passcode/PIN is not set on the device
   * Platform: Android, iOS
   */
  PASSCODE_NOT_SET = 14,
  /**
   * System canceled the authentication (e.g., due to screen lock)
   * Platform: Android, iOS
   */
  SYSTEM_CANCEL = 15,
  /**
   * User canceled the authentication
   * Platform: Android, iOS
   */
  USER_CANCEL = 16,
  /**
   * User chose to use fallback authentication method
   * Platform: Android, iOS
   */
  USER_FALLBACK = 17,
}

/**
 * Callback type for biometry change listener
 */
export type BiometryChangeListener = (result: AvailableResult) => void;

export interface NativeBiometricPlugin {
  /**
   * Checks if biometric authentication hardware is available.
   * @param {IsAvailableOptions} [options]
   * @returns {Promise<AvailableResult>}
   * @memberof NativeBiometricPlugin
   * @since 1.0.0
   */
  isAvailable(options?: IsAvailableOptions): Promise<AvailableResult>;

  /**
   * Adds a listener that is called when the app resumes from background.
   * This is useful to detect if biometry availability has changed while
   * the app was in the background (e.g., user enrolled/unenrolled biometrics).
   *
   * @param eventName - Must be 'biometryChange'
   * @param {BiometryChangeListener} listener - Callback function that receives the updated AvailableResult
   * @returns {Promise<PluginListenerHandle>} Handle to remove the listener
   * @since 7.6.0
   *
   * @example
   * ```typescript
   * const handle = await NativeBiometric.addListener('biometryChange', (result) => {
   *   console.log('Biometry availability changed:', result.isAvailable);
   * });
   *
   * // To remove the listener:
   * await handle.remove();
   * ```
   */
  addListener(eventName: 'biometryChange', listener: BiometryChangeListener): Promise<PluginListenerHandle>;
  /**
   * Prompts the user to authenticate with biometrics.
   * @param {BiometricOptions} [options]
   * @returns {Promise<any>}
   * @memberof NativeBiometricPlugin
   * @since 1.0.0
   */
  verifyIdentity(options?: BiometricOptions): Promise<void>;
  /**
   * Gets the stored credentials for a given server.
   * @param {GetCredentialOptions} options
   * @returns {Promise<Credentials>}
   * @memberof NativeBiometricPlugin
   * @since 1.0.0
   */
  getCredentials(options: GetCredentialOptions): Promise<Credentials>;
  /**
   * Stores the given credentials for a given server.
   * @param {SetCredentialOptions} options
   * @returns {Promise<any>}
   * @memberof NativeBiometricPlugin
   * @since 1.0.0
   */
  setCredentials(options: SetCredentialOptions): Promise<void>;
  /**
   * Deletes the stored credentials for a given server.
   * @param {DeleteCredentialOptions} options
   * @returns {Promise<any>}
   * @memberof NativeBiometricPlugin
   * @since 1.0.0
   */
  deleteCredentials(options: DeleteCredentialOptions): Promise<void>;
  /**
   * Gets the stored credentials for a given server, requiring biometric authentication.
   * Credentials must have been stored with accessControl set to BIOMETRY_CURRENT_SET or BIOMETRY_ANY.
   *
   * On iOS, the system automatically shows the biometric prompt when accessing the protected Keychain item.
   * On Android, BiometricPrompt is shown with a CryptoObject bound to the credential decryption key.
   *
   * @param {GetSecureCredentialsOptions} options
   * @returns {Promise<Credentials>}
   * @memberof NativeBiometricPlugin
   * @since 8.4.0
   */
  getSecureCredentials(options: GetSecureCredentialsOptions): Promise<Credentials>;
  /**
   * Checks if credentials are already saved for a given server.
   * @param {IsCredentialsSavedOptions} options
   * @returns {Promise<IsCredentialsSavedResult>}
   * @memberof NativeBiometricPlugin
   * @since 7.3.0
   */
  isCredentialsSaved(options: IsCredentialsSavedOptions): Promise<IsCredentialsSavedResult>;

  /**
   * Get the native Capacitor plugin version.
   *
   * @returns Promise that resolves with the plugin version
   * @since 1.0.0
   */
  getPluginVersion(): Promise<{ version: string }>;
}
