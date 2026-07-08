package ee.forgr.biometric;

import android.os.Build;
import android.security.keystore.KeyProperties;
import androidx.biometric.BiometricManager;

/**
 * Maps plugin {@code allowedBiometryTypes} values to Android BiometricPrompt authenticators
 * and matching Keystore user-authentication requirements.
 */
public final class BiometricAuthenticatorConfig {

    private static final int FINGERPRINT = 3;
    private static final int FACE_AUTHENTICATION = 4;
    private static final int IRIS_AUTHENTICATION = 5;
    private static final int MULTIPLE = 6;
    private static final int DEVICE_CREDENTIAL = 7;

    // Mirrors KeyProperties auth-type flags when older compile stubs omit symbols.
    private static final int KEY_AUTH_BIOMETRIC_STRONG = 1;
    private static final int KEY_AUTH_BIOMETRIC_WEAK = 2;
    private static final int KEY_AUTH_DEVICE_CREDENTIAL = 4;

    public static final int PROMPT_BIOMETRIC_ANY =
        BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.BIOMETRIC_WEAK;

    public final int promptAuthenticators;
    public final int keyAuthTypes;
    public final boolean allowNegativeButton;
    public final boolean requiresCryptoObject;

    BiometricAuthenticatorConfig(int promptAuthenticators, int keyAuthTypes, boolean allowNegativeButton, boolean requiresCryptoObject) {
        this.promptAuthenticators = promptAuthenticators;
        this.keyAuthTypes = keyAuthTypes;
        this.allowNegativeButton = allowNegativeButton;
        this.requiresCryptoObject = requiresCryptoObject;
    }

    public static BiometricAuthenticatorConfig fromAllowedTypes(int[] allowedTypes) {
        if (allowedTypes == null || allowedTypes.length == 0) {
            return defaultBiometric();
        }

        int promptAuth = 0;
        int keyAuth = 0;
        boolean hasBiometric = false;
        boolean hasDeviceCredential = false;
        boolean fingerprintOnly = true;

        for (int type : allowedTypes) {
            switch (type) {
                case FINGERPRINT:
                    promptAuth |= BiometricManager.Authenticators.BIOMETRIC_STRONG;
                    keyAuth |= keyAuthStrong();
                    hasBiometric = true;
                    break;
                case FACE_AUTHENTICATION:
                case IRIS_AUTHENTICATION:
                    promptAuth |= PROMPT_BIOMETRIC_ANY;
                    keyAuth |= keyAuthAny();
                    hasBiometric = true;
                    fingerprintOnly = false;
                    break;
                case MULTIPLE:
                    promptAuth |= PROMPT_BIOMETRIC_ANY;
                    keyAuth |= keyAuthAny();
                    hasBiometric = true;
                    fingerprintOnly = false;
                    break;
                case DEVICE_CREDENTIAL:
                    promptAuth |= BiometricManager.Authenticators.DEVICE_CREDENTIAL;
                    keyAuth |= keyAuthDeviceCredential();
                    hasDeviceCredential = true;
                    fingerprintOnly = false;
                    break;
                default:
                    // Ignore iOS-only enum values (TOUCH_ID, FACE_ID).
                    break;
            }
        }

        if (promptAuth == 0) {
            return defaultBiometric();
        }

        if (hasBiometric && fingerprintOnly && !hasDeviceCredential) {
            promptAuth = BiometricManager.Authenticators.BIOMETRIC_STRONG;
            keyAuth = keyAuthStrong();
        }

        boolean allowNegative = !hasDeviceCredential;
        boolean deviceCredentialOnly = hasDeviceCredential && !hasBiometric;

        return new BiometricAuthenticatorConfig(promptAuth, keyAuth > 0 ? keyAuth : keyAuthAny(), allowNegative, !deviceCredentialOnly);
    }

    private static BiometricAuthenticatorConfig defaultBiometric() {
        return new BiometricAuthenticatorConfig(PROMPT_BIOMETRIC_ANY, keyAuthAny(), true, true);
    }

    private static int keyAuthStrong() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return KEY_AUTH_BIOMETRIC_STRONG;
        }
        return 0;
    }

    private static int keyAuthAny() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return KEY_AUTH_BIOMETRIC_STRONG | KEY_AUTH_BIOMETRIC_WEAK;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return KEY_AUTH_BIOMETRIC_STRONG;
        }
        return 0;
    }

    private static int keyAuthDeviceCredential() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return KEY_AUTH_DEVICE_CREDENTIAL;
        }
        return 0;
    }
}
