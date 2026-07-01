package ee.forgr.biometric;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.StrongBoxUnavailableException;
import android.util.Base64;
import androidx.activity.result.ActivityResult;
import androidx.biometric.BiometricManager;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.ProviderException;
import java.security.SecureRandom;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Objects;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONException;

@CapacitorPlugin(name = "NativeBiometric")
public class NativeBiometric extends Plugin {

    private final String pluginVersion = "7.5.4";

    //protected final static int AUTH_CODE = 0102;

    private static final int NONE = 0;
    private static final int FINGERPRINT = 3;
    private static final int FACE_AUTHENTICATION = 4;
    private static final int IRIS_AUTHENTICATION = 5;
    private static final int MULTIPLE = 6;
    private static final int DEVICE_CREDENTIAL = 7;

    // AuthenticationStrength enum values
    private static final int AUTH_STRENGTH_NONE = 0;
    private static final int AUTH_STRENGTH_STRONG = 1;
    private static final int AUTH_STRENGTH_WEAK = 2;

    private KeyStore keyStore;
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String RSA_MODE = "RSA/ECB/PKCS1Padding";
    private static final String AES_MODE = "AES/ECB/PKCS7Padding";
    private static final int GCM_IV_LENGTH = 12;
    private static final String ENCRYPTED_KEY = "NativeBiometricKey";
    private static final String NATIVE_BIOMETRIC_SHARED_PREFERENCES = "NativeBiometricSharedPreferences";

    private SharedPreferences encryptedSharedPreferences;

    @Override
    protected void handleOnResume() {
        super.handleOnResume();
        // Notify listeners when app resumes from background
        JSObject result = checkBiometryAvailability(false);
        notifyListeners("biometryChange", result);
    }

    /**
     * Check biometry availability and return result as JSObject.
     * This is a helper method used by both isAvailable() and handleOnResume().
     */
    private JSObject checkBiometryAvailability(boolean useFallback) {
        JSObject ret = new JSObject();

        BiometricManager biometricManager = BiometricManager.from(getContext());

        // Check for strong biometrics first
        int strongAuthenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG;
        int strongResult = biometricManager.canAuthenticate(strongAuthenticators);
        boolean hasStrongBiometric = (strongResult == BiometricManager.BIOMETRIC_SUCCESS);

        // Check for weak biometrics
        int weakAuthenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK;
        int weakResult = biometricManager.canAuthenticate(weakAuthenticators);
        boolean hasWeakBiometric = (weakResult == BiometricManager.BIOMETRIC_SUCCESS);

        // Check if device has credentials (PIN/pattern/password)
        boolean deviceIsSecure = this.deviceHasCredentials();
        boolean fallbackAvailable = useFallback && deviceIsSecure;

        // Determine biometry type
        int biometryType = detectBiometryType(biometricManager);
        ret.put("biometryType", biometryType);

        // Device is secure if it has PIN/pattern/password
        ret.put("deviceIsSecure", deviceIsSecure);

        // Strong biometry is available only if strong biometric check passes
        ret.put("strongBiometryIsAvailable", hasStrongBiometric);

        // Determine authentication strength
        int authenticationStrength = AUTH_STRENGTH_NONE;
        boolean isAvailable = false;

        if (hasStrongBiometric) {
            authenticationStrength = AUTH_STRENGTH_STRONG;
            isAvailable = true;
        } else if (hasWeakBiometric) {
            // Weak-only biometrics (e.g. some face unlock) are reported for UX via
            // biometryType/authenticationStrength but do not enable verifyIdentity().
            authenticationStrength = AUTH_STRENGTH_WEAK;
        } else if (fallbackAvailable) {
            authenticationStrength = AUTH_STRENGTH_WEAK;
            isAvailable = true;
        }

        // Handle error codes when authentication is not available
        if (!isAvailable) {
            int biometricManagerErrorCode;
            if (strongResult != BiometricManager.BIOMETRIC_SUCCESS) {
                biometricManagerErrorCode = strongResult;
            } else if (weakResult != BiometricManager.BIOMETRIC_SUCCESS) {
                biometricManagerErrorCode = weakResult;
            } else {
                biometricManagerErrorCode = BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE;
            }
            int pluginErrorCode = convertBiometricManagerErrorToPluginError(biometricManagerErrorCode);
            ret.put("errorCode", pluginErrorCode);
        }

        ret.put("isAvailable", isAvailable);
        ret.put("authenticationStrength", authenticationStrength);
        return ret;
    }

