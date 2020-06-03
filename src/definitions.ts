declare module "@capacitor/core" {
  interface PluginRegistry {
    NativeBiometric: NativeBiometricPlugin;
  }
}

export interface AvailableOptions {
  has: boolean;
  touchId: boolean; //iOS
  faceId: boolean; //iOS
  fingerprint: boolean; //Android
  faceAuth: boolean; //Android
  irisAuth: boolean; //Android
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
