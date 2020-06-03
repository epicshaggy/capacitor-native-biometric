import Foundation
import Capacitor
import LocalAuthentication

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitor.ionicframework.com/docs/plugins/ios
 */
 
@objc(NativeBiometric)
public class NativeBiometric: CAPPlugin {
    
    var context = LAContext()
    typealias JSObject = [String:Any]
    
    @objc func isAvailable(_ call: CAPPluginCall) {
        var error: NSError?
        var obj = JSObject()
        
        obj["has"] = false
        
        if context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error){
            obj["has"] = true
        }
        
        obj["touchId"] = context.biometryType == .touchID
        obj["faceId"] = context.biometryType == .faceID
        obj["fingerprint"] = false
        obj["faceAuth"] = false
        obj["irisAuth"] = false
        
        call.resolve(obj)
    }
    
    @objc func verify(_ call: CAPPluginCall){
        let reason = call.getString("reason") ?? "For biometric authentication"
        
        context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason) { (success, error) in
            
            if success {
                call.resolve()
            }else{
                call.reject("Failed to authenticate")
            }
        }
        
    }
}
