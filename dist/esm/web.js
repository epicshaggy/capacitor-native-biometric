import { WebPlugin } from "@capacitor/core";
export class NativeBiometricWeb extends WebPlugin {
    constructor() {
        super();
    }
    isAvailable() {
        throw new Error("Method not implemented.");
    }
    verifyIdentity(_options) {
        throw new Error("Method not implemented.");
    }
    getCredentials(_options) {
        throw new Error("Method not implemented.");
    }
    setCredentials(_options) {
        throw new Error("Method not implemented.");
    }
    deleteCredentials(_options) {
        throw new Error("Method not implemented.");
    }
}
//# sourceMappingURL=web.js.map