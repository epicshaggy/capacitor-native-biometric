import Foundation
import Capacitor
import LocalAuthentication

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitor.ionicframework.com/docs/plugins/ios
 */



typealias JSObject = [String:Any]

@objc(NativeBiometric)
public class NativeBiometric: CAPPlugin {
    
    let cryptoManager = CryptoManager()
    
    @objc func isAvailable(_ call: CAPPluginCall) {

        let useFallback = call.getBool("useFallback", false)
        let result = cryptoManager.isAvailable(withFallback: useFallback)

        call.resolve(result.toJSONObject())
      
    }

    @objc func verifyIdentity(_ call: CAPPluginCall){
        
        let useFallback = call.getBool("useFallback", false)
        let reason = call.getString("reason") ?? "For biometric authentication"
        cryptoManager.verifyIdentity(withFallback:useFallback, reason: reason) {
            (_ success: Bool, _ errorMessage: String?, _ errorCode: String?, _ error : Error?) in
            
            if success {
                call.resolve()
            } else {
                call.reject(errorMessage ?? "Biometrics Error", errorCode, error)
            }
        }
    }

    @objc func getCredentials(_ call: CAPPluginCall){
        guard let server = call.getString("server") else{
            call.reject("No server name was provided")
            return
        }

        let reason = call.getString("reason") ?? "For biometric authentication"

        cryptoManager.getCredentials(forServer: server, reason: reason) {
            (_ credentials: Credentials?, _ errorMessage: String?, _ errorCode: String?, _ error : Error?) in
            
            guard let credentials = credentials else {
                call.reject(errorMessage ?? "Biometric error", errorCode, error)
                return
            }
            
            call.resolve(credentials.toJSONObject())
        }
    }

    @objc func setCredentials(_ call: CAPPluginCall){

        guard   let server = call.getString("server"),
                let username = call.getString("username"),
                let password = call.getString("password") else {
            call.reject("Missing properties")
            return;
        }
        
        let credentials = Credentials(username: username, password: password, server: server)
        let reason = call.getString("reason") ?? "For biometric authentication"

        cryptoManager.setCredentials(credentials, reason: reason) {
            (_ success: Bool, _ errorMessage: String?, _ errorCode: String?, _ error : Error?) in
            
            if success {
                call.resolve()
            } else {
                call.reject(errorMessage ?? "Biometrics Error", errorCode, error)
            }
        }
    }

    @objc func deleteCredentials(_ call: CAPPluginCall){
        guard let server = call.getString("server") else {
            call.reject("No server name was provided")
            return
        }

        cryptoManager.deleteCredentials(forServer: server) {
            (_ success: Bool, _ errorMessage: String?, _ errorCode: String?, _ error : Error?) in
            
            if success {
                call.resolve()
            } else {
                call.reject(errorMessage ?? "Biometrics Error", errorCode, error)
            }
        }
    }
}
