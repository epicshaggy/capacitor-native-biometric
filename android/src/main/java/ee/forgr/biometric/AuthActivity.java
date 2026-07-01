package ee.forgr.biometric;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import ee.forgr.biometric.capacitornativebiometric.R;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.ProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Objects;
import java.util.concurrent.Executor;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

public class AuthActivity extends AppCompatActivity {

    private static final String AUTH_KEY_ALIAS = "NativeBiometricAuthKey";
    private static final String AUTH_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String SECURE_KEY_PREFIX = "NativeBiometricSecure_";
    private static final int CREDENTIAL_GCM_IV_LENGTH = 12;
    private static final String SHARED_PREFS_NAME = "NativeBiometricSharedPreferences";

    private BiometricPrompt biometricPrompt;
    private Cipher authCipher;
    private String mode;
    private int maxAttempts;
    private int counter = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth_acitivy);

        mode = getIntent().getStringExtra("mode");
        if (mode == null) mode = "verify";

        int rawMaxAttempts = getIntent().getIntExtra("maxAttempts", 1);
        maxAttempts = Math.max(1, Math.min(5, rawMaxAttempts));

        Executor executor;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            executor = this.getMainExecutor();
        } else {
            executor = new Executor() {
                @Override
                public void execute(Runnable command) {
                    new Handler().post(command);
                }
            };
        }

        BiometricPrompt.PromptInfo.Builder builder = new BiometricPrompt.PromptInfo.Builder()
            .setTitle(getIntent().hasExtra("title") ? Objects.requireNonNull(getIntent().getStringExtra("title")) : "Authenticate")
            .setSubtitle(getIntent().hasExtra("subtitle") ? getIntent().getStringExtra("subtitle") : null)
            .setDescription(getIntent().hasExtra("description") ? getIntent().getStringExtra("description") : null);

        // Note: useFallback parameter is ignored on Android (iOS-only feature)
        // Android's BiometricPrompt API has a constraint: when DEVICE_CREDENTIAL authenticator is used,
        // setNegativeButtonText() cannot be called (it will throw IllegalArgumentException).
        // Since this plugin always provides a cancel button for consistency, we cannot support
        // device credential fallback. Users should use system settings to enroll biometrics instead.
        int[] allowedTypes = getIntent().getIntArrayExtra("allowedBiometryTypes");

        int authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG;
        if (allowedTypes != null) {
            // Filter authenticators based on allowed types
            authenticators = getAllowedAuthenticators(allowedTypes);
        }
        builder.setAllowedAuthenticators(authenticators);

        String negativeText = getIntent().getStringExtra("negativeButtonText");
        builder.setNegativeButtonText(negativeText != null ? negativeText : "Cancel");

        BiometricPrompt.PromptInfo promptInfo = builder.build();

        biometricPrompt = new BiometricPrompt(
            this,
            executor,
            new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    // Handle lockout cases explicitly
                    if (errorCode == BiometricPrompt.ERROR_LOCKOUT || errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT) {
                        int pluginErrorCode = convertToPluginErrorCode(errorCode);
                        finishActivity("error", pluginErrorCode, errString.toString());
                        return;
                    }
                    int pluginErrorCode = convertToPluginErrorCode(errorCode);
                    finishActivity("error", pluginErrorCode, errString.toString());
                }

                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    if ("setSecureCredentials".equals(mode)) {
                        handleSetSecureCredentials(result);
                    } else if ("getSecureCredentials".equals(mode)) {
                        handleGetSecureCredentials(result);
                    } else {
                        if (!validateCryptoObject(result)) {
                            finishActivity("error", 10, "Biometric security check failed");
                            return;
                        }
                        finishActivity();
                    }
                }

                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                    counter++;
                    if (counter >= maxAttempts) {
                        biometricPrompt.cancelAuthentication();
                        // Use error code 4 for too many attempts to match iOS behavior
                        finishActivity("error", 4, "Too many failed attempts");
                    }
                }
            }
        );

        BiometricPrompt.CryptoObject cryptoObject;
        if ("setSecureCredentials".equals(mode)) {
            cryptoObject = createCredentialEncryptCryptoObject();
        } else if ("getSecureCredentials".equals(mode)) {
            cryptoObject = createCredentialDecryptCryptoObject();
        } else {
            cryptoObject = createCryptoObject();
        }
        if (cryptoObject == null) {
            finishActivity("error", 0, "Biometric crypto object unavailable");
            return;
        }
        biometricPrompt.authenticate(promptInfo, cryptoObject);
    }

    void finishActivity() {
        finishActivity("success", null, null);
    }

    void finishActivity(String result, Integer errorCode, String errorDetails) {
        Intent intent = new Intent();
        intent.putExtra("result", result);
        if (errorCode != null) {
            intent.putExtra("errorCode", String.valueOf(errorCode));
        }
        if (errorDetails != null) {
            intent.putExtra("errorDetails", errorDetails);
        }
        setResult(RESULT_OK, intent);
        finish();
    }

    private BiometricPrompt.CryptoObject createCryptoObject() {
        try {
            authCipher = createCipher();
            return new BiometricPrompt.CryptoObject(authCipher);
        } catch (GeneralSecurityException | IOException e) {
            return null;
        }
    }

    private Cipher createCipher() throws GeneralSecurityException, IOException {
        SecretKey secretKey = getOrCreateSecretKey();
        Cipher cipher = Cipher.getInstance(AUTH_TRANSFORMATION);
        try {
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        } catch (InvalidKeyException e) {
            // Handles KeyPermanentlyInvalidatedException (biometric enrollment changed) and
            // UserNotAuthenticatedException (key was created with time-based auth on older Android).
            deleteSecretKey();
            secretKey = getOrCreateSecretKey();
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        }
        return cipher;
    }

    private SecretKey getOrCreateSecretKey() throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        try {
            keyStore.load(null);
        } catch (CertificateException e) {
            throw new GeneralSecurityException("Failed to load AndroidKeyStore", e);
        }
        if (!keyStore.containsAlias(AUTH_KEY_ALIAS)) {
            generateSecretKey();
        }
        try {
            return (SecretKey) keyStore.getKey(AUTH_KEY_ALIAS, null);
        } catch (UnrecoverableKeyException e) {
            throw new GeneralSecurityException("Failed to retrieve biometric auth key", e);
        }
    }

    private void generateSecretKey() throws GeneralSecurityException {
        try {
            buildAuthKey(true);
        } catch (ProviderException e) {
            // Some OEM Keymaster/TEE implementations (notably Xiaomi/MIUI and Oppo/ColorOS) reject
            // setInvalidatedByBiometricEnrollment(true) and throw a generic ProviderException
            // ("Keystore key generation failed"). Retry once without that flag.
            try {
                buildAuthKey(false);
            } catch (ProviderException retryError) {
                // ProviderException is unchecked and would otherwise crash AuthActivity.onCreate.
                // Convert it so callers handle it gracefully (return null -> error result).
                throw new GeneralSecurityException("Keystore key generation failed", retryError);
            }
        }
    }

    private void buildAuthKey(boolean invalidatedByEnrollment) throws GeneralSecurityException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
            AUTH_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG);
        } else {
            // Use -1 for per-operation authentication, required for BiometricPrompt CryptoObject binding.
            // A positive value creates a time-based key that throws UserNotAuthenticatedException
            // when cipher.init() is called before authentication, breaking CryptoObject creation.
            builder.setUserAuthenticationValidityDurationSeconds(-1);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && invalidatedByEnrollment) {
            builder.setInvalidatedByBiometricEnrollment(true);
        }

        keyGenerator.init(builder.build());
        keyGenerator.generateKey();
    }

    private void deleteSecretKey() throws GeneralSecurityException, IOException {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            keyStore.deleteEntry(AUTH_KEY_ALIAS);
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
            throw new GeneralSecurityException("Failed to delete biometric auth key", e);
        }
    }

    private boolean validateCryptoObject(BiometricPrompt.AuthenticationResult result) {
        BiometricPrompt.CryptoObject cryptoObject = result.getCryptoObject();
        if (cryptoObject == null || cryptoObject.getCipher() == null) {
            return false;
        }
        if (authCipher != null && cryptoObject.getCipher() != authCipher) {
            return false;
        }
        try {
            cryptoObject.getCipher().doFinal(new byte[] { 0x00 });
            return true;
        } catch (GeneralSecurityException | IllegalStateException e) {
            return false;
        }
    }

    private SecretKey getOrCreateCredentialKey(String server, int accessControl) throws GeneralSecurityException, IOException {
        String alias = SECURE_KEY_PREFIX + server;
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);

        if (ks.containsAlias(alias)) {
            return (SecretKey) ks.getKey(alias, null);
        }

        boolean invalidatedByEnrollment = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && accessControl == 1;
        try {
            return buildCredentialKey(alias, invalidatedByEnrollment);
        } catch (ProviderException e) {
            // Xiaomi/MIUI and Oppo/ColorOS Keymasters may reject setInvalidatedByBiometricEnrollment(true)
            // with a generic ProviderException. Retry without it when it was requested.
            if (invalidatedByEnrollment) {
                try {
                    return buildCredentialKey(alias, false);
                } catch (ProviderException retryError) {
                    throw new GeneralSecurityException("Keystore key generation failed", retryError);
                }
            }
            // ProviderException is unchecked; convert it so callers handle it gracefully
            // (return null -> error result) instead of crashing AuthActivity.onCreate.
            throw new GeneralSecurityException("Keystore key generation failed", e);
        }
    }

    private SecretKey buildCredentialKey(String alias, boolean invalidatedByEnrollment) throws GeneralSecurityException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG);
        } else {
            // Use -1 for per-operation authentication, required for BiometricPrompt CryptoObject binding.
            builder.setUserAuthenticationValidityDurationSeconds(-1);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setInvalidatedByBiometricEnrollment(invalidatedByEnrollment);
        }

        keyGenerator.init(builder.build());
        return keyGenerator.generateKey();
    }

    private BiometricPrompt.CryptoObject createCredentialEncryptCryptoObject() {
        try {
            String server = getIntent().getStringExtra("server");
            int accessControl = getIntent().getIntExtra("accessControl", 2);
            SecretKey key = getOrCreateCredentialKey(server, accessControl);
            Cipher cipher = Cipher.getInstance(AUTH_TRANSFORMATION);
            try {
                cipher.init(Cipher.ENCRYPT_MODE, key);
            } catch (InvalidKeyException e) {
                // Handles KeyPermanentlyInvalidatedException and UserNotAuthenticatedException
                // (key created with time-based auth on older Android).
                KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
                ks.load(null);
                ks.deleteEntry(SECURE_KEY_PREFIX + server);
                key = getOrCreateCredentialKey(server, accessControl);
                cipher.init(Cipher.ENCRYPT_MODE, key);
            }
            return new BiometricPrompt.CryptoObject(cipher);
        } catch (GeneralSecurityException | IOException e) {
            return null;
        }
    }

    private BiometricPrompt.CryptoObject createCredentialDecryptCryptoObject() {
        try {
            String server = getIntent().getStringExtra("server");
            SharedPreferences prefs = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);
            String encryptedData = prefs.getString("secure_" + server, null);
            if (encryptedData == null) return null;

            byte[] combined = Base64.decode(encryptedData, Base64.DEFAULT);
            byte[] iv = new byte[CREDENTIAL_GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, CREDENTIAL_GCM_IV_LENGTH);

            SecretKey key = getOrCreateCredentialKey(server, 0);
            Cipher cipher = Cipher.getInstance(AUTH_TRANSFORMATION);
            try {
                cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            } catch (InvalidKeyException e) {
                // Key was created with incompatible parameters (e.g., time-based auth on older
                // Android). Delete the unusable key and the encrypted data so the user can
                // re-enroll credentials via setSecureCredentials.
                KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
                ks.load(null);
                ks.deleteEntry(SECURE_KEY_PREFIX + server);
                prefs.edit().remove("secure_" + server).apply();
                return null;
            }
            return new BiometricPrompt.CryptoObject(cipher);
        } catch (GeneralSecurityException | IOException e) {
            return null;
        }
    }

    private void handleSetSecureCredentials(BiometricPrompt.AuthenticationResult result) {
        try {
            Cipher cipher = result.getCryptoObject().getCipher();
            String username = getIntent().getStringExtra("username");
            String password = getIntent().getStringExtra("password");
            String server = getIntent().getStringExtra("server");

            JSONObject json = new JSONObject();
            json.put("u", username);
            json.put("p", password);

            byte[] encrypted = cipher.doFinal(json.toString().getBytes(StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            String encoded = Base64.encodeToString(combined, Base64.DEFAULT);

            SharedPreferences.Editor editor = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE).edit();
            editor.putString("secure_" + server, encoded);
            editor.apply();

            finishActivity();
        } catch (Exception e) {
            finishActivity("error", 0, "Failed to encrypt credentials: " + e.getMessage());
        }
    }

    private void handleGetSecureCredentials(BiometricPrompt.AuthenticationResult result) {
        try {
            Cipher cipher = result.getCryptoObject().getCipher();
            String server = getIntent().getStringExtra("server");

            SharedPreferences prefs = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);
            String encryptedData = prefs.getString("secure_" + server, null);
            if (encryptedData == null) {
                finishActivity("error", 21, "No protected credentials found");
                return;
            }

            byte[] combined = Base64.decode(encryptedData, Base64.DEFAULT);
            byte[] ciphertext = new byte[combined.length - CREDENTIAL_GCM_IV_LENGTH];
            System.arraycopy(combined, CREDENTIAL_GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            byte[] decrypted = cipher.doFinal(ciphertext);
            String jsonStr = new String(decrypted, StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(jsonStr);

            Intent intent = new Intent();
            intent.putExtra("result", "success");
            intent.putExtra("username", json.getString("u"));
            intent.putExtra("password", json.getString("p"));
            setResult(RESULT_OK, intent);
            finish();
        } catch (Exception e) {
            finishActivity("error", 0, "Failed to decrypt credentials: " + e.getMessage());
        }
    }

    /**
     * Convert Auth Error Codes to plugin expected Biometric Auth Errors (in README.md)
     * This way both iOS and Android return the same error codes for the same authentication failure reasons.
     * !!IMPORTANT!!: Whenever this is modified, check if similar function in iOS Plugin.swift needs to be modified as well
     * @see <a href="https://developer.android.com/reference/androidx/biometric/BiometricPrompt#constants">...</a>
     * @return BiometricAuthError
     */
    public static int convertToPluginErrorCode(int errorCode) {
        switch (errorCode) {
            case BiometricPrompt.ERROR_HW_UNAVAILABLE:
            case BiometricPrompt.ERROR_HW_NOT_PRESENT:
                return 1;
            case BiometricPrompt.ERROR_LOCKOUT_PERMANENT:
                return 2; // Permanent lockout
            case BiometricPrompt.ERROR_NO_BIOMETRICS:
                return 3;
            case BiometricPrompt.ERROR_LOCKOUT:
                return 4; // Temporary lockout (too many attempts)
            // Authentication Failure (10) Handled by `onAuthenticationFailed`.
            // App Cancel (11), Invalid Context (12), and Not Interactive (13) are not valid error codes for Android.
            case BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL:
                return 14;
            case BiometricPrompt.ERROR_TIMEOUT:
            case BiometricPrompt.ERROR_CANCELED:
                return 15;
            case BiometricPrompt.ERROR_USER_CANCELED:
            case BiometricPrompt.ERROR_NEGATIVE_BUTTON:
                return 16;
            case BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC:
                return 0; // Success case, should not be handled here
            default:
                return 0;
        }
    }

    private int getAllowedAuthenticators(int[] allowedTypes) {
        int authenticators = 0;
        for (int type : allowedTypes) {
            switch (type) {
                case 3: // FINGERPRINT
                    authenticators |= BiometricManager.Authenticators.BIOMETRIC_STRONG;
                    break;
                case 4: // FACE_AUTHENTICATION
                    authenticators |= BiometricManager.Authenticators.BIOMETRIC_STRONG;
                    break;
                case 5: // IRIS_AUTHENTICATION
                    authenticators |= BiometricManager.Authenticators.BIOMETRIC_STRONG;
                    break;
                case 6: // MULTIPLE - allow all biometric types
                    authenticators |= BiometricManager.Authenticators.BIOMETRIC_STRONG;
                    break;
                case 7: // DEVICE_CREDENTIAL (PIN, pattern, or password)
                    authenticators |= BiometricManager.Authenticators.DEVICE_CREDENTIAL;
                    break;
            }
        }
        return authenticators > 0 ? authenticators : BiometricManager.Authenticators.BIOMETRIC_STRONG;
    }
}
