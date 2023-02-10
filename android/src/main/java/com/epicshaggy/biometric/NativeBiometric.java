package com.epicshaggy.biometric;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.activity.result.ActivityResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.security.GeneralSecurityException;

@CapacitorPlugin(name = "NativeBiometric")
public class NativeBiometric extends Plugin {

    // Input Key
    public static final String TITLE_KEY = "title";
    public static final String SUBTITLE_KEY = "subtitle";
    public static final String DESCRIPTION_KEY = "description";
    public static final String NEGATIVE_BUTTON_TEXT_KEY = "negativeButtonText";
    public static final String MAX_ATTEMPTS_KEY = "maxAttempts";
    public static final String USE_FALLBACK_KEY = "useFallback";
    public static final String SERVER_KEY = "server";

    // Input / Result Keys
    public static final String USERNAME_KEY = "username";
    public static final String PASSWORD_KEY = "password";

    // Result Keys
    public static final String RESULT_KEY = "result";
    public static final String SUCCESS_KEY = "success";
    public static final String FAILED_KEY = "failed";
    public static final String ERROR_DETAILS_KEY = "errorDetails";
    public static final String ERROR_CODE_KEY = "errorCode";
    public static final String IS_AVAILABLE_KEY = "isAvailable";
    public static final String BIOMETRY_TYPE_KEY = "biometryType";

    private static final int NONE = 0;
    private static final int FINGERPRINT = 3;
    private static final int FACE_AUTHENTICATION = 4;
    private static final int IRIS_AUTHENTICATION = 5;
    private static final int MULTIPLE = 6;

    enum BiometricOperation {
        VERIFY_USER,
        SET_CREDENTIALS,
        GET_CREDENTIALS
    }

    public static String actionForOperation(BiometricOperation operation) {
        return operation.name();
    }

    private final BroadcastReceiver onGetCredentials;
    private String lastGetCredentialsCallId;

