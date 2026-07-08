// Test script to reproduce the exact issue reported by the user
import { NativeBiometric } from '@capgo/capacitor-native-biometric';

// This replicates the exact user code pattern
async function setCredentials(email, password, server) {
  try {
    console.log(`Attempting to save credentials for ${email} on ${server}`);
    await NativeBiometric.setCredentials({
      username: email,
      password: password,
      server: server,
    });
    console.log('✅ Credentials saved successfully!');
    return true;
  } catch (e) {
    console.error('❌ Error saving credentials:', e);
    console.error('Error message:', e.message);
    console.error('Error code:', e.code);
    console.error('Full error:', e);
    return false;
  }
}

// Test the exact scenario from the user's report
async function testUserIssue() {
  console.log('🔍 Testing the exact user issue...');

  // First check if biometrics are available
  try {
    const availability = await NativeBiometric.isAvailable();
    console.log('Biometric availability:', availability);

    if (!availability.isAvailable) {
      console.log('⚠️ Biometrics not available. Error code:', availability.errorCode);
      return;
    }
  } catch (error) {
    console.error('❌ Failed to check biometric availability:', error);
    return;
  }

  // Test the exact user pattern
  const testCases = [
    {
      email: 'test@example.com',
      password: 'password123',
      server: 'example.com'
    },
    {
      email: 'user@company.com',
      password: 'securePassword456',
      server: 'https://api.company.com'
    },
    {
      email: 'admin@domain.org',
      password: 'mySecretPass789',
      server: 'www.domain.org'
    }
  ];

  for (const testCase of testCases) {
    console.log(`\n--- Testing: ${testCase.email} @ ${testCase.server} ---`);
    await setCredentials(testCase.email, testCase.password, testCase.server);

    // Wait a bit between tests
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
}

// Test all biometric methods
async function comprehensiveTest() {
  console.log('🚀 Starting comprehensive biometric test...\n');

  try {
    // 1. Check availability
    console.log('1. Checking biometric availability...');
    const available = await NativeBiometric.isAvailable();
    console.log('   Result:', available);
    console.log('');

    if (!available.isAvailable) {
      console.log('❌ Biometrics not available. Cannot proceed with other tests.');
      return;
    }

    // 2. Test authentication
    console.log('2. Testing biometric authentication...');
    try {
      await NativeBiometric.verifyIdentity({
        reason: 'Test authentication for debugging'
      });
      console.log('   ✅ Authentication successful');
    } catch (authError) {
      console.log('   ❌ Authentication failed:', authError.message);
    }
    console.log('');

    // 3. Test credential operations
    const server = 'test-server.com';
    const credentials = {
      username: 'testuser',
      password: 'testpass123'
    };

    console.log(`3. Testing credential operations on ${server}...`);

    // Set credentials
    try {
      await NativeBiometric.setCredentials({
        username: credentials.username,
        password: credentials.password,
        server: server
      });
      console.log('   ✅ Set credentials successful');
    } catch (setError) {
      console.log('   ❌ Set credentials failed:', setError.message);
    }

    // Get credentials
    try {
      const retrieved = await NativeBiometric.getCredentials({ server });
      console.log('   ✅ Get credentials successful:', retrieved);
    } catch (getError) {
      console.log('   ❌ Get credentials failed:', getError.message);
    }

    // Delete credentials
    try {
      await NativeBiometric.deleteCredentials({ server });
      console.log('   ✅ Delete credentials successful');
    } catch (delError) {
      console.log('   ❌ Delete credentials failed:', delError.message);
    }

    console.log('\n🎉 Comprehensive test completed!');

  } catch (error) {
    console.error('💥 Comprehensive test failed:', error);
  }
}

// Export for use in browser console
window.testBiometricIssue = testUserIssue;
window.testComprehensive = comprehensiveTest;
window.setTestCredentials = setCredentials;

console.log('📋 Test functions available in console:');
console.log('   - testBiometricIssue() - Test the exact user issue');
console.log('   - testComprehensive() - Run all biometric tests');
console.log('   - setTestCredentials(email, password, server) - Test specific credentials');
console.log('');
console.log('💡 Run testBiometricIssue() to reproduce the exact error!');
