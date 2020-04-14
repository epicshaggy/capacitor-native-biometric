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
        obj["touchId"] = context.biometryType == .touchID
        obj["faceId"] = context.biometryType == .faceID
        
        if context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error){
            obj["has"] = true
        }
        
        call.resolve(obj)
    }
    
    @objc func verify(_ call: CAPPluginCall){
        let reason = "For easy Log in"
        context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason) { (success, error) in
            
            if success {
                call.resolve()
            }
        }
        call.reject("Failed")
    }
}
