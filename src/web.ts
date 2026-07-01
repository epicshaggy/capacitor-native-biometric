import { WebPlugin } from '@capacitor/core';
import type { PluginListenerHandle } from '@capacitor/core';

import type {
  NativeBiometricPlugin,
  AvailableResult,
  BiometricOptions,
  GetCredentialOptions,
  GetSecureCredentialsOptions,
  SetCredentialOptions,
  DeleteCredentialOptions,
  IsCredentialsSavedOptions,
  IsCredentialsSavedResult,
  Credentials,
  BiometryChangeListener,
} from './definitions';
import { BiometryType, AuthenticationStrength } from './definitions';

export class NativeBiometricWeb extends WebPlugin implements NativeBiometricPlugin {
  /**
   * In-memory credential storage for browser development/testing.
   * Credentials are stored temporarily and cleared on page refresh.
   * This is NOT secure storage and should only be used for development purposes.
   */
  private credentialStore: Map<string, Credentials> = new Map();

  constructor() {
    super();
  }

  isAvailable(): Promise<AvailableResult> {
    // Web platform: return a dummy implementation for development/testing
    // Using TOUCH_ID as a generic placeholder for simulated biometric authentication
    return Promise.resolve({
      isAvailable: true,
      authenticationStrength: AuthenticationStrength.STRONG,
      biometryType: BiometryType.TOUCH_ID,
      deviceIsSecure: true,
      strongBiometryIsAvailable: true,
    });
  }

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  async addListener(_eventName: 'biometryChange', _listener: BiometryChangeListener): Promise<PluginListenerHandle> {
    // Web platform: no-op, but return a valid handle
    return {
      remove: async () => {
        // Nothing to remove on web
      },
    };
  }

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  verifyIdentity(_options?: BiometricOptions): Promise<void> {
    console.log('verifyIdentity (dummy implementation)');
    // Dummy implementation: always succeeds for browser testing
    return Promise.resolve();
  }

  getCredentials(_options: GetCredentialOptions): Promise<Credentials> {
    console.log('getCredentials (dummy implementation)', { server: _options.server });
    const credentials = this.credentialStore.get(_options.server);
    if (!credentials) {
      throw new Error('No credentials found for the specified server');
    }
    return Promise.resolve(credentials);
  }

  getSecureCredentials(_options: GetSecureCredentialsOptions): Promise<Credentials> {
    console.log('getSecureCredentials (dummy implementation)', { server: _options.server });
    const credentials = this.credentialStore.get(_options.server);
    if (!credentials) {
      throw new Error('No credentials found for the specified server');
    }
    return Promise.resolve(credentials);
  }

  setCredentials(_options: SetCredentialOptions): Promise<void> {
    console.log('setCredentials (dummy implementation)', { server: _options.server });
    // Dummy implementation: store in memory
    this.credentialStore.set(_options.server, {
      username: _options.username,
      password: _options.password,
    });
    return Promise.resolve();
  }

  deleteCredentials(_options: DeleteCredentialOptions): Promise<void> {
    console.log('deleteCredentials (dummy implementation)', { server: _options.server });
    // Dummy implementation: remove from in-memory store
    this.credentialStore.delete(_options.server);
    return Promise.resolve();
  }

  isCredentialsSaved(_options: IsCredentialsSavedOptions): Promise<IsCredentialsSavedResult> {
    console.log('isCredentialsSaved (dummy implementation)', { server: _options.server });
    // Dummy implementation: check in-memory store
    return Promise.resolve({ isSaved: this.credentialStore.has(_options.server) });
  }

  async getPluginVersion(): Promise<{ version: string }> {
    return { version: 'web' };
  }
}
