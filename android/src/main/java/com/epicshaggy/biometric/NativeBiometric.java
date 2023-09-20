package com.epicshaggy.biometric;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.activity.result.ActivityResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricConstants;
import androidx.biometric.BiometricManager;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme;
import androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme;
import androidx.security.crypto.MasterKeys;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.IOException;
import java.security.GeneralSecurityException;


@CapacitorPlugin(name = "NativeBiometric")
public class NativeBiometric extends Plugin {

    //protected final static int AUTH_CODE = 0102;

    private static final int NONE = 0;
    private static final int FINGERPRINT = 3;
    private static final int FACE_AUTHENTICATION = 4;
    private static final int IRIS_AUTHENTICATION = 5;
    private static final int MULTIPLE = 6;

    private static final String NATIVE_BIOMETRIC_SHARED_PREFERENCES = "NativeBiometricSharedPreferences";

    private int getAvailableFeature() {
        PackageManager packageManager = getContext().getPackageManager();

        // default to none
        int type = NONE;

        // if has fingerprint
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) {
            type = FINGERPRINT;
        }

        // if has face auth
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_FACE)) {
            // if also has fingerprint
            if (type != NONE)
                return MULTIPLE;

            type = FACE_AUTHENTICATION;
        }

        // if has iris auth
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_IRIS)) {
            // if also has fingerprint or face auth
            if (type != NONE)
                return MULTIPLE;

            type = IRIS_AUTHENTICATION;
        }

        return type;
    }

    @PluginMethod()
    public void isAvailable(PluginCall call) {
        JSObject ret = new JSObject();

        boolean useFallback = Boolean.TRUE.equals(call.getBoolean("useFallback", false));

        BiometricManager biometricManager = BiometricManager.from(getContext());
        int canAuthenticateResult = biometricManager.canAuthenticate();
        // Using deviceHasCredentials instead of canAuthenticate(DEVICE_CREDENTIAL)
        // > "Developers that wish to check for the presence of a PIN, pattern, or password on these versions should instead use isDeviceSecure."
        // @see https://developer.android.com/reference/androidx/biometric/BiometricManager#canAuthenticate(int)
        boolean fallbackAvailable = useFallback && this.deviceHasCredentials();
        if (useFallback && !fallbackAvailable) {
            canAuthenticateResult = BiometricConstants.ERROR_NO_DEVICE_CREDENTIAL;
        }

        boolean isAvailable = (canAuthenticateResult == BiometricManager.BIOMETRIC_SUCCESS || fallbackAvailable);
        ret.put("isAvailable", isAvailable);

        if (!isAvailable) {
            // BiometricManager Error Constants use the same values as BiometricPrompt's Constants. So we can reuse our
            int pluginErrorCode = AuthActivity.convertToPluginErrorCode(canAuthenticateResult);
            ret.put("errorCode", pluginErrorCode);
        }

        ret.put("biometryType", getAvailableFeature());
        call.resolve(ret);
    }

    @PluginMethod()
    public void verifyIdentity(final PluginCall call) {
        Intent intent = new Intent(getContext(), AuthActivity.class);

        intent.putExtra("title", call.getString("title", "Authenticate"));

        if (call.hasOption("subtitle")) {
            intent.putExtra("subtitle", call.getString("subtitle"));
        }

        if (call.hasOption("description")) {
            intent.putExtra("description", call.getString("description"));
        }

        if (call.hasOption("negativeButtonText")) {
            intent.putExtra("negativeButtonText", call.getString("negativeButtonText"));
        }

        if (call.hasOption("maxAttempts")) {
            intent.putExtra("maxAttempts", call.getInt("maxAttempts"));
        }

        boolean useFallback = Boolean.TRUE.equals(call.getBoolean("useFallback", false));
        if (useFallback) {
            useFallback = this.deviceHasCredentials();
        }

        intent.putExtra("useFallback", useFallback);

        startActivityForResult(call, intent, "verifyResult");
    }

    @PluginMethod()
    public void setCredentials(final PluginCall call) {
        String username = call.getString("username", null);
        String password = call.getString("password", null);
        String keyAlias = getKeyAlias(call);

        if (username != null && password != null && keyAlias != null) {
            try {
                SharedPreferences.Editor editor = getSharedPreferences().edit();
                editor.putString(keyAlias + "-username", username);
                editor.putString(keyAlias + "-password", password);
                editor.apply();
                call.resolve();
            } catch (GeneralSecurityException | IOException e) {
                call.reject("Failed to save credentials", e);
            }
        } else {
            call.reject("No username, password, and/or server provided");
        }
    }

    @PluginMethod()
    public void getCredentials(final PluginCall call) throws GeneralSecurityException, IOException {
        String keyAlias = getKeyAlias(call);
        if (keyAlias != null) {
            try {
                SharedPreferences sharedPreferences = getSharedPreferences();
                String username = sharedPreferences.getString(keyAlias + "-username", null);
                String password = sharedPreferences.getString(keyAlias + "-password", null);
                if (username != null && password != null) {
                    JSObject jsObject = new JSObject();
                    jsObject.put("username", username);
                    jsObject.put("password", password);
                    call.resolve(jsObject);
                } else {
                    call.reject("No credentials found");
                }
            }
            catch (Exception e) {
                call.reject("Error getting credentials", e);
            }
        } else {
            call.reject("No server provided");
        }
    }

    @ActivityCallback
    private void verifyResult(PluginCall call, ActivityResult result) {
        if (result.getResultCode() == Activity.RESULT_OK) {
            Intent data = result.getData();
            if (data != null && data.hasExtra("result")) {
                switch (data.getStringExtra("result")) {
                    case "success":
                        call.resolve();
                        break;
                    case "failed":
                    case "error":
                        call.reject(data.getStringExtra("errorDetails"), data.getStringExtra("errorCode"));
                        break;
                    default:
                        // Should not get to here unless AuthActivity starts returning different Activity Results.
                        call.reject("Something went wrong; unexpected activity result = " + data.getStringExtra("result"));
                        break;
                }
            }
        } else {
            call.reject("Something went wrong; unexpected resultCode = " + result.getResultCode());
        }
    }

    @PluginMethod()
    public void deleteCredentials(final PluginCall call) {
        String keyAlias = getKeyAlias(call);
        if (keyAlias != null) {
            try {
                SharedPreferences.Editor editor = getSharedPreferences().edit();
                editor.clear();
                editor.apply();
                call.resolve();
            } catch (GeneralSecurityException | IOException e) {
                call.reject("Failed to delete credentials", e);
            }
        } else {
            call.reject("No server provided");
        }
    }

    private boolean deviceHasCredentials() {
        KeyguardManager keyguardManager = (KeyguardManager) getActivity().getSystemService(Context.KEYGUARD_SERVICE);
        // Can only use fallback if the device has a pin/pattern/password lockscreen.
        return keyguardManager.isDeviceSecure();
    }

    @Nullable
    private String getKeyAlias(PluginCall call) {
        return call.getString("server", null);
    }

    @NonNull
    private SharedPreferences getSharedPreferences() throws GeneralSecurityException, IOException {
        String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
        return EncryptedSharedPreferences.create(
                NATIVE_BIOMETRIC_SHARED_PREFERENCES, masterKeyAlias, getContext(),
                PrefKeyEncryptionScheme.AES256_SIV, PrefValueEncryptionScheme.AES256_GCM);
    }
}
