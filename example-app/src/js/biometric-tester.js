import { CapacitorUpdater } from '@capgo/capacitor-updater';
import { Capacitor } from '@capacitor/core';
import {
  NativeBiometric,
  AuthenticationStrength,
  BiometryType,
  AccessControl,
} from '@capgo/capacitor-native-biometric';
import { SplashScreen } from '@capacitor/splash-screen';

// Simple function-based approach - no Web Components
function createBiometricTester() {
  // Create main container
  const container = document.createElement('div');
  container.style.cssText = `
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
    padding: 20px;
    max-width: 800px;
    margin: 0 auto;
  `;

  // Create the HTML structure
  container.innerHTML = `
    <style>
      .section {
        margin-bottom: 30px;
        padding: 20px;
        border: 1px solid #ddd;
        border-radius: 8px;
        background: #f9f9f9;
      }
      .section h2 {
        margin-top: 0;
        color: #333;
      }
      .button {
        display: inline-block;
        padding: 10px 20px;
        margin: 5px;
        background-color: #007bff;
        color: #fff;
        border: none;
        border-radius: 4px;
        cursor: pointer;
        font-size: 14px;
      }
      .button:hover {
        background-color: #0056b3;
      }
      .button:disabled {
        background-color: #ccc;
        cursor: not-allowed;
      }
      .error {
        color: #dc3545;
        background: #f8d7da;
        padding: 10px;
        border-radius: 4px;
        margin: 10px 0;
      }
      .success {
        color: #155724;
        background: #d4edda;
        padding: 10px;
        border-radius: 4px;
        margin: 10px 0;
      }
      .info {
        color: #856404;
        background: #fff3cd;
        padding: 10px;
        border-radius: 4px;
        margin: 10px 0;
      }
      .form-group {
        margin-bottom: 15px;
      }
      .form-group label {
        display: block;
        margin-bottom: 5px;
        font-weight: bold;
      }
      .form-group input {
        width: 100%;
        padding: 8px;
        border: 1px solid #ddd;
        border-radius: 4px;
        box-sizing: border-box;
      }
      .credentials-display {
        background: #e9ecef;
        padding: 10px;
        border-radius: 4px;
        margin: 10px 0;
        font-family: monospace;
        max-height: 300px;
        overflow-y: auto;
      }
      .status {
        padding: 10px;
        margin: 10px 0;
        border-radius: 4px;
        font-weight: bold;
      }
      .status.available {
        background: #d4edda;
        color: #155724;
      }
      .status.unavailable {
        background: #f8d7da;
        color: #721c24;
      }
    </style>

    <h1>🔐 Native Biometric Test App</h1>

    <div class="section">
      <h2>Biometric Status</h2>
      <div id="biometric-status" class="status">Checking...</div>
      <div class="form-group" style="margin-bottom: 10px;">
        <label style="display: flex; align-items: center; cursor: pointer;">
          <input type="checkbox" id="use-fallback-check" style="width: auto; margin-right: 8px;">
          Use Fallback (passcode/PIN)
        </label>
      </div>
      <button class="button" id="check-availability">Check Availability</button>
      <div id="biometric-info" class="info" style="display: none;"></div>
    </div>

    <div class="section">
      <h2>Authentication Test</h2>
      <button class="button" id="verify-identity">Verify Identity (Biometric)</button>
      <button class="button" id="verify-identity-fallback">Verify Identity (with Fallback)</button>
      <div id="auth-result"></div>
    </div>

    <div class="section">
      <h2>Credentials Management</h2>
      <div class="form-group">
        <label for="server">Server:</label>
        <input type="text" id="server" value="www.example.com" placeholder="Enter server URL">
      </div>
      <div class="form-group">
        <label for="username">Username:</label>
        <input type="text" id="username" value="testuser" placeholder="Enter username">
      </div>
      <div class="form-group">
        <label for="password">Password:</label>
        <input type="password" id="password" value="testpass123" placeholder="Enter password">
      </div>

      <button class="button" id="set-credentials">Set Credentials</button>
      <button class="button" id="get-credentials">Get Credentials</button>
      <button class="button" id="check-credentials-saved">Check If Credentials Saved</button>
      <button class="button" id="delete-credentials">Delete Credentials</button>
      <div id="credentials-result"></div>
    </div>

    <div class="section">
      <h2>Secure Credentials (Biometric-Protected)</h2>
      <div class="info">
        Credentials stored with hardware-enforced biometric protection.<br>
        Set requires biometric on Android. Get always requires biometric on both platforms.
      </div>
      <div class="form-group">
        <label for="secure-server">Server:</label>
        <input type="text" id="secure-server" value="secure.example.com" placeholder="Enter server URL">
      </div>
      <div class="form-group">
        <label for="secure-username">Username:</label>
        <input type="text" id="secure-username" value="secureuser" placeholder="Enter username">
      </div>
      <div class="form-group">
        <label for="secure-password">Password:</label>
        <input type="password" id="secure-password" value="securepass123" placeholder="Enter password">
      </div>
      <button class="button" id="set-secure-credentials">Set Secure Credentials</button>
      <button class="button" id="get-secure-credentials">Get Secure Credentials</button>
      <div id="secure-credentials-result"></div>
    </div>

    <div class="section">
      <h2>Login Flow Demo (Issue Use Case)</h2>
      <div class="info">
        This section demonstrates the exact use case from the issue:<br>
        After login, check if credentials are already saved to decide whether to show the "save credentials" popup.
      </div>
      <div class="form-group">
        <label for="login-username">Login Username:</label>
        <input type="text" id="login-username" value="user@example.com" placeholder="Enter username">
      </div>
      <div class="form-group">
        <label for="login-password">Login Password:</label>
        <input type="password" id="login-password" value="mypassword123" placeholder="Enter password">
      </div>
      <button class="button" id="simulate-login">Simulate Login Flow</button>
      <div id="login-flow-result"></div>
    </div>

    <div class="section">
      <h2>Debug Console</h2>
      <div class="form-group">
        <label for="debug-input">Test Code:</label>
        <input type="text" id="debug-input" placeholder="Enter code to execute">
        <button class="button" id="execute-debug">Execute</button>
      </div>
      <div class="info">
        <strong>Quick Debug Commands:</strong><br>
        • Set credentials: <code>NativeBiometric.setCredentials({username: 'test@example.com', password: 'password123', server: 'example.com'})</code><br>
        • Check if saved: <code>NativeBiometric.isCredentialsSaved({server: 'example.com'})</code><br>
        • Check availability: <code>NativeBiometric.isAvailable()</code><br>
        • Get credentials: <code>NativeBiometric.getCredentials({server: 'example.com'})</code>
      </div>
      <div id="debug-output" class="credentials-display"></div>
    </div>

    <div class="section">
      <h2>Console Logs</h2>
      <div id="console-logs" class="credentials-display"></div>
    </div>
  `;

  // Add event listeners
  const elements = {
    checkAvailability: container.querySelector('#check-availability'),
    useFallbackCheck: container.querySelector('#use-fallback-check'),
    verifyIdentity: container.querySelector('#verify-identity'),
    verifyIdentityFallback: container.querySelector('#verify-identity-fallback'),
    setCredentials: container.querySelector('#set-credentials'),
    getCredentials: container.querySelector('#get-credentials'),
    checkCredentialsSaved: container.querySelector('#check-credentials-saved'),
    deleteCredentials: container.querySelector('#delete-credentials'),
    simulateLogin: container.querySelector('#simulate-login'),
    loginUsername: container.querySelector('#login-username'),
    loginPassword: container.querySelector('#login-password'),
    loginFlowResult: container.querySelector('#login-flow-result'),
    executeDebug: container.querySelector('#execute-debug'),
    debugInput: container.querySelector('#debug-input'),
    biometricStatus: container.querySelector('#biometric-status'),
    biometricInfo: container.querySelector('#biometric-info'),
    authResult: container.querySelector('#auth-result'),
    credentialsResult: container.querySelector('#credentials-result'),
    debugOutput: container.querySelector('#debug-output'),
    consoleLogs: container.querySelector('#console-logs'),
    server: container.querySelector('#server'),
    username: container.querySelector('#username'),
    password: container.querySelector('#password'),
    setSecureCredentials: container.querySelector('#set-secure-credentials'),
    getSecureCredentials: container.querySelector('#get-secure-credentials'),
    secureCredentialsResult: container.querySelector('#secure-credentials-result'),
    secureServer: container.querySelector('#secure-server'),
    secureUsername: container.querySelector('#secure-username'),
    securePassword: container.querySelector('#secure-password'),
  };

  // Override console.log to capture logs
  const originalLog = console.log;
  const originalError = console.error;
  const originalWarn = console.warn;

  console.log = (...args) => {
    originalLog.apply(console, args);
    addConsoleLog('LOG', args);
  };

  console.error = (...args) => {
    originalError.apply(console, args);
    addConsoleLog('ERROR', args);
  };

  console.warn = (...args) => {
    originalWarn.apply(console, args);
    addConsoleLog('WARN', args);
  };

  function addConsoleLog(type, args) {
    const timestamp = new Date().toLocaleTimeString();
    const message = args
      .map((arg) => (typeof arg === 'object' ? JSON.stringify(arg, null, 2) : String(arg)))
      .join(' ');

    elements.consoleLogs.innerHTML += `<div>[${timestamp}] ${type}: ${message}</div>`;
    elements.consoleLogs.scrollTop = elements.consoleLogs.scrollHeight;
  }

  // Event listeners
  elements.checkAvailability.addEventListener('click', checkAvailability);
  elements.verifyIdentity.addEventListener('click', () => verifyIdentity(false));
  elements.verifyIdentityFallback.addEventListener('click', () => verifyIdentity(true));
  elements.setCredentials.addEventListener('click', setCredentials);
  elements.getCredentials.addEventListener('click', getCredentials);
  elements.checkCredentialsSaved.addEventListener('click', checkCredentialsSaved);
  elements.deleteCredentials.addEventListener('click', deleteCredentials);
  elements.simulateLogin.addEventListener('click', simulateLoginFlow);
  elements.setSecureCredentials.addEventListener('click', setSecureCredentials);
  elements.getSecureCredentials.addEventListener('click', getSecureCredentials);
  elements.executeDebug.addEventListener('click', executeDebug);


  function getBiometryTypeName(type) {
    switch (type) {
      case BiometryType.TOUCH_ID:
        return 'Touch ID';
      case BiometryType.FACE_ID:
        return 'Face ID';
      case BiometryType.FINGERPRINT:
        return 'Fingerprint';
      case BiometryType.FACE_AUTHENTICATION:
        return 'Face authentication';
      case BiometryType.IRIS_AUTHENTICATION:
        return 'Iris authentication';
      case BiometryType.MULTIPLE:
        return 'Multiple biometry types';
      case BiometryType.DEVICE_CREDENTIAL:
        return 'Device credential (PIN/pattern/password)';
      case BiometryType.NONE:
      default:
        return 'None';
    }
  }

  // Functions
  async function checkAvailability() {
    try {
      const useFallback = elements.useFallbackCheck.checked;
      addConsoleLog('INFO', [`Checking biometric availability (useFallback: ${useFallback})...`]);
      const result = await NativeBiometric.isAvailable({ useFallback });
      addConsoleLog('INFO', ['Availability result:', result]);

      const biometryName = getBiometryTypeName(result.biometryType);
      const strengthName = getAuthenticationStrengthName(result.authenticationStrength);

      if (result.isAvailable) {
        elements.biometricStatus.textContent = `✅ Biometrics Available (${biometryName}, ${strengthName})`;
        elements.biometricStatus.className = 'status available';
      } else {
        elements.biometricStatus.textContent = `❌ Biometrics Not Available (${biometryName}, ${strengthName})`;
        elements.biometricStatus.className = 'status unavailable';
      }

      elements.biometricInfo.style.display = 'block';
      elements.biometricInfo.innerHTML = `
        <strong>Biometry type:</strong> ${biometryName} (${result.biometryType})<br>
        <strong>Authentication strength:</strong> ${strengthName} (${result.authenticationStrength})<br>
        <strong>Strong biometry available:</strong> ${result.strongBiometryIsAvailable ? 'Yes' : 'No'}<br>
        <strong>Device secure:</strong> ${result.deviceIsSecure ? 'Yes' : 'No'}${result.errorCode !== undefined ? `<br><strong>Error code:</strong> ${result.errorCode}` : ''}
      `;
    } catch (error) {
      addConsoleLog('ERROR', ['Availability check failed:', error]);
      elements.biometricStatus.textContent = '❌ Check Failed';
      elements.biometricStatus.className = 'status unavailable';
    }
  }

  async function verifyIdentity(useFallback) {
    try {
      addConsoleLog('INFO', [`Verifying identity (fallback: ${useFallback})...`]);
      await NativeBiometric.verifyIdentity({
        reason: 'Test biometric authentication',
        useFallback: useFallback,
      });
      addConsoleLog('SUCCESS', ['✅ Identity verified successfully!']);
      showResult(elements.authResult, '✅ Identity verified successfully!', 'success');
    } catch (error) {
      addConsoleLog('ERROR', ['Identity verification failed:', error]);
      showResult(elements.authResult, `❌ Verification failed: ${error.message || error}`, 'error');
    }
  }

  async function setCredentials() {
    const server = elements.server.value;
    const username = elements.username.value;
    const password = elements.password.value;

    if (!server || !username || !password) {
      showResult(elements.credentialsResult, '❌ Please fill in all fields', 'error');
      return;
    }

    try {
      addConsoleLog('INFO', [`Setting credentials for server: ${server}, username: ${username}`]);
      await NativeBiometric.setCredentials({
        username: username,
        password: password,
        server: server,
      });
      addConsoleLog('SUCCESS', ['✅ Credentials saved successfully!']);
      showResult(elements.credentialsResult, '✅ Credentials saved successfully!', 'success');
    } catch (error) {
      addConsoleLog('ERROR', ['Set credentials failed:', error]);
      showResult(elements.credentialsResult, `❌ Save failed: ${error.message || error}`, 'error');
    }
  }

  async function getCredentials() {
    const server = elements.server.value;

    if (!server) {
      showResult(elements.credentialsResult, '❌ Please enter server', 'error');
      return;
    }

    try {
      addConsoleLog('INFO', [`Getting credentials for server: ${server}`]);
      const credentials = await NativeBiometric.getCredentials({
        server: server,
      });
      addConsoleLog('SUCCESS', ['✅ Credentials retrieved:', credentials]);
      showResult(
        elements.credentialsResult,
        `<strong>Retrieved Credentials:</strong><br>Username: ${credentials.username}<br>Password: ${credentials.password}`,
        'success',
      );
    } catch (error) {
      addConsoleLog('ERROR', ['Get credentials failed:', error]);
      showResult(elements.credentialsResult, `❌ Get failed: ${error.message || error}`, 'error');
    }
  }

  async function deleteCredentials() {
    const server = elements.server.value;

    if (!server) {
      showResult(elements.credentialsResult, '❌ Please enter server', 'error');
      return;
    }

    try {
      addConsoleLog('INFO', [`Deleting credentials for server: ${server}`]);
      await NativeBiometric.deleteCredentials({
        server: server,
      });
      addConsoleLog('SUCCESS', ['✅ Credentials deleted successfully!']);
      showResult(elements.credentialsResult, '✅ Credentials deleted successfully!', 'success');
    } catch (error) {
      addConsoleLog('ERROR', ['Delete credentials failed:', error]);
      showResult(
        elements.credentialsResult,
        `❌ Delete failed: ${error.message || error}`,
        'error',
      );
    }
  }

  async function checkCredentialsSaved() {
    const server = elements.server.value;

    if (!server) {
      showResult(elements.credentialsResult, '❌ Please enter server', 'error');
      return;
    }

    try {
      addConsoleLog('INFO', [`Checking if credentials are saved for server: ${server}`]);
      const result = await NativeBiometric.isCredentialsSaved({
        server: server,
      });
      addConsoleLog('SUCCESS', ['✅ Check completed:', result]);

      if (result.isSaved) {
        showResult(
          elements.credentialsResult,
          `✅ Credentials ARE saved for server: ${server}`,
          'success',
        );
      } else {
        showResult(
          elements.credentialsResult,
          `ℹ️ No credentials saved for server: ${server}`,
          'info',
        );
      }
    } catch (error) {
      addConsoleLog('ERROR', ['Check credentials failed:', error]);
      showResult(elements.credentialsResult, `❌ Check failed: ${error.message || error}`, 'error');
    }
  }

  async function setSecureCredentials() {
    const server = elements.secureServer.value;
    const username = elements.secureUsername.value;
    const password = elements.securePassword.value;

    if (!server || !username || !password) {
      showResult(elements.secureCredentialsResult, '❌ Please fill in all fields', 'error');
      return;
    }

    try {
      addConsoleLog('INFO', [`Setting secure credentials for server: ${server}`]);
      await NativeBiometric.setCredentials({
        username,
        password,
        server,
        accessControl: AccessControl.BIOMETRY_ANY,
      });
      addConsoleLog('SUCCESS', ['✅ Secure credentials saved!']);
      showResult(
        elements.secureCredentialsResult,
        '✅ Secure credentials saved with biometric protection!',
        'success',
      );
    } catch (error) {
      addConsoleLog('ERROR', ['Set secure credentials failed:', error]);
      showResult(
        elements.secureCredentialsResult,
        `❌ Save failed: ${error.message || error}`,
        'error',
      );
    }
  }

  async function getSecureCredentials() {
    const server = elements.secureServer.value;

    if (!server) {
      showResult(elements.secureCredentialsResult, '❌ Please enter server', 'error');
      return;
    }

    try {
      addConsoleLog('INFO', [`Getting secure credentials for server: ${server}`]);
      const credentials = await NativeBiometric.getSecureCredentials({
        server,
        reason: 'Authenticate to access your credentials',
        title: 'Access Credentials',
      });
      addConsoleLog('SUCCESS', ['✅ Secure credentials retrieved:', credentials]);
      showResult(
        elements.secureCredentialsResult,
        `<strong>Retrieved Secure Credentials:</strong><br>Username: ${credentials.username}<br>Password: ${credentials.password}`,
        'success',
      );
    } catch (error) {
      addConsoleLog('ERROR', ['Get secure credentials failed:', error]);
      showResult(
        elements.secureCredentialsResult,
        `❌ Get failed: ${error.message || error}`,
        'error',
      );
    }
  }

  async function simulateLoginFlow() {
    const username = elements.loginUsername.value;
    const password = elements.loginPassword.value;
    const server = 'auth.example.com'; // Using a fixed server for this demo

    if (!username || !password) {
      showResult(elements.loginFlowResult, '❌ Please fill in username and password', 'error');
      return;
    }

    try {
      // Step 1: Simulate successful login
      addConsoleLog('INFO', ['📝 Step 1: Simulating login...']);
      showResult(elements.loginFlowResult, '📝 Step 1: Simulating login...', 'info');

      // Simulate API delay
      await new Promise((resolve) => setTimeout(resolve, 500));
      addConsoleLog('SUCCESS', ['✅ Login successful!']);

      // Step 2: Check if biometrics are available
      addConsoleLog('INFO', ['🔍 Step 2: Checking biometric availability...']);
      const result = await NativeBiometric.isAvailable({ useFallback: true });

      if (!result.isAvailable) {
        addConsoleLog('INFO', ['ℹ️ Biometrics not available, going to home page directly']);
        showResult(
          elements.loginFlowResult,
          '✅ Login successful! Biometrics not available, redirecting to home page...',
          'success',
        );
        return;
      }

      addConsoleLog('SUCCESS', ['✅ Biometrics available!']);

      // Step 3: Check if credentials are already saved (THE KEY FEATURE FROM THE ISSUE)
      addConsoleLog('INFO', ['🔍 Step 3: Checking if credentials are already saved...']);
      const checkCredentials = await NativeBiometric.isCredentialsSaved({ server });

      if (checkCredentials.isSaved) {
        // Credentials already saved - go to home page directly
        addConsoleLog('SUCCESS', ['✅ Credentials already saved! Going to home page...']);
        showResult(
          elements.loginFlowResult,
          `✅ Login successful!<br>
          ℹ️ Credentials already saved for ${server}<br>
          🏠 Redirecting to home page (no popup shown)...`,
          'success',
        );
      } else {
        // No credentials saved - show save credentials popup
        addConsoleLog('INFO', ['ℹ️ No credentials saved yet']);
        showResult(
          elements.loginFlowResult,
          `✅ Login successful!<br>
          ℹ️ No credentials saved for ${server}<br>
          💾 SHOWING SAVE CREDENTIALS POPUP (in real app, this would be a modal)<br>
          <br>
          <strong>Would you like to save credentials?</strong><br>
          <button class="button" id="save-creds-yes">Yes, Save</button>
          <button class="button" id="save-creds-no">No, Thanks</button>`,
          'info',
        );

        // Add event listeners for the popup buttons
        setTimeout(() => {
          const yesBtn = document.getElementById('save-creds-yes');
          const noBtn = document.getElementById('save-creds-no');

          if (yesBtn) {
            yesBtn.addEventListener('click', async () => {
              try {
                await NativeBiometric.setCredentials({
                  username: username,
                  password: password,
                  server: server,
                });
                addConsoleLog('SUCCESS', ['✅ Credentials saved!']);
                showResult(
                  elements.loginFlowResult,
                  '✅ Credentials saved successfully! Redirecting to home page...',
                  'success',
                );
              } catch (error) {
                addConsoleLog('ERROR', ['❌ Failed to save credentials:', error]);
                showResult(
                  elements.loginFlowResult,
                  `❌ Failed to save credentials: ${error.message || error}`,
                  'error',
                );
              }
            });
          }

          if (noBtn) {
            noBtn.addEventListener('click', () => {
              addConsoleLog('INFO', ['User declined to save credentials']);
              showResult(
                elements.loginFlowResult,
                'ℹ️ Credentials not saved. Redirecting to home page...',
                'info',
              );
            });
          }
        }, 100);
      }
    } catch (error) {
      addConsoleLog('ERROR', ['Login flow failed:', error]);
      showResult(
        elements.loginFlowResult,
        `❌ Login flow error: ${error.message || error}`,
        'error',
      );
    }
  }

  async function executeDebug() {
    const code = elements.debugInput.value.trim();

    if (!code) {
      showResult(elements.debugOutput, '❌ Please enter some code to execute', 'error');
      return;
    }

    try {
      addConsoleLog('INFO', [`Executing debug code: ${code}`]);

      // Simple eval for testing (be careful with this in production!)
      const result = await eval(`(async () => { ${code} })()`);

      addConsoleLog('SUCCESS', ['✅ Debug code executed successfully:', result]);
      showResult(elements.debugOutput, `✅ Result: ${JSON.stringify(result, null, 2)}`, 'success');
    } catch (error) {
      addConsoleLog('ERROR', ['Debug code execution failed:', error]);
      showResult(elements.debugOutput, `❌ Error: ${error.message || error}`, 'error');
    }
  }

  function showResult(element, message, type) {
    element.innerHTML = `<div class="${type}">${message}</div>`;
    element.style.display = 'block';

    // Auto-hide after 5 seconds for success messages
    if (type === 'success') {
      setTimeout(() => {
        element.style.display = 'none';
      }, 5000);
    }
  }

  function getAuthenticationStrengthName(strength) {
    const names = {
      [AuthenticationStrength.NONE]: 'None',
      [AuthenticationStrength.STRONG]: 'Strong',
      [AuthenticationStrength.WEAK]: 'Weak',
    };
    return names[strength] || 'Unknown';
  }

  // Initialize
  console.log('🔍 Biometric Test App initialized. Ready to test!');

  // Hide splash screen immediately
  SplashScreen.hide().catch((err) => {
    console.log('SplashScreen already hidden or not available:', err.message);
  });

  checkAvailability();

  return container;
}

// Create and append the tester
document.body.appendChild(createBiometricTester());

if (Capacitor.isNativePlatform()) {
  CapacitorUpdater.notifyAppReady().catch((error) => {
    console.error('Capgo notifyAppReady failed', error);
  });
}