    @PluginMethod
    public void isAvailable(PluginCall call) {
        boolean useFallback = Boolean.TRUE.equals(call.getBoolean("useFallback", false));
        JSObject result = checkBiometryAvailability(useFallback);
        call.resolve(result);
    }

    /**
     * Detect the primary biometry type available on the device.
     * Note: Android doesn't provide a direct API to query specific biometry types,
     * so we check for hardware features. This is informational only - always use
     * isAvailable for logic decisions as hardware presence doesn't guarantee availability.
     */
    private int detectBiometryType(BiometricManager biometricManager) {
        PackageManager pm = getContext().getPackageManager();

        boolean hasFingerprint = pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT);
        boolean hasFace = pm.hasSystemFeature(PackageManager.FEATURE_FACE);
        boolean hasIris = pm.hasSystemFeature(PackageManager.FEATURE_IRIS);

        int typeCount = 0;
        if (hasFingerprint) typeCount++;
        if (hasFace) typeCount++;
        if (hasIris) typeCount++;

        // Prefer FINGERPRINT when enrolled, even on devices advertising multiple biometric sensors.
        // This avoids returning MULTIPLE in common cases where only fingerprint is actually enabled.
        if (hasFingerprint && isFingerprintEnrolled()) {
            return FINGERPRINT;
        }

        if (typeCount > 1) {
            return MULTIPLE; // Multiple biometry types available
        } else if (hasFingerprint) {
            return FINGERPRINT;
        } else if (hasFace) {
            return FACE_AUTHENTICATION;
        } else if (hasIris) {
            return IRIS_AUTHENTICATION;
        }

        // If no biometric sensors are available but device has credentials (PIN/pattern/password)
        // return DEVICE_CREDENTIAL type
        if (this.deviceHasCredentials()) {
            return DEVICE_CREDENTIAL;
        }

