import { registerPlugin } from '@capacitor/core';

import type { NativeBiometricPlugin } from './definitions';

const NativeBiometric = registerPlugin<NativeBiometricPlugin>('NativeBiometric', {
  web: () => import('./web').then(m => new m.NativeBiometricWeb()),
});

export * from './definitions';
export { NativeBiometric };
