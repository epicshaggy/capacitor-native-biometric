export declare enum BiometryType {
    NONE = 0,
    TOUCH_ID = 1,
    FACE_ID = 2,
    FINGERPRINT = 3,
    FACE_AUTHENTICATION = 4,
    IRIS_AUTHENTICATION = 5
}
export interface Credentials {
    username: string;
    password: string;
}
export interface IsAvailableOptions {
    useFallback: boolean;
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
    useFallback?: boolean;
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
    isAvailable(options?: IsAvailableOptions): Promise<AvailableResult>;
    verifyIdentity(options?: BiometricOptions): Promise<any>;
    getCredentials(options: GetCredentialOptions): Promise<Credentials>;
    setCredentials(options: SetCredentialOptions): Promise<any>;
    deleteCredentials(options: DeleteCredentialOptions): Promise<any>;
}
