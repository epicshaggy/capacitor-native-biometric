import { WebPlugin } from "@capacitor/core";
import { NativeBiometricPlugin, AvailableResult, BiometricOptions, GetCredentialOptions, SetCredentialOptions, DeleteCredentialOptions } from "./definitions";
export declare class NativeBiometricWeb extends WebPlugin implements NativeBiometricPlugin {
    constructor();
    isAvailable(): Promise<AvailableResult>;
    verifyIdentity(options?: BiometricOptions): Promise<any>;
    getCredentials(options: GetCredentialOptions): Promise<import("./definitions").Credentials>;
    setCredentials(options: SetCredentialOptions): Promise<any>;
    deleteCredentials(options: DeleteCredentialOptions): Promise<any>;
}
declare const NativeBiometric: NativeBiometricWeb;
export { NativeBiometric };
