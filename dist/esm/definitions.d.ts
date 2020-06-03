declare module "@capacitor/core" {
    interface PluginRegistry {
        NativeBiometric: NativeBiometricPlugin;
    }
}
export interface AvailableOptions {
    has: boolean;
    touchId: boolean;
    faceId: boolean;
    fingerprint: boolean;
    faceAuth: boolean;
    irisAuth: boolean;
}
export interface BiometricOptions {
    reason?: string;
    title?: string;
    subtitle?: string;
    description?: string;
}
export interface NativeBiometricPlugin {
    isAvailable(): Promise<AvailableOptions>;
    verify(options?: BiometricOptions): Promise<any>;
}
