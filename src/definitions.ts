export enum BiometryType {
  NONE,
  TOUCH_ID,
  FACE_ID,
  FINGERPRINT,
  FACE_AUTHENTICATION,
  IRIS_AUTHENTICATION,
}

export interface Credentials {
  username: string;
  password: string;
}

export interface AvailableResult {
  isAvailable: boolean;
  biometryType: BiometryType;
}

export interface BiometricOptions {
  reason?: string;
  title?: string;
  subtitle?: string;
  description?: string;
  negativeButtonText?: string;
  retries?: number;
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

export interface NativeBiometricPlugin {
  isAvailable(): Promise<AvailableResult>;

  verifyIdentity(options?: BiometricOptions): Promise<any>;

  getCredentials(options: GetCredentialOptions): Promise<Credentials>;

  setCredentials(options: SetCredentialOptions): Promise<any>;

  deleteCredentials(options: DeleteCredentialOptions): Promise<any>;
}
