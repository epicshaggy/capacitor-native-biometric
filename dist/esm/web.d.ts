import { WebPlugin } from "@capacitor/core";
import { NativeBiometricPlugin, AvailableOptions } from "./definitions";
export declare class NativeBiometricWeb extends WebPlugin implements NativeBiometricPlugin {
    constructor();
    isAvailable(): Promise<AvailableOptions>;
    verify(): Promise<any>;
}
declare const NativeBiometric: NativeBiometricWeb;
export { NativeBiometric };
