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
}

export interface Credentials {
  username: string;
  password: string;
}

export interface IsAvailableOptions {
  /**
   * Specifies if should fallback to passcode authentication if biometric authentication is not available.
   */
  useFallback: boolean;
}

export interface AvailableResult {
  isAvailable: boolean;
  biometryType: BiometryType;
  errorCode?: number;
}

export interface BiometricOptions {
  reason?: string;
  title?: string;
  subtitle?: string;
  description?: string;
  negativeButtonText?: string;
  /**
   * Specifies if should fallback to passcode authentication if biometric authentication fails.
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
}

export interface GetCredentialOptions {
  server: string;
}

export interface SetCredentialOptions {
  username: string;
  password: string;
  server: string;
}

export interface DeleteCredentialOptions {
  server: string;
}

/**
 * Keep this in sync with BiometricAuthError in README.md
 * Update whenever `convertToPluginErrorCode` functions are modified
 */
export enum BiometricAuthError {
  UNKNOWN_ERROR = 0,
  BIOMETRICS_UNAVAILABLE = 1,
  USER_LOCKOUT = 2,
  BIOMETRICS_NOT_ENROLLED = 3,
  USER_TEMPORARY_LOCKOUT = 4,
  AUTHENTICATION_FAILED = 10,
  APP_CANCEL = 11,
  INVALID_CONTEXT = 12,
  NOT_INTERACTIVE = 13,
  PASSCODE_NOT_SET = 14,
  SYSTEM_CANCEL = 15,
  USER_CANCEL = 16,
  USER_FALLBACK = 17,
}

export interface NativeBiometricPlugin {
  isAvailable(options?: IsAvailableOptions): Promise<AvailableResult>;

  verifyIdentity(options?: BiometricOptions): Promise<void>;

  getCredentials(options: GetCredentialOptions): Promise<Credentials>;

  setCredentials(options: SetCredentialOptions): Promise<void>;

  deleteCredentials(options: DeleteCredentialOptions): Promise<void>;
}