    public NativeBiometric() {
        super();

        onGetCredentials = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String username = intent.getStringExtra(USERNAME_KEY);
                String password = intent.getStringExtra(PASSWORD_KEY);
                String server = intent.getStringExtra(SERVER_KEY);
                PluginCall call = bridge.getSavedCall(lastGetCredentialsCallId);
                lastGetCredentialsCallId = null;

                if (username == null || password == null || server == null) {
                    call.reject("No credentials found");
                    return;
                }

                if (call == null) {
                    // Received get credential local broadcast but no plugin call have been saved
                    return;
                }

                JSObject credentialsResult = new JSObject();
                credentialsResult.put(USERNAME_KEY, username);
                credentialsResult.put(PASSWORD_KEY, password);
                credentialsResult.put(SERVER_KEY, server);
                call.resolve(credentialsResult);
            }
        };
    }

    @Override
    public void load() {
        IntentFilter intentFilter = new IntentFilter(actionForOperation(BiometricOperation.GET_CREDENTIALS));
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(onGetCredentials, intentFilter);
        super.load();
    }

    @Override
    protected void handleOnDestroy() {
        super.handleOnDestroy();
        lastGetCredentialsCallId = null;
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(onGetCredentials);
    }

    private int getAvailableFeature() {
        // default to none
        int type = NONE;

        // if has fingerprint
        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) {
            type = FINGERPRINT;
        }

        // if has face auth
        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_FACE)
        ) {
            // if also has fingerprint
            if (type != NONE)
                return MULTIPLE;

            type = FACE_AUTHENTICATION;
        }

        // if has iris auth
        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_IRIS)) {
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

        BiometricManager biometricManager = BiometricManager.from(getContext());
        int canAuthenticateResult = biometricManager.canAuthenticate();

        if (canAuthenticateResult == BiometricManager.BIOMETRIC_SUCCESS) {
            ret.put(IS_AVAILABLE_KEY, true);
        } else {
            ret.put(IS_AVAILABLE_KEY, false);
            ret.put(ERROR_CODE_KEY, canAuthenticateResult);
        }

        ret.put(BIOMETRY_TYPE_KEY, getAvailableFeature());
        call.resolve(ret);
    }

    @Nullable
    private Intent intentForOperation(@NonNull final PluginCall call, BiometricOperation operation) {
        Intent intent = new Intent(getContext(), AuthActivity.class);
        intent.setAction(actionForOperation(operation));

        intent.putExtra(TITLE_KEY, call.getString(TITLE_KEY, "Authenticate"));

        if (call.hasOption(SUBTITLE_KEY))
            intent.putExtra(SUBTITLE_KEY, call.getString(SUBTITLE_KEY));

        if (call.hasOption(DESCRIPTION_KEY))
            intent.putExtra(DESCRIPTION_KEY, call.getString(DESCRIPTION_KEY));

        if (call.hasOption(NEGATIVE_BUTTON_TEXT_KEY))
            intent.putExtra(NEGATIVE_BUTTON_TEXT_KEY, call.getString(NEGATIVE_BUTTON_TEXT_KEY));

        if (call.hasOption(MAX_ATTEMPTS_KEY))
            intent.putExtra(MAX_ATTEMPTS_KEY, call.getInt(MAX_ATTEMPTS_KEY));

        boolean useFallback = call.getBoolean(USE_FALLBACK_KEY, false);

        if (useFallback && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            KeyguardManager keyguardManager = (KeyguardManager) getActivity().getSystemService(Context.KEYGUARD_SERVICE);
            useFallback = keyguardManager.isDeviceSecure();
        }

        intent.putExtra(USE_FALLBACK_KEY, useFallback);

        if (operation == BiometricOperation.SET_CREDENTIALS) {
            String username = call.getString(USERNAME_KEY, null);
            String password = call.getString(PASSWORD_KEY, null);
            String server = call.getString(SERVER_KEY, null);
            if (username == null || password == null || server == null) {
                return null;
            }

            intent.putExtra(USERNAME_KEY, username);
            intent.putExtra(PASSWORD_KEY, password);
            intent.putExtra(SERVER_KEY, server);

        } else if (operation == BiometricOperation.GET_CREDENTIALS) {
            String server = call.getString(SERVER_KEY, null);
            if (server == null) {
                return null;
            }

            intent.putExtra(SERVER_KEY, server);
        }

        return intent;
    }

    @PluginMethod()
    public void verifyIdentity(final PluginCall call) {
        Intent intent = intentForOperation(call, BiometricOperation.VERIFY_USER);
        if (intent == null) {
            call.reject("Missing properties");
        }
        bridge.saveCall(call);
        startActivityForResult(call, intent, "verifyResult");
    }

    @PluginMethod()
    public void setCredentials(final PluginCall call) {
        Intent intent = intentForOperation(call, BiometricOperation.SET_CREDENTIALS);
        if (intent == null) {
            call.reject("Missing properties");
        }
        bridge.saveCall(call);
        startActivityForResult(call, intent, "setCredentialsResult");
    }

    @PluginMethod()
    public void getCredentials(final PluginCall call) {

        Intent intent = intentForOperation(call, BiometricOperation.GET_CREDENTIALS);
        if (intent == null) {
            call.reject("Missing properties");
        }
        lastGetCredentialsCallId = call.getCallbackId();
        bridge.saveCall(call);
        startActivityForResult(call, intent, "getCredentialsResult");
    }

    @PluginMethod()
    public void deleteCredentials(final PluginCall call) {
        String server = call.getString(SERVER_KEY, null);

        if (server != null) {
            try {
                new CryptoManager().deleteCredentials(server, getContext());
                call.resolve();
            } catch (GeneralSecurityException e) {
                call.reject("Failed to delete", e);
            }
        } else {
            call.reject("No server name was provided");
        }
    }

    @ActivityCallback
    private void verifyResult(PluginCall call, ActivityResult result) {
        handleActivityResult(call, result, true);
    }

    @ActivityCallback
    private void getCredentialsResult(PluginCall call, ActivityResult result) {
        handleActivityResult(call,result,false);
    }

    @ActivityCallback
    private void setCredentialsResult(PluginCall call, ActivityResult result) {
        handleActivityResult(call, result, true);
    }

    private void handleActivityResult(PluginCall call, ActivityResult result, boolean resolveOnSuccess) {
        if (result.getResultCode() == Activity.RESULT_OK) {
            Intent data = result.getData();
            if (data.hasExtra(RESULT_KEY)) {
                switch (data.getStringExtra(RESULT_KEY)) {
                    case SUCCESS_KEY:
                        if(resolveOnSuccess) {
                            call.resolve();
                        }
                        break;
                    case FAILED_KEY:
                        call.reject(data.getStringExtra(ERROR_DETAILS_KEY), data.getStringExtra(ERROR_CODE_KEY));
                        break;
                    default:
                        call.reject("Verification error: " + data.getStringExtra(RESULT_KEY));
                        break;
                }
            }
        } else {
            call.reject("Something went wrong.");
        }
    }
}
