package com.epicshaggy.biometric;

import static com.epicshaggy.biometric.NativeBiometric.DESCRIPTION_KEY;
import static com.epicshaggy.biometric.NativeBiometric.ERROR_CODE_KEY;
import static com.epicshaggy.biometric.NativeBiometric.ERROR_DETAILS_KEY;
import static com.epicshaggy.biometric.NativeBiometric.FAILED_KEY;
import static com.epicshaggy.biometric.NativeBiometric.MAX_ATTEMPTS_KEY;
import static com.epicshaggy.biometric.NativeBiometric.NEGATIVE_BUTTON_TEXT_KEY;
import static com.epicshaggy.biometric.NativeBiometric.PASSWORD_KEY;
import static com.epicshaggy.biometric.NativeBiometric.RESULT_KEY;
import static com.epicshaggy.biometric.NativeBiometric.SERVER_KEY;
import static com.epicshaggy.biometric.NativeBiometric.SUBTITLE_KEY;
import static com.epicshaggy.biometric.NativeBiometric.SUCCESS_KEY;
import static com.epicshaggy.biometric.NativeBiometric.TITLE_KEY;
import static com.epicshaggy.biometric.NativeBiometric.USERNAME_KEY;
import static com.epicshaggy.biometric.NativeBiometric.USE_FALLBACK_KEY;
import static com.epicshaggy.biometric.NativeBiometric.actionForOperation;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricPrompt;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONException;

import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;

public class AuthActivityViewModel extends ViewModel {

    private NativeBiometric.BiometricOperation action;
    private Credentials credentials;
    private int maxAttempts;
    private int attemptCounter = 0;
    private final CryptoManager cryptoManager = new CryptoManager();

    private final MutableLiveData<Intent> _result = new MutableLiveData<>(null);
    final LiveData<Intent> result = _result;

    @Nullable
    private Credentials createCredentials(@NonNull Intent intent, @NonNull NativeBiometric.BiometricOperation action) {

        final Credentials credentials = new Credentials(intent.getStringExtra(USERNAME_KEY), intent.getStringExtra(PASSWORD_KEY), intent.getStringExtra(SERVER_KEY));

        switch (action) {
            case SET_CREDENTIALS:
                if (credentials.server == null) {
                    finishWithError("No server specified", 999);
                    return null;
                }
                if (credentials.username == null) {
                    finishWithError("No username specified", 999);
                    return null;
                }
                if (credentials.password == null) {
                    finishWithError("No password specified", 999);
                    return null;
                }
                break;
            case GET_CREDENTIALS:
                if (credentials.server == null) {
                    finishWithError("No server specified", 999);
                    return null;
                }
                break;
        }
        return credentials;

    }

    @Nullable
    private Cipher createCipher(@NonNull NativeBiometric.BiometricOperation action, Credentials credentials, Context context) throws GeneralSecurityException, IOException {
        Cipher cipher = null;
        switch (action) {
            case SET_CREDENTIALS:
                cipher = cryptoManager.getCipherForEncryption(credentials.server, context);

                break;
            case GET_CREDENTIALS:
                cipher = cryptoManager.getCipherForDecryption(credentials.server, context);
                break;
        }
        return cipher;
    }

    @NonNull
    private BiometricPrompt.PromptInfo createPromptInfo(@NonNull Intent intent) {
        BiometricPrompt.PromptInfo.Builder promptInfoBuilder = new BiometricPrompt.PromptInfo.Builder();

        String title = intent.hasExtra(TITLE_KEY) ? intent.getStringExtra(TITLE_KEY) : "Authenticate";
        String subtitle = intent.hasExtra(SUBTITLE_KEY) ? intent.getStringExtra(SUBTITLE_KEY) : null;
        String description = intent.hasExtra(DESCRIPTION_KEY) ? intent.getStringExtra(DESCRIPTION_KEY) : null;
        promptInfoBuilder.setTitle(title).setSubtitle(subtitle).setDescription(description);

        boolean useFallback = intent.getBooleanExtra(USE_FALLBACK_KEY, false);
        promptInfoBuilder.setDeviceCredentialAllowed(useFallback);

        if (!useFallback) {
            String buttonText = intent.hasExtra(NEGATIVE_BUTTON_TEXT_KEY) ? intent.getStringExtra(NEGATIVE_BUTTON_TEXT_KEY) : "Cancel";
            promptInfoBuilder.setNegativeButtonText(buttonText);
        }

        return promptInfoBuilder.build();
    }


