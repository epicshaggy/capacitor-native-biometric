import { registerPlugin } from '@capacitor/core';
const NativeBiometric = registerPlugin('Storage', {
    web: () => import('./web').then(m => new m.NativeBiometricWeb()),
});
export * from './definitions';
export { NativeBiometric };
//# sourceMappingURL=index.js.map