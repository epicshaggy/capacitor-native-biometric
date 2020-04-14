import { WebPlugin } from "@capacitor/core";
import { NativeBiometricPlugin, AvailableOptions } from "./definitions";

export class NativeBiometricWeb extends WebPlugin
  implements NativeBiometricPlugin {
  constructor() {
    super({
      name: "NativeBiometric",
      platforms: ["web"],
    });
  }

  async isAvailable(): Promise<AvailableOptions> {
    return new Promise(() => {});
  }

  async verify(): Promise<any> {
    return new Promise(() => {});
  }
}

const NativeBiometric = new NativeBiometricWeb();

export { NativeBiometric };

import { registerWebPlugin } from "@capacitor/core";
registerWebPlugin(NativeBiometric);
