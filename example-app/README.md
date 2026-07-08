# Native Biometric Test App

This example app is designed to test and reproduce the KeychainError issue with the Capacitor Native Biometric plugin on iOS.

## Issue Description

The user is experiencing this error when calling `setCredentials`:

```
errorMessage: "The operation couldn't be completed. (CapgoCapacitorNativeBiometric.NativeBiometric.KeychainError error 0.)"
```

## Setup

1. **Install dependencies:**
   ```bash
   npm install
   ```

2. **Add platforms:**
   ```bash
   npx cap add ios
   npx cap add android
   ```

3. **Build and run:**
   ```bash
   # For web (development)
   npm start

   # For iOS (with splash screen fix)
   npm run rebuild:ios
   npx cap open ios

   # For Android
   npm run rebuild:android
   npx cap open android
   ```

## Testing the Issue

### 🚀 **Simple Test (Recommended)**

Open `simple-test.html` in your browser or on your device. This is a straightforward HTML page that exactly replicates your issue:

```html
<!-- Your exact code pattern -->
await NativeBiometric.setCredentials({
  username: email,
  password: password,
  server: server,
})
```

**Features:**
- ✅ Exact reproduction of your error
- ✅ Simple form inputs (email, password, server)
- ✅ Real-time console logging
- ✅ One-click testing

### 🧪 **Full Test App**

The main app (`index.html`) provides comprehensive testing:

1. **Biometric Status Check** - See if biometrics are available
2. **Authentication Test** - Test Face ID/Touch ID
3. **Credentials Management** - Test the problematic `setCredentials` method
4. **Debug Console** - Execute custom code snippets
5. **Console Logs** - See detailed error information

## Your Exact Code

The app includes your exact function:

```javascript
async setCredentials(email, password, server) {
  try {
    await NativeBiometric.setCredentials({
      username: email,
      password: password,
      server: server,
    });
  } catch (e) {
    this.saveErrorLog(e);
  }
}
```

## How to Reproduce

1. **iOS Testing:**
   ```bash
   npx cap build ios
   npx cap open ios
   ```
   - Open the app on your iOS device/simulator
   - Click "Set Credentials"
   - Check the console logs for the error

2. **Web Testing:**
   ```bash
   npm start
   ```
   - Open in browser
   - Click "Set Credentials"
   - Check browser console

3. **Simple HTML Test:**
   - Open `simple-test.html` directly
   - Fill in the form
   - Click "Test setCredentials (Your Issue)"

## Expected vs Actual Behavior

- **Expected**: Credentials should save successfully
- **Actual**: Getting `KeychainError error 0`

## Console Logs

All console output is captured and displayed in the app. Look for:
- Plugin method calls and responses
- Error details and stack traces
- iOS Keychain status codes
- Detailed error information

## Test Data

Pre-filled test data:
- **Email**: `test@example.com`
- **Password**: `password123`
- **Server**: `example.com`

## Troubleshooting

1. **iOS Issues:**
   - Ensure device has passcode enabled
   - Check Face ID/Touch ID settings
   - Verify Keychain permissions in Info.plist
   - Look at console for specific error codes

2. **Common Issues:**
   - Server URL format issues
   - Device security restrictions
   - Keychain access permissions

3. **iOS Console Warnings (Safe to Ignore):**
   - `UIScene lifecycle will soon be required` - Future iOS requirement, doesn't affect functionality
   - `Could not create a sandbox extension` - Debug message, app still works
   - `Unable to hide query parameters from script` - WebView debug info, safe to ignore

4. **Splash Screen Issues:**
   - Run `npm run rebuild:ios` to apply configuration changes
   - Splash screen should auto-hide after 1 second
   - If it doesn't hide, check browser console for errors

## Files

- `simple-test.html` - Minimal test for your exact issue
- `index.html` - Full test app with all features
- `test-issue.js` - Node.js test script
- `js/biometric-tester.js` - Main app logic (simple JavaScript, no frameworks)

## Quick Test Commands

In the debug console, try:
```javascript
// Test your exact issue
NativeBiometric.setCredentials({
  username: 'test@example.com',
  password: 'password123',
  server: 'example.com'
})

// Check what's available
NativeBiometric.isAvailable()

// Try to get credentials
NativeBiometric.getCredentials({server: 'example.com'})
```
