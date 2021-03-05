import { WebPlugin } from "@capacitor/core";
import {
  NativeBiometricPlugin,
  AvailableResult,
  BiometricOptions,
  GetCredentialOptions,
  SetCredentialOptions,
  DeleteCredentialOptions,
} from "./definitions";

export class NativeBiometricWeb
  extends WebPlugin
  implements NativeBiometricPlugin {
  constructor() {
    super({
      name: "NativeBiometric",
      platforms: ["web"],
    });
  }
  isAvailable(): Promise<AvailableResult> {
    throw new Error("Method not implemented.");
  }

  verifyIdentity(options?: BiometricOptions): Promise<any> {
    throw new Error("Method not implemented.");
  }
  getCredentials(
    options: GetCredentialOptions
  ): Promise<import("./definitions").Credentials> {
    throw new Error("Method not implemented.");
  }
  setCredentials(options: SetCredentialOptions): Promise<any> {
    throw new Error("Method not implemented.");
  }
  deleteCredentials(options: DeleteCredentialOptions): Promise<any> {
    throw new Error("Method not implemented.");
  }
}

const NativeBiometric = new NativeBiometricWeb();

export { NativeBiometric };

import { registerWebPlugin } from "@capacitor/core";
registerWebPlugin(NativeBiometric);
