package com.epicshaggy.biometric;

import android.app.Activity;
import android.content.Intent;

import androidx.biometric.BiometricManager;
import com.getcapacitor.JSObject;
import com.getcapacitor.NativePlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;


@NativePlugin(requestCodes = {NativeBiometric.AUTH_CODE})
public class NativeBiometric extends Plugin {

    private BiometricManager biometricManager;
    protected final static int AUTH_CODE = 0102;

    @PluginMethod()
    public void isAvailable(PluginCall call) {
        JSObject ret = new JSObject();

        biometricManager = BiometricManager.from(getContext());

        switch (biometricManager.canAuthenticate()) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                ret.put("has", true);
                break;
            default:
                ret.put("has", false);
                break;
        }

        ret.put("touchId", false);
        ret.put("faceId", false);
        call.resolve(ret);
    }

    @PluginMethod()
    public void verify(final PluginCall call) {
        saveCall(call);
        Intent intent = new Intent(getContext(), AuthAcitivy.class);
        startActivityForResult(call, intent, AUTH_CODE);
    }

    @Override
    protected void handleOnActivityResult(int requestCode, int resultCode, Intent data) {
        super.handleOnActivityResult(requestCode, resultCode, data);
        PluginCall call = getSavedCall();
        if(requestCode == AUTH_CODE){
            if(resultCode == Activity.RESULT_OK){
                if(data.hasExtra("result")){
                    switch (data.getStringExtra("result")){
                        case "success":
                            call.resolve();
                            break;
                        default:
                            call.reject("Failed to authenticate");
                            break;
                        /*case "failed":
                            call.reject("Failed to authenticate");
                            break;
                        case "error":
                            call.error("");
                            break;*/
                    }
                }
            }
        }
    }
}
