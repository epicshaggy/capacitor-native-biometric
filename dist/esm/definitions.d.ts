declare module "@capacitor/core" {
    interface PluginRegistry {
        NativeBiometric: NativeBiometricPlugin;
    }
}
export interface AvailableOptions {
    has: boolean;
    touchId: boolean;
    faceId: boolean;
}
export interface NativeBiometricPlugin {
    isAvailable(): Promise<AvailableOptions>;
    verify(): Promise<any>;
}
