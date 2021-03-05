import { WebPlugin } from "@capacitor/core";
export class NativeBiometricWeb extends WebPlugin {
    constructor() {
        super({
            name: "NativeBiometric",
            platforms: ["web"],
        });
    }
    isAvailable() {
        throw new Error("Method not implemented.");
    }
    verifyIdentity(options) {
        throw new Error("Method not implemented.");
    }
    getCredentials(options) {
        throw new Error("Method not implemented.");
    }
    setCredentials(options) {
        throw new Error("Method not implemented.");
    }
    deleteCredentials(options) {
        throw new Error("Method not implemented.");
    }
}
const NativeBiometric = new NativeBiometricWeb();
export { NativeBiometric };
import { registerWebPlugin } from "@capacitor/core";
registerWebPlugin(NativeBiometric);
//# sourceMappingURL=web.js.map