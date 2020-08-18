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
        console.log(options);
        throw new Error("Method not implemented.");
    }
    getCredentials(options) {
        console.log(options);
        throw new Error("Method not implemented.");
    }
    setCredentials(options) {
        console.log(options);
        throw new Error("Method not implemented.");
    }
    deleteCredentials(options) {
        console.log(options);
        throw new Error("Method not implemented.");
    }
}
const NativeBiometric = new NativeBiometricWeb();
export { NativeBiometric };
import { registerWebPlugin } from "@capacitor/core";
registerWebPlugin(NativeBiometric);
//# sourceMappingURL=web.js.map