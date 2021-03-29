import { registerPlugin } from '@capacitor/core';

import type { NativeBiometricPlugin } from './definitions';

const NativeBiometric = registerPlugin<NativeBiometricPlugin>('Storage', {
  web: () => import('./web').then(m => new m.NativeBiometricWeb()),
});

export * from './definitions';
export { NativeBiometric };
