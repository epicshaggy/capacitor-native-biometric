package ee.forgr.biometric;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.security.keystore.UserNotAuthenticatedException;
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
    private static final String SECURE_VALIDITY_SUFFIX = "_validity";

    private BiometricPrompt biometricPrompt;
    private Cipher authCipher;
    private String mode;
    private int maxAttempts;
    private int counter = 0;
    private int authValidityDuration;
    private BiometricAuthenticatorConfig authenticatorConfig;
    private static final String AUTH_KEY_AUTH_TYPES = "auth_key_auth_types";

    // Mirrors KeyProperties auth-type flags when older compile stubs omit symbols.
    private static final int KEY_AUTH_BIOMETRIC_STRONG = 1;
    private static final int KEY_AUTH_BIOMETRIC_WEAK = 2;
    private static final int KEY_AUTH_DEVICE_CREDENTIAL = 4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth_acitivy);

        mode = getIntent().getStringExtra("mode");
        if (mode == null) mode = "verify";

        int rawMaxAttempts = getIntent().getIntExtra("maxAttempts", 1);
        maxAttempts = Math.max(1, Math.min(5, rawMaxAttempts));

        String server = getIntent().getStringExtra("server");
        if ("setSecureCredentials".equals(mode)) {
            // Not yet persisted — this call establishes the mode for the alias.
            authValidityDuration = Math.max(0, getIntent().getIntExtra("authValidityDuration", 0));
        } else if ("getSecureCredentials".equals(mode)) {
            authValidityDuration = getStoredAuthValidityDuration(server);
        }

        if (("setSecureCredentials".equals(mode) || "getSecureCredentials".equals(mode)) && authValidityDuration > 0) {
            // Opt-in validity-window mode: try the Keystore operation without a prompt first.
            // If the window already covers us, we can finish immediately with no BiometricPrompt.
            if (tryWithoutPrompt()) {
                return;
            }
        }

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

        int[] allowedTypes = getIntent().getIntArrayExtra("allowedBiometryTypes");
        authenticatorConfig = BiometricAuthenticatorConfig.fromAllowedTypes(allowedTypes);
        builder.setAllowedAuthenticators(authenticatorConfig.promptAuthenticators);

        if (authenticatorConfig.allowNegativeButton) {
            String negativeText = getIntent().getStringExtra("negativeButtonText");
            builder.setNegativeButtonText(negativeText != null ? negativeText : "Cancel");
        }

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
                    boolean isValidityWindowMode =
                        ("setSecureCredentials".equals(mode) || "getSecureCredentials".equals(mode)) && authValidityDuration > 0;
                    if (isValidityWindowMode) {
                        // The prompt carries no CryptoObject in this mode (see tryWithoutPrompt) — the
                        // successful authentication merely unlocks the Keystore key for the validity
                        // window. Retry the plain cipher operation now that the device is authenticated.
                        retryAfterPrompt();
                    } else if ("setSecureCredentials".equals(mode)) {
                        handleSetSecureCredentials(result);
                    } else if ("getSecureCredentials".equals(mode)) {
                        handleGetSecureCredentials(result);
                    } else if (authenticatorConfig.requiresCryptoObject) {
                        if (!validateCryptoObject(result)) {
                            finishActivity("error", 10, "Biometric security check failed");
                            return;
                        }
                        finishActivity();
                    } else {
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

        if (("setSecureCredentials".equals(mode) || "getSecureCredentials".equals(mode)) && authValidityDuration > 0) {
            // Validity-window mode: a single authentication unlocks the Keystore key for
            // `authValidityDuration` seconds, so the prompt is not bound to a CryptoObject.
            biometricPrompt.authenticate(promptInfo);
            return;
        }

        if (!authenticatorConfig.requiresCryptoObject) {
            biometricPrompt.authenticate(promptInfo);
            return;
        }

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
        int expectedAuthTypes = getAuthKeyTypes();
        int storedAuthTypes = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE).getInt(AUTH_KEY_AUTH_TYPES, -1);
        if (keyStore.containsAlias(AUTH_KEY_ALIAS) && storedAuthTypes != expectedAuthTypes) {
            keyStore.deleteEntry(AUTH_KEY_ALIAS);
        }
        if (!keyStore.containsAlias(AUTH_KEY_ALIAS)) {
            generateSecretKey();
            storeAuthKeyTypes(expectedAuthTypes);
        }
        try {
            return (SecretKey) keyStore.getKey(AUTH_KEY_ALIAS, null);
        } catch (UnrecoverableKeyException e) {
            throw new GeneralSecurityException("Failed to retrieve biometric auth key", e);
        }
    }

    private void generateSecretKey() throws GeneralSecurityException {
        try {
            buildAuthKey(true, getAuthKeyTypes());
        } catch (ProviderException e) {
            // Some OEM Keymaster/TEE implementations (notably Xiaomi/MIUI and Oppo/ColorOS) reject
            // setInvalidatedByBiometricEnrollment(true) and throw a generic ProviderException
            // ("Keystore key generation failed"). Retry once without that flag.
            try {
                buildAuthKey(false, getAuthKeyTypes());
            } catch (ProviderException retryError) {
                // ProviderException is unchecked and would otherwise crash AuthActivity.onCreate.
                // Convert it so callers handle it gracefully (return null -> error result).
                throw new GeneralSecurityException("Keystore key generation failed", retryError);
            }
        }
    }

    private void buildAuthKey(boolean invalidatedByEnrollment, int keyAuthTypes) throws GeneralSecurityException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
            AUTH_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            int authTypes = keyAuthTypes > 0 ? keyAuthTypes : defaultKeyAuthTypes();
            builder.setUserAuthenticationParameters(0, authTypes);
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
        return getOrCreateCredentialKey(server, accessControl, 0);
    }

    private SecretKey getOrCreateCredentialKey(String server, int accessControl, int authValidityDuration)
        throws GeneralSecurityException, IOException {
        String alias = SECURE_KEY_PREFIX + server;
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);

        if (ks.containsAlias(alias)) {
            // If the caller asked for a different auth model than the one the existing key was
            // generated with (per-operation vs. time-based validity window), the key must be
            // regenerated so the new setUserAuthenticationParameters() take effect — Keystore
            // does not allow changing these parameters on an existing key.
            if (getStoredAuthValidityDuration(server) != authValidityDuration) {
                ks.deleteEntry(alias);
            } else {
                return (SecretKey) ks.getKey(alias, null);
            }
        }

        boolean invalidatedByEnrollment = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && accessControl == 1;
        SecretKey key;
        try {
            key = buildCredentialKey(alias, invalidatedByEnrollment, authValidityDuration);
        } catch (ProviderException e) {
            // Xiaomi/MIUI and Oppo/ColorOS Keymasters may reject setInvalidatedByBiometricEnrollment(true)
            // with a generic ProviderException. Retry without it when it was requested.
            if (invalidatedByEnrollment) {
                try {
                    key = buildCredentialKey(alias, false, authValidityDuration);
                } catch (ProviderException retryError) {
                    throw new GeneralSecurityException("Keystore key generation failed", retryError);
                }
            } else {
                // ProviderException is unchecked; convert it so callers handle it gracefully
                // (return null -> error result) instead of crashing AuthActivity.onCreate.
                throw new GeneralSecurityException("Keystore key generation failed", e);
            }
        }
        storeAuthValidityDuration(server, authValidityDuration);
        return key;
    }

    private SecretKey buildCredentialKey(String alias, boolean invalidatedByEnrollment, int authValidityDuration)
        throws GeneralSecurityException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            int authTypes = getAuthKeyTypes();
            if (authTypes == 0) {
                authTypes = defaultKeyAuthTypes();
            }
            builder.setUserAuthenticationParameters(Math.max(0, authValidityDuration), authTypes);
        } else if (authValidityDuration > 0) {
            builder.setUserAuthenticationValidityDurationSeconds(authValidityDuration);
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

    private int getStoredAuthValidityDuration(String server) {
        SharedPreferences prefs = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);
        return prefs.getInt("secure_" + server + SECURE_VALIDITY_SUFFIX, 0);
    }

    private void storeAuthValidityDuration(String server, int authValidityDuration) {
        SharedPreferences.Editor editor = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE).edit();
        editor.putInt("secure_" + server + SECURE_VALIDITY_SUFFIX, authValidityDuration);
        editor.apply();
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
        encryptAndStoreCredentials(result.getCryptoObject().getCipher());
    }

    private void handleGetSecureCredentials(BiometricPrompt.AuthenticationResult result) {
        decryptAndReturnCredentials(result.getCryptoObject().getCipher());
    }

    private void encryptAndStoreCredentials(Cipher cipher) {
        try {
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

    private void decryptAndReturnCredentials(Cipher cipher) {
        try {
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
     * Validity-window mode (authValidityDuration > 0): attempt the Keystore cipher operation
     * directly, with no BiometricPrompt. If a prior authentication is still within the window,
     * this succeeds immediately and the credential read/write finishes without ever prompting
     * the user. Returns true if the activity was finished this way (success or a non-auth
     * error); returns false if a BiometricPrompt is required (window expired or never started),
     * in which case the caller should continue with the prompt-without-CryptoObject flow.
     */
    private boolean tryWithoutPrompt() {
        try {
            if ("setSecureCredentials".equals(mode)) {
                String server = getIntent().getStringExtra("server");
                int accessControl = getIntent().getIntExtra("accessControl", 2);
                Cipher cipher = createCredentialCipherForEncrypt(server, accessControl, authValidityDuration);
                encryptAndStoreCredentials(cipher);
            } else {
                String server = getIntent().getStringExtra("server");
                Cipher cipher = createCredentialCipherForDecrypt(server, authValidityDuration);
                if (cipher == null) {
                    finishActivity("error", 21, "No protected credentials found");
                    return true;
                }
                decryptAndReturnCredentials(cipher);
            }
            return true;
        } catch (UserNotAuthenticatedException e) {
            // Validity window expired (or this is the first use) — a prompt is required.
            return false;
        } catch (GeneralSecurityException | IOException e) {
            finishActivity("error", 0, "Keystore operation failed: " + e.getMessage());
            return true;
        }
    }

    /**
     * Called after a successful BiometricPrompt shown without a CryptoObject (validity-window
     * mode). The authentication just performed unlocks the Keystore key for the window, so the
     * same cipher operation that threw UserNotAuthenticatedException in tryWithoutPrompt() is
     * now expected to succeed.
     */
    private void retryAfterPrompt() {
        try {
            if ("setSecureCredentials".equals(mode)) {
                String server = getIntent().getStringExtra("server");
                int accessControl = getIntent().getIntExtra("accessControl", 2);
                Cipher cipher = createCredentialCipherForEncrypt(server, accessControl, authValidityDuration);
                encryptAndStoreCredentials(cipher);
            } else {
                String server = getIntent().getStringExtra("server");
                Cipher cipher = createCredentialCipherForDecrypt(server, authValidityDuration);
                if (cipher == null) {
                    finishActivity("error", 21, "No protected credentials found");
                    return;
                }
                decryptAndReturnCredentials(cipher);
            }
        } catch (GeneralSecurityException | IOException e) {
            finishActivity("error", 0, "Keystore operation failed after authentication: " + e.getMessage());
        }
    }

    private Cipher createCredentialCipherForEncrypt(String server, int accessControl, int authValidityDuration)
        throws GeneralSecurityException, IOException {
        SecretKey key = getOrCreateCredentialKey(server, accessControl, authValidityDuration);
        Cipher cipher = Cipher.getInstance(AUTH_TRANSFORMATION);
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key);
        } catch (InvalidKeyException e) {
            if (e instanceof UserNotAuthenticatedException) {
                throw e;
            }
            // KeyPermanentlyInvalidatedException (biometric enrollment changed) — delete and recreate.
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            ks.deleteEntry(SECURE_KEY_PREFIX + server);
            key = getOrCreateCredentialKey(server, accessControl, authValidityDuration);
            cipher.init(Cipher.ENCRYPT_MODE, key);
        }
        return cipher;
    }

    private Cipher createCredentialCipherForDecrypt(String server, int authValidityDuration) throws GeneralSecurityException, IOException {
        SharedPreferences prefs = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);
        String encryptedData = prefs.getString("secure_" + server, null);
        if (encryptedData == null) return null;

        byte[] combined = Base64.decode(encryptedData, Base64.DEFAULT);
        byte[] iv = new byte[CREDENTIAL_GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, CREDENTIAL_GCM_IV_LENGTH);

        SecretKey key = getOrCreateCredentialKey(server, 0, authValidityDuration);
        Cipher cipher = Cipher.getInstance(AUTH_TRANSFORMATION);
        try {
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        } catch (InvalidKeyException e) {
            if (e instanceof UserNotAuthenticatedException) {
                throw e;
            }
            // Key was invalidated (e.g. biometric enrollment changed). Delete the unusable key
            // and encrypted data so the user can re-enroll via setSecureCredentials.
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            ks.deleteEntry(SECURE_KEY_PREFIX + server);
            prefs.edit().remove("secure_" + server).apply();
            return null;
        }
        return cipher;
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

    private static int defaultKeyAuthTypes() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return KEY_AUTH_BIOMETRIC_STRONG | KEY_AUTH_BIOMETRIC_WEAK;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return KEY_AUTH_BIOMETRIC_STRONG;
        }
        return 0;
    }

    private int getAuthKeyTypes() {
        if (authenticatorConfig != null && authenticatorConfig.keyAuthTypes > 0) {
            return authenticatorConfig.keyAuthTypes;
        }
        return defaultKeyAuthTypes();
    }

    private void storeAuthKeyTypes(int authTypes) {
        getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE).edit().putInt(AUTH_KEY_AUTH_TYPES, authTypes).apply();
    }
}
