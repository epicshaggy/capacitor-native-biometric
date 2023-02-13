package com.epicshaggy.biometric;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;


import com.epicshaggy.biometric.capacitornativebiometric.R;

import java.util.concurrent.Executor;

public class AuthActivity extends AppCompatActivity {

    private AuthActivityViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth_acitivy);
        viewModel = new ViewModelProvider(this).get(AuthActivityViewModel.class);

        viewModel.result.observe(this, this::handleResult);

        Intent intent = getIntent();
        viewModel.authenticate(intent,createBiometricPrompt(), getApplicationContext());
    }

    @NonNull
    private BiometricPrompt createBiometricPrompt() {
        Executor executor = ContextCompat.getMainExecutor(getApplicationContext());
        return new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                viewModel.handleAuthenticationError(errorCode, errString);
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                viewModel.handleAuthenticationSucceeded(result.getCryptoObject(), getApplicationContext());
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                viewModel.handleAuthenticationFailed();
            }
        });
    }

    private void handleResult(Intent intent) {
        if(intent != null) {
            setResult(RESULT_OK, intent);
            finish();
        }
    }






}
