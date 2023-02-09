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

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.os.Handler;

import com.epicshaggy.biometric.capacitornativebiometric.R;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.util.concurrent.Executor;

import javax.crypto.Cipher;

public class AuthActivity extends AppCompatActivity {

    private Executor executor;
    private int maxAttempts;
    private int attemptCounter = 0;

    private NativeBiometric.BiometricOperation action;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final CryptoManager cryptoManager = new CryptoManager();

        setContentView(R.layout.activity_auth_acitivy);

        Intent intent = getIntent();
        try {
            action = NativeBiometric.BiometricOperation.valueOf(intent.getAction());
        } finally {
            action = null;
        }

        final Credentials credentials = new Credentials(intent.getStringExtra(USERNAME_KEY), intent.getStringExtra(PASSWORD_KEY), intent.getStringExtra(SERVER_KEY));

        if (!validateIntent(credentials)) return;

        maxAttempts = getIntent().getIntExtra(MAX_ATTEMPTS_KEY, 1);

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
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);

                finishWithError(errString.toString(), errorCode);
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);

                BiometricPrompt.CryptoObject cryptoObject = result.getCryptoObject();
                if (cryptoObject == null) {
                    finishWithError("No cipher returned by biometric prompt", 999);
                    return;
                }

                handleAuthenticationSucceeded(cryptoObject, cryptoManager, credentials);
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                attemptCounter++;
                if (attemptCounter == maxAttempts)
                    finishWithFail();
            }
        });

        try {
            Cipher cipher = cryptoManager.getCipher();
            Key secretKey = cryptoManager.getSecretKey(credentials.server, getApplicationContext());
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            biometricPrompt.authenticate(createPromptInfo(), new BiometricPrompt.CryptoObject(cipher));
        } catch (GeneralSecurityException | IOException e) {
            finishWithError(e.toString(), 999);
        }

    }

    private void handleAuthenticationSucceeded(BiometricPrompt.CryptoObject cryptoObject, CryptoManager cryptoManager, Credentials credentials) {
        if (action == NativeBiometric.BiometricOperation.GET_CREDENTIALS) {
            try {
                Credentials retrievedCredentials = cryptoManager.getCredentials(credentials.server, cryptoObject.getCipher(), getApplicationContext());
                Intent intent = new Intent();
                intent.setAction(actionForOperation(NativeBiometric.BiometricOperation.GET_CREDENTIALS));
                intent.putExtra(NativeBiometric.USERNAME_KEY, retrievedCredentials.username);
                intent.putExtra(NativeBiometric.PASSWORD_KEY, retrievedCredentials.password);
                if (LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent)) {
                    finishWithSuccess();
                } else {
                    finishWithError("Impossible to send credentials to plugin", 999);
                }
            } catch (GeneralSecurityException e) {
                finishWithError(e.toString(), 999);
            }
        } else if (action == NativeBiometric.BiometricOperation.SET_CREDENTIALS) {
            try {
                cryptoManager.saveCredentials(credentials, cryptoObject.getCipher(), getApplicationContext());
                finishWithSuccess();
            } catch (GeneralSecurityException e) {
                finishWithError(e.toString(), 999);
            }
        }
    }

    @NonNull
    private BiometricPrompt.PromptInfo createPromptInfo() {
        BiometricPrompt.PromptInfo.Builder promptInfoBuilder = new BiometricPrompt.PromptInfo.Builder();

        String title = getIntent().hasExtra(TITLE_KEY) ? getIntent().getStringExtra(TITLE_KEY) : "Authenticate";
        String subtitle = getIntent().hasExtra(SUBTITLE_KEY) ? getIntent().getStringExtra(SUBTITLE_KEY) : null;
        String description = getIntent().hasExtra(DESCRIPTION_KEY) ? getIntent().getStringExtra(DESCRIPTION_KEY) : null;
        promptInfoBuilder
                .setTitle(title)
                .setSubtitle(subtitle)
                .setDescription(description);

        boolean useFallback = getIntent().getBooleanExtra(USE_FALLBACK_KEY, false);
        promptInfoBuilder.setDeviceCredentialAllowed(useFallback);

        if (!useFallback) {
            String buttonText = getIntent().hasExtra(NEGATIVE_BUTTON_TEXT_KEY) ? getIntent().getStringExtra(NEGATIVE_BUTTON_TEXT_KEY) : "Cancel";
            promptInfoBuilder.setNegativeButtonText(buttonText);
        }

        return promptInfoBuilder.build();
    }

    private boolean validateIntent(Credentials credentials) {
        if (action == null) {
            finishWithError("No action specified", 999);
            return false;
        }

        switch (action) {
            case VERIFY_USER:
                finishWithError("Action not supported " + action.name(), 999);
                return false;
            case SET_CREDENTIALS:
                if (credentials.server == null) {
                    finishWithError("No server specified", 999);
                    return false;
                }
                if (credentials.username == null) {
                    finishWithError("No username specified", 999);
                    return false;
                }
                if (credentials.password == null) {
                    finishWithError("No password specified", 999);
                    return false;
                }
                break;
            case GET_CREDENTIALS:
                if (credentials.server == null) {
                    finishWithError("No server specified", 999);
                    return false;
                }
                break;
        }
        return true;
    }

    void finishWithFail() {
        Intent intent = new Intent();
        setResult(RESULT_OK, intent);
        intent.putExtra(RESULT_KEY, FAILED_KEY);
        intent.putExtra(ERROR_DETAILS_KEY, "Authentication failed.");
        intent.putExtra(ERROR_CODE_KEY, "999");
        finish();
    }

    void finishWithSuccess() {
        Intent intent = new Intent();
        setResult(RESULT_OK, intent);
        intent.putExtra(RESULT_KEY, SUCCESS_KEY);
        finish();
    }

    void finishWithError(String message, int errorCode) {
        Intent intent = new Intent();
        setResult(RESULT_OK, intent);
        intent.putExtra(RESULT_KEY, FAILED_KEY);
        intent.putExtra(ERROR_DETAILS_KEY, message);
        intent.putExtra(ERROR_CODE_KEY, String.valueOf(errorCode));
        finish();
    }

}