        return NONE;
    }

    private boolean isFingerprintEnrolled() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }

        try {
            FingerprintManager fingerprintManager = getContext().getSystemService(FingerprintManager.class);
            return fingerprintManager != null && fingerprintManager.hasEnrolledFingerprints();
        } catch (SecurityException ignored) {
            return false;
        }
    }

    @PluginMethod
    public void verifyIdentity(final PluginCall call) throws JSONException {
        Intent intent = new Intent(getContext(), AuthActivity.class);

        intent.putExtra("title", call.getString("title", "Authenticate"));

        String subtitle = call.getString("subtitle");
        if (subtitle != null) {
            intent.putExtra("subtitle", subtitle);
        }

        String description = call.getString("description");
        if (description != null) {
            intent.putExtra("description", description);
        }

        String negativeButtonText = call.getString("negativeButtonText");
        if (negativeButtonText != null) {
            intent.putExtra("negativeButtonText", negativeButtonText);
        }

        Integer maxAttempts = call.getInt("maxAttempts");
        if (maxAttempts != null) {
            intent.putExtra("maxAttempts", maxAttempts);
        }

        // Pass allowed biometry types
        JSArray allowedTypes = call.getArray("allowedBiometryTypes");
        if (allowedTypes != null) {
            int[] types = new int[allowedTypes.length()];
            for (int i = 0; i < allowedTypes.length(); i++) {
                types[i] = (int) allowedTypes.toList().get(i);
            }
            intent.putExtra("allowedBiometryTypes", types);
        }

        // Note: useFallback parameter is ignored on Android (iOS-only feature)
        // Android's BiometricPrompt doesn't support fallback to device credentials when a negative button is present.
        // The API constraint: setNegativeButtonText() and DEVICE_CREDENTIAL authenticator are mutually exclusive.
        // Since we need the negative button for user cancellation, fallback cannot be supported on Android.

        startActivityForResult(call, intent, "verifyResult");
    }

    @PluginMethod
    public void setCredentials(final PluginCall call) {
        String username = call.getString("username", null);
        String password = call.getString("password", null);
        String KEY_ALIAS = call.getString("server", null);
        Integer accessControl = call.getInt("accessControl", 0);

        if (username == null || password == null || KEY_ALIAS == null) {
            call.reject("Missing properties");
            return;
        }

        if (accessControl != null && accessControl > 0) {
            Intent intent = new Intent(getContext(), AuthActivity.class);
            intent.putExtra("mode", "setSecureCredentials");
            intent.putExtra("server", KEY_ALIAS);
            intent.putExtra("username", username);
            intent.putExtra("password", password);
            intent.putExtra("accessControl", accessControl);
            intent.putExtra("title", "Protect Credentials");
            intent.putExtra("negativeButtonText", "Cancel");
            startActivityForResult(call, intent, "setSecureCredentialsResult");
        } else {
            try {
                SharedPreferences.Editor editor = getContext()
                    .getSharedPreferences(NATIVE_BIOMETRIC_SHARED_PREFERENCES, Context.MODE_PRIVATE)
                    .edit();
                editor.putString(KEY_ALIAS + "-username", encryptString(username, KEY_ALIAS));
                editor.putString(KEY_ALIAS + "-password", encryptString(password, KEY_ALIAS));
                editor.apply();
                call.resolve();
            } catch (GeneralSecurityException | IOException e) {
                call.reject("Failed to save credentials", e);
            }
        }
    }

    @PluginMethod
    public void getSecureCredentials(final PluginCall call) {
        String server = call.getString("server", null);
        if (server == null) {
            call.reject("No server name was provided");
            return;
        }

        SharedPreferences sharedPreferences = getContext().getSharedPreferences(NATIVE_BIOMETRIC_SHARED_PREFERENCES, Context.MODE_PRIVATE);
        String encryptedData = sharedPreferences.getString("secure_" + server, null);
        if (encryptedData == null) {
            call.reject("No protected credentials found", "21");
            return;
        }

        Intent intent = new Intent(getContext(), AuthActivity.class);
        intent.putExtra("mode", "getSecureCredentials");
        intent.putExtra("server", server);
        intent.putExtra("title", call.getString("title", "Authenticate"));

        String subtitle = call.getString("subtitle");
        if (subtitle != null) intent.putExtra("subtitle", subtitle);
        String description = call.getString("description");
        if (description != null) intent.putExtra("description", description);
        String negativeText = call.getString("negativeButtonText");
        if (negativeText != null) intent.putExtra("negativeButtonText", negativeText);

        startActivityForResult(call, intent, "getSecureCredentialsResult");
    }

    @PluginMethod
    public void getCredentials(final PluginCall call) {
        String KEY_ALIAS = call.getString("server", null);

        SharedPreferences sharedPreferences = getContext().getSharedPreferences(NATIVE_BIOMETRIC_SHARED_PREFERENCES, Context.MODE_PRIVATE);
        String username = sharedPreferences.getString(KEY_ALIAS + "-username", null);
        String password = sharedPreferences.getString(KEY_ALIAS + "-password", null);
        if (KEY_ALIAS != null) {
            if (username != null && password != null) {
                try {
                    JSObject jsObject = new JSObject();
                    jsObject.put("username", decryptString(username, KEY_ALIAS));
                    jsObject.put("password", decryptString(password, KEY_ALIAS));
                    call.resolve(jsObject);
                } catch (GeneralSecurityException | IOException e) {
                    // Can get here if not authenticated.
                    String errorMessage = "Failed to get credentials";
                    call.reject(errorMessage);
                }
            } else {
                call.reject("No credentials found");
            }
        } else {
            call.reject("No server name was provided");
        }
    }

    @ActivityCallback
    private void verifyResult(PluginCall call, ActivityResult result) {
        if (result.getResultCode() == Activity.RESULT_OK) {
            Intent data = result.getData();
            if (data != null && data.hasExtra("result")) {
                switch (Objects.requireNonNull(data.getStringExtra("result"))) {
                    case "success":
                        call.resolve();
                        break;
                    case "failed":
                    case "error":
                        call.reject(data.getStringExtra("errorDetails"), data.getStringExtra("errorCode"));
                        break;
                    default:
                        // Should not get to here unless AuthActivity starts returning different Activity Results.
                        call.reject("Something went wrong.");
                        break;
                }
            }
        } else {
            call.reject("Something went wrong.");
        }
    }

    @ActivityCallback
    private void setSecureCredentialsResult(PluginCall call, ActivityResult result) {
        if (result.getResultCode() == Activity.RESULT_OK) {
            Intent data = result.getData();
            if (data != null && "success".equals(data.getStringExtra("result"))) {
                call.resolve();
            } else {
                String errorCode = data != null ? data.getStringExtra("errorCode") : "0";
                String errorDetails = data != null ? data.getStringExtra("errorDetails") : "Failed to store credentials";
                call.reject(errorDetails, errorCode);
            }
        } else {
            call.reject("Failed to store credentials");
        }
    }

    @ActivityCallback
    private void getSecureCredentialsResult(PluginCall call, ActivityResult result) {
        if (result.getResultCode() == Activity.RESULT_OK) {
            Intent data = result.getData();
            if (data != null && "success".equals(data.getStringExtra("result"))) {
                JSObject jsObject = new JSObject();
                jsObject.put("username", data.getStringExtra("username"));
                jsObject.put("password", data.getStringExtra("password"));
                call.resolve(jsObject);
            } else {
                String errorCode = data != null ? data.getStringExtra("errorCode") : "0";
                String errorDetails = data != null ? data.getStringExtra("errorDetails") : "Authentication failed";
                call.reject(errorDetails, errorCode);
            }
        } else {
            call.reject("Authentication failed");
        }
    }

    @PluginMethod
    public void deleteCredentials(final PluginCall call) {
        String KEY_ALIAS = call.getString("server", null);

        if (KEY_ALIAS != null) {
            try {
                getKeyStore().deleteEntry(KEY_ALIAS);
                SharedPreferences.Editor editor = getContext()
                    .getSharedPreferences(NATIVE_BIOMETRIC_SHARED_PREFERENCES, Context.MODE_PRIVATE)
                    .edit();
                editor.remove(KEY_ALIAS + "-username");
                editor.remove(KEY_ALIAS + "-password");
                editor.remove("secure_" + KEY_ALIAS);
                editor.apply();

                try {
                    getKeyStore().deleteEntry("NativeBiometricSecure_" + KEY_ALIAS);
                } catch (KeyStoreException e) {
                    // Ignore — may not exist
                }

                call.resolve();
            } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
                call.reject("Failed to delete", e);
            }
        } else {
            call.reject("No server name was provided");
        }
    }

    @PluginMethod
    public void isCredentialsSaved(final PluginCall call) {
        String KEY_ALIAS = call.getString("server", null);

        if (KEY_ALIAS != null) {
            SharedPreferences sharedPreferences = getContext().getSharedPreferences(
                NATIVE_BIOMETRIC_SHARED_PREFERENCES,
                Context.MODE_PRIVATE
            );
            String username = sharedPreferences.getString(KEY_ALIAS + "-username", null);
            String password = sharedPreferences.getString(KEY_ALIAS + "-password", null);

            boolean hasUnprotected = username != null && password != null;
            boolean hasProtected = sharedPreferences.getString("secure_" + KEY_ALIAS, null) != null;

            JSObject ret = new JSObject();
            ret.put("isSaved", hasUnprotected || hasProtected);
            call.resolve(ret);
        } else {
            call.reject("No server name was provided");
        }
    }

    private String encryptString(String stringToEncrypt, String KEY_ALIAS) throws GeneralSecurityException, IOException {
        Cipher cipher;
        cipher = Cipher.getInstance(TRANSFORMATION);

        // Let the system generate the IV to comply with hardware-backed keystore requirements
        // Modern Android devices with StrongBox/TEE enforce RandomizedEncryption and reject caller-provided IVs
        cipher.init(Cipher.ENCRYPT_MODE, getKey(KEY_ALIAS));
        byte[] iv = cipher.getIV(); // Retrieve the system-generated IV
        if (iv == null || iv.length != GCM_IV_LENGTH) {
            throw new GeneralSecurityException(
                "Failed to generate valid IV: expected " + GCM_IV_LENGTH + " bytes, got " + (iv == null ? "null" : iv.length + " bytes")
            );
        }
        byte[] encryptedBytes = cipher.doFinal(stringToEncrypt.getBytes(StandardCharsets.UTF_8));

        // Prepend IV to the encrypted data
        byte[] combined = new byte[iv.length + encryptedBytes.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);

        return Base64.encodeToString(combined, Base64.DEFAULT);
    }

    private String decryptString(String stringToDecrypt, String KEY_ALIAS) throws GeneralSecurityException, IOException {
        byte[] combined = Base64.decode(stringToDecrypt, Base64.DEFAULT);

        // Try new format first (IV prepended to ciphertext)
        // New format: 12-byte IV + ciphertext (plaintext + 16-byte GCM auth tag)
        // We check for > GCM_IV_LENGTH to ensure there's at least some ciphertext beyond just the IV
        // The cipher's doFinal() will validate the auth tag and fail if data is malformed
        if (combined.length >= GCM_IV_LENGTH + 1) {
            try {
                // Extract IV from the beginning of the data
                byte[] iv = new byte[GCM_IV_LENGTH];
                byte[] encryptedData = new byte[combined.length - GCM_IV_LENGTH];
                System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
                System.arraycopy(combined, GCM_IV_LENGTH, encryptedData, 0, encryptedData.length);

                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.DECRYPT_MODE, getKey(KEY_ALIAS), new GCMParameterSpec(128, iv));
                byte[] decryptedData = cipher.doFinal(encryptedData);
                return new String(decryptedData, StandardCharsets.UTF_8);
            } catch (BadPaddingException e) {
                // Authentication tag verification failed (AEADBadTagException) or padding error
                // BadPaddingException is the parent class of AEADBadTagException
                // Likely means data was encrypted with legacy format - fall through to legacy decryption
            } catch (GeneralSecurityException e) {
                // Other security exceptions should not be masked - rethrow
                throw e;
            }
        }

        // Fallback to legacy format (FIXED_IV - all zeros)
        // This branch handles credentials encrypted with the old vulnerable method
        byte[] LEGACY_FIXED_IV = new byte[12]; // All zeros by default
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, getKey(KEY_ALIAS), new GCMParameterSpec(128, LEGACY_FIXED_IV));
        byte[] decryptedData = cipher.doFinal(combined);
        return new String(decryptedData, StandardCharsets.UTF_8);
    }

    @SuppressLint("NewAPI") // API level is already checked
    private Key generateKey(String KEY_ALIAS) throws GeneralSecurityException, IOException {
        Key key;
        try {
            key = generateKey(KEY_ALIAS, true);
        } catch (StrongBoxUnavailableException e) {
            // Retry without StrongBox if it's unavailable
            key = generateKey(KEY_ALIAS, false);
        } catch (ProviderException e) {
            // ProviderException can be thrown for various device-specific keystore issues
            // Retry without StrongBox as a fallback
            try {
                key = generateKey(KEY_ALIAS, false);
            } catch (StrongBoxUnavailableException ex) {
                // This shouldn't happen when isStrongBoxBacked=false, but handle it anyway
                throw new GeneralSecurityException("Failed to generate key without StrongBox", ex);
            } catch (ProviderException ex) {
                // If it still fails without StrongBox, wrap and rethrow
                throw new GeneralSecurityException("Keystore key generation failed", ex);
            }
        }
        return key;
    }

    private Key generateKey(String KEY_ALIAS, boolean isStrongBoxBacked)
        throws GeneralSecurityException, IOException, StrongBoxUnavailableException {
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);
        KeyGenParameterSpec.Builder paramBuilder = new KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || Build.VERSION.SDK_INT > 34) {
                // Avoiding setUnlockedDeviceRequired(true) due to known issues on Android 12-14
                paramBuilder.setUnlockedDeviceRequired(true);
            }
            paramBuilder.setIsStrongBoxBacked(isStrongBoxBacked);
        }

        generator.init(paramBuilder.build());
        return generator.generateKey();
    }

    private Key getKey(String KEY_ALIAS) throws GeneralSecurityException, IOException {
        KeyStore.SecretKeyEntry secretKeyEntry = (KeyStore.SecretKeyEntry) getKeyStore().getEntry(KEY_ALIAS, null);
        if (secretKeyEntry != null) {
            return secretKeyEntry.getSecretKey();
        }
        return generateKey(KEY_ALIAS);
    }

    private KeyStore getKeyStore() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        if (keyStore == null) {
            keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
            keyStore.load(null);
        }
        return keyStore;
    }

    private Key getAESKey(String KEY_ALIAS)
        throws CertificateException, NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, KeyStoreException, NoSuchProviderException, UnrecoverableEntryException, IOException, InvalidAlgorithmParameterException {
        SharedPreferences sharedPreferences = getContext().getSharedPreferences("", Context.MODE_PRIVATE);
        String encryptedKeyB64 = sharedPreferences.getString(ENCRYPTED_KEY, null);
        if (encryptedKeyB64 == null) {
            byte[] key = new byte[16];
            SecureRandom secureRandom = new SecureRandom();
            secureRandom.nextBytes(key);
            byte[] encryptedKey = rsaEncrypt(key, KEY_ALIAS);
            encryptedKeyB64 = Base64.encodeToString(encryptedKey, Base64.DEFAULT);
            SharedPreferences.Editor edit = sharedPreferences.edit();
            edit.putString(ENCRYPTED_KEY, encryptedKeyB64);
            edit.apply();
            return new SecretKeySpec(key, "AES");
        } else {
            byte[] encryptedKey = Base64.decode(encryptedKeyB64, Base64.DEFAULT);
            byte[] key = rsaDecrypt(encryptedKey, KEY_ALIAS);
            return new SecretKeySpec(key, "AES");
        }
    }

    private KeyStore.PrivateKeyEntry getPrivateKeyEntry(String KEY_ALIAS)
        throws NoSuchProviderException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, CertificateException, KeyStoreException, IOException, UnrecoverableEntryException {
        KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) getKeyStore().getEntry(KEY_ALIAS, null);

        if (privateKeyEntry == null) {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEY_STORE);
            keyPairGenerator.initialize(
                new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                    .setUserAuthenticationRequired(true)
                    // Set authentication validity duration to 0 to require authentication for every use
                    .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                    .build()
            );
            keyPairGenerator.generateKeyPair();
            // Get the newly generated key entry
            privateKeyEntry = (KeyStore.PrivateKeyEntry) getKeyStore().getEntry(KEY_ALIAS, null);
        }

        return privateKeyEntry;
    }

    private byte[] rsaEncrypt(byte[] secret, String KEY_ALIAS)
        throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, UnrecoverableEntryException, NoSuchProviderException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException {
        KeyStore.PrivateKeyEntry privateKeyEntry = getPrivateKeyEntry(KEY_ALIAS);
        // Encrypt the text
        Cipher inputCipher = Cipher.getInstance(RSA_MODE, "AndroidOpenSSL");
        inputCipher.init(Cipher.ENCRYPT_MODE, privateKeyEntry.getCertificate().getPublicKey());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        CipherOutputStream cipherOutputStream = new CipherOutputStream(outputStream, inputCipher);
        cipherOutputStream.write(secret);
        cipherOutputStream.close();

        return outputStream.toByteArray();
    }

    private byte[] rsaDecrypt(byte[] encrypted, String KEY_ALIAS)
        throws UnrecoverableEntryException, NoSuchAlgorithmException, KeyStoreException, NoSuchProviderException, NoSuchPaddingException, InvalidKeyException, IOException, CertificateException, InvalidAlgorithmParameterException {
        KeyStore.PrivateKeyEntry privateKeyEntry = getPrivateKeyEntry(KEY_ALIAS);
        Cipher output = Cipher.getInstance(RSA_MODE, "AndroidOpenSSL");
        output.init(Cipher.DECRYPT_MODE, privateKeyEntry.getPrivateKey());
        CipherInputStream cipherInputStream = new CipherInputStream(new ByteArrayInputStream(encrypted), output);
        ArrayList<Byte> values = new ArrayList<>();
        int nextByte;
        while ((nextByte = cipherInputStream.read()) != -1) {
            values.add((byte) nextByte);
        }

        byte[] bytes = new byte[values.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = values.get(i);
        }
        return bytes;
    }

    private boolean deviceHasCredentials() {
        KeyguardManager keyguardManager = (KeyguardManager) getActivity().getSystemService(Context.KEYGUARD_SERVICE);
        // Can only use fallback if the device has a pin/pattern/password lockscreen.
        return keyguardManager.isDeviceSecure();
    }

    /**
     * Convert BiometricManager error codes to plugin error codes
     * BiometricManager constants have different values than BiometricPrompt constants
     */
    private int convertBiometricManagerErrorToPluginError(int errorCode) {
        switch (errorCode) {
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
            case BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED:
                return 1; // BIOMETRICS_UNAVAILABLE
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                return 3; // BIOMETRICS_NOT_ENROLLED
            case BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED:
                return 1; // BIOMETRICS_UNAVAILABLE (security update required, treat as unavailable)
            default:
                return 0; // UNKNOWN_ERROR
        }
    }

    @PluginMethod
    public void getPluginVersion(final PluginCall call) {
        try {
            final JSObject ret = new JSObject();
            ret.put("version", this.pluginVersion);
            call.resolve(ret);
        } catch (final Exception e) {
            call.reject("Could not get plugin version", e);
        }
    }
}
