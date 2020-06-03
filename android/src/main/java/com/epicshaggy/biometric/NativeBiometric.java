package com.epicshaggy.biometric;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;

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

    private boolean hasFeature(String feature){
       return getContext().getPackageManager().hasSystemFeature(feature);
    }
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
        ret.put("fingerprint", hasFeature(PackageManager.FEATURE_FINGERPRINT));
        ret.put("faceAuth", hasFeature(PackageManager.FEATURE_FACE));
        ret.put("irisAuth", hasFeature(PackageManager.FEATURE_IRIS));
        call.resolve(ret);
    }

    @PluginMethod()
    public void verify(final PluginCall call) {
        Intent intent = new Intent(getContext(), AuthAcitivy.class);

        intent.putExtra("title", call.hasOption("title") ?
                call.getString("title"):"Authenticate");

        if(call.hasOption("subtitle"))
            intent.putExtra("subtitle", call.getString("subtitle"));

        if(call.hasOption("description"))
            intent.putExtra("description", call.getString("description"));

        saveCall(call);
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
