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

  verifyIdentity(_options?: BiometricOptions): Promise<any> {
    throw new Error("Method not implemented.");
  }
  getCredentials(
    _options: GetCredentialOptions
  ): Promise<import("./definitions").Credentials> {
    throw new Error("Method not implemented.");
  }
  setCredentials(_options: SetCredentialOptions): Promise<any> {
    throw new Error("Method not implemented.");
  }
  deleteCredentials(_options: DeleteCredentialOptions): Promise<any> {
    throw new Error("Method not implemented.");
  }
}