    void authenticate(@NonNull Intent intent, BiometricPrompt biometricPrompt, Context context) {

        maxAttempts = intent.getIntExtra(MAX_ATTEMPTS_KEY, 1);

        try {
            action = NativeBiometric.BiometricOperation.valueOf(intent.getAction());
        } catch (Exception e) {
            finishWithError("Unsupported/undefined intent ACTION", 999);
            return;
        }

        credentials = createCredentials(intent, action);
        if (credentials == null) {
            finishWithError("Missing mandatory credentials info for action " + action, 999);
            return;
        }

        BiometricPrompt.PromptInfo promptInfo = createPromptInfo(intent);

        Cipher cipher = null;
        try {
            cipher = createCipher(action,credentials,context);
        } catch (GeneralSecurityException | IOException e) {
            finishWithError(e.getMessage(), 999);
            return;
        }

        if (cipher == null) {
            biometricPrompt.authenticate(promptInfo);
        } else {
            biometricPrompt.authenticate(promptInfo, new BiometricPrompt.CryptoObject(cipher));
        }
    }

    void handleAuthenticationSucceeded(BiometricPrompt.CryptoObject cryptoObject, Context context) {
        try {
            switch (action) {
                case VERIFY_USER:
                    finishWithSuccess();
                    break;
                case GET_CREDENTIALS:
                    Credentials retrievedCredentials = cryptoManager.getCredentials(credentials.server, cryptoObject.getCipher(), context);
                    Intent intent = new Intent();
                    intent.setAction(actionForOperation(NativeBiometric.BiometricOperation.GET_CREDENTIALS));
                    intent.putExtra(USERNAME_KEY, retrievedCredentials.username);
                    intent.putExtra(PASSWORD_KEY, retrievedCredentials.password);
                    intent.putExtra(SERVER_KEY, retrievedCredentials.server);
                    if (LocalBroadcastManager.getInstance(context).sendBroadcast(intent)) {
                        finishWithSuccess();
                    } else {
                        finishWithError("Impossible to send credentials to plugin", 999);
                    }
                    break;
                case SET_CREDENTIALS:
                    cryptoManager.saveCredentials(credentials, cryptoObject.getCipher(), context);
                    finishWithSuccess();
                    break;
            }
        } catch (GeneralSecurityException | JSONException e) {
            finishWithError(e.toString(), 999);
        }
    }

    void handleAuthenticationError(int errorCode, CharSequence errString) {
        finishWithError(errString.toString(), errorCode);
    }

    public void handleAuthenticationFailed() {
        attemptCounter++;
        if (attemptCounter == maxAttempts)
            finishWithFail();
    }

    private void finishWithFail() {
        Intent intent = new Intent();
        intent.putExtra(RESULT_KEY, FAILED_KEY);
        intent.putExtra(ERROR_DETAILS_KEY, "Authentication failed.");
        intent.putExtra(ERROR_CODE_KEY, "999");
        _result.postValue(intent);
    }

    private void finishWithSuccess() {
        Intent intent = new Intent();
        intent.putExtra(RESULT_KEY, SUCCESS_KEY);
        _result.postValue(intent);
    }

    private void finishWithError(String message, int errorCode) {
        Intent intent = new Intent();
        intent.putExtra(RESULT_KEY, FAILED_KEY);
        intent.putExtra(ERROR_DETAILS_KEY, message);
        intent.putExtra(ERROR_CODE_KEY, String.valueOf(errorCode));
        _result.postValue(intent);
    }
}
