import Foundation
import Capacitor
import LocalAuthentication

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitor.ionicframework.com/docs/plugins/ios
 */

@objc(NativeBiometric)
public class NativeBiometric: CAPPlugin {
    
    struct Credentials {
        var username: String
        var password: String
    }
    
    enum KeychainError: Error{
        case noPassword
        case unexpectedPasswordData
        case duplicateItem
        case unhandledError(status: OSStatus)
    }
    
    typealias JSObject = [String:Any]
    
    @objc func isAvailable(_ call: CAPPluginCall) {
        let context = LAContext()
        var error: NSError?
        var obj = JSObject()
        
        obj["isAvailable"] = false
        obj["biometryType"] = 0

        let useFallback = call.getBool("useFallback", false)
        let policy = useFallback ? LAPolicy.deviceOwnerAuthentication : LAPolicy.deviceOwnerAuthenticationWithBiometrics
        
        if context.canEvaluatePolicy(policy, error: &error){
            switch context.biometryType {
                case .touchID:
                    obj["biometryType"] = 1
                case .faceID:
                    obj["biometryType"] = 2
                default:
                    obj["biomertryType"] = 0
            }
            
            obj["isAvailable"] = true
            call.resolve(obj)
        } else {
            guard let authError = error else {
                obj["errorCode"] = 0
                call.resolve(obj)
                return
            }
            var errorCode = 0
            switch authError.code {
                case LAError.biometryNotAvailable.rawValue:
                    errorCode = 1
                    
                case LAError.biometryLockout.rawValue:
                    errorCode = 2 //"Authentication could not continue because the user has been locked out of biometric authentication, due to failing authentication too many times."
                    
                case LAError.biometryNotEnrolled.rawValue:
                    errorCode = 3//message = "Authentication could not start because the user has not enrolled in biometric authentication."
                    
                default:
                    errorCode = 0 //"Did not find error code on LAError object"
            }
            obj["errorCode"] = errorCode
            call.resolve(obj)
        }
                        
    }
    
    @objc func verifyIdentity(_ call: CAPPluginCall){
        let context = LAContext()
        var canEvaluateError: NSError?

        let useFallback = call.getBool("useFallback", false)
        let policy = useFallback ? LAPolicy.deviceOwnerAuthentication : LAPolicy.deviceOwnerAuthenticationWithBiometrics
        
        if context.canEvaluatePolicy(policy, error: &canEvaluateError){
            
            let reason = call.getString("reason") ?? "For biometric authentication"

            context.evaluatePolicy(policy, localizedReason: reason) { (success, evaluateError) in
                
                if success {
                    call.resolve()
                }else{
                    var errorCode = "0"
                    guard let error = evaluateError
                    else {
                        call.reject("Biometrics Error", "0")
                        return
                    }
                    
                    switch error._code {
                    
                    case LAError.authenticationFailed.rawValue:
                        errorCode = "10"
                        
                    case LAError.appCancel.rawValue:
                        errorCode = "11"
                        
                    case LAError.invalidContext.rawValue:
                        errorCode = "12"
                        
                    case LAError.notInteractive.rawValue:
                        errorCode = "13"
                        
                    case LAError.passcodeNotSet.rawValue:
                        errorCode = "14"
                        
                    case LAError.systemCancel.rawValue:
                        errorCode = "15"
                        
                    case LAError.userCancel.rawValue:
                        errorCode = "16"
                        
                    case LAError.userFallback.rawValue:
                        errorCode = "17"

                    case LAError.biometryNotAvailable.rawValue:
                        errorCode = "1"
                
                    case LAError.biometryLockout.rawValue:
                        errorCode = "2" //"Authentication could not continue because the user has been locked out of biometric authentication, due to failing authentication too many times."
                
                    case LAError.biometryNotEnrolled.rawValue:
                        errorCode = "3" //message = "Authentication could not start because the user has not enrolled in biometric authentication."
                        
                    default:
                        errorCode = "0" // Biometrics unavailable
                    }                    
                    call.reject(error.localizedDescription, errorCode, error )
                }
                
            }
            
        }else{
            call.reject("Authentication not available")
        }
    }
    
    @objc func getCredentials(_ call: CAPPluginCall){
        guard let server = call.getString("server") else{
            call.reject("No server name was provided")
            return
        }
        
        let reason = call.getString("reason") ?? "For biometric authentication"
        
        let authContext = LAContext()
        
        do {
            let accessControl = try getBioSecAccessControl()
            authContext.evaluateAccessControl(accessControl,
                                              operation: .useItem,
                                              localizedReason: reason) { [weak self] (laSuccess, laError) in
                
                do{
                    if laSuccess, let credentials = try self?.getCredentialsFromKeychain(server,
                                                                                         context: authContext) {
                        var obj = JSObject()
                        obj["username"] = credentials.username
                        obj["password"] = credentials.password
                        call.resolve(obj)
                    } else {
                        call.reject(laError?.localizedDescription ?? "Biometric error")
                    }
                } catch {
                    call.reject(error.localizedDescription)
                }
                
            }
        } catch {
            call.reject(error.localizedDescription)
        }
    }
    
    @objc func setCredentials(_ call: CAPPluginCall){
        
        guard   let server = call.getString("server"),
                let username = call.getString("username"),
                let password = call.getString("password") else {
            call.reject("Missing properties")
            return;
        }
        
        let reason = call.getString("reason") ?? "For biometric authentication"
        
        let credentials = Credentials(username: username, password: password)
        
        do{
            try storeCredentialsInKeychain(credentials, server, prompt:reason)
            call.resolve()
        } catch KeychainError.duplicateItem {
            do {
                try updateCredentialsInKeychain(credentials, server)
                call.resolve()
            }catch{
                call.reject(error.localizedDescription)
            }
        } catch {
            call.reject(error.localizedDescription)
        }
    }
    
    @objc func deleteCredentials(_ call: CAPPluginCall){
        guard let server = call.getString("server") else {
            call.reject("No server name was provided")
            return
        }
        
        do {
            try deleteCredentialsFromKeychain(server)
            call.resolve()
        }catch {
            call.reject(error.localizedDescription)
        }
    }
    
    private func getBioSecAccessControl() throws -> SecAccessControl {
        var error: Unmanaged<CFError>?
        
        let flags: SecAccessControlCreateFlags
        if #available(iOS 11.3, *) {
            flags = [.privateKeyUsage, .biometryCurrentSet]
        } else {
            flags = [.privateKeyUsage, .touchIDCurrentSet]
        }
        
        guard let access = SecAccessControlCreateWithFlags(kCFAllocatorDefault,
                                                        kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                                                        flags,
                                                        &error) else {
            throw KeychainError.unhandledError(status: 0)
        }
        
        return access
    }
    
    // Store user Credentials in Keychain
    func storeCredentialsInKeychain(_ credentials: Credentials,
                                    _ server: String,
                                    context: LAContext? = nil,
                                    prompt: String? = nil) throws {
        
        let acl = try getBioSecAccessControl()
        var query: [String: Any] = [kSecClass as String: kSecClassInternetPassword,
                                    kSecAttrAccount as String: credentials.username,
                                    kSecAttrServer as String: server,
                                    kSecAttrAccessControl as String: acl,
                                    kSecValueData as String: credentials.password.data(using: .utf8)!]
        
        if let context = context {
            query[kSecUseAuthenticationContext as String] = context
            
            // Prevent system UI from automatically requesting Touc ID/Face ID authentication
            // just in case someone passes here an LAContext instance without
            // a prior evaluateAccessControl call
            query[kSecUseAuthenticationUI as String] = kSecUseAuthenticationUISkip
        }
        
        if let prompt = prompt {
            query[kSecUseOperationPrompt as String] = prompt
        }
        
        let status = SecItemAdd(query as CFDictionary, nil)
        
        guard status != errSecDuplicateItem else { throw KeychainError.duplicateItem }
        guard status == errSecSuccess else { throw KeychainError.unhandledError(status: status) }
    }
    
    // Update user Credentials in Keychain
    func updateCredentialsInKeychain(_ credentials: Credentials,
                                     _ server: String,
                                     context: LAContext? = nil,
                                     prompt: String? = nil) throws {
        
        let acl = try getBioSecAccessControl()
        var query: [String: Any] = [kSecClass as String: kSecClassInternetPassword,
                                    kSecAttrAccessControl as String: acl,
                                    kSecAttrServer as String: server]
        
        if let context = context {
            query[kSecUseAuthenticationContext as String] = context
            
            // Prevent system UI from automatically requesting Touc ID/Face ID authentication
            // just in case someone passes here an LAContext instance without
            // a prior evaluateAccessControl call
            query[kSecUseAuthenticationUI as String] = kSecUseAuthenticationUISkip
        }
        
        if let prompt = prompt {
            query[kSecUseOperationPrompt as String] = prompt
        }
        
        let account = credentials.username
        let password = credentials.password.data(using: String.Encoding.utf8)!
        let attributes: [String: Any] = [kSecAttrAccount as String: account,
                                         kSecValueData as String: password]
        
        let status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        guard status != errSecItemNotFound else { throw KeychainError.noPassword }
        guard status == errSecSuccess else { throw KeychainError.unhandledError(status: status) }
    }
    
    // Get user Credentials from Keychain
    func getCredentialsFromKeychain(_ server: String,
                                    context: LAContext? = nil,
                                    prompt: String? = nil) throws -> Credentials {
        let acl = try getBioSecAccessControl()
        var query: [String: Any] = [kSecClass as String: kSecClassInternetPassword,
                                    kSecAttrServer as String: server,
                                    kSecMatchLimit as String: kSecMatchLimitOne,
                                    kSecReturnAttributes as String: true,
                                    kSecAttrAccessControl as String: acl,
                                    kSecReturnData as String: true]
        
        if let context = context {
            query[kSecUseAuthenticationContext as String] = context
            
            // Prevent system UI from automatically requesting Touc ID/Face ID authentication
            // just in case someone passes here an LAContext instance without
            // a prior evaluateAccessControl call
            query[kSecUseAuthenticationUI as String] = kSecUseAuthenticationUISkip
        }
        
        if let prompt = prompt {
            query[kSecUseOperationPrompt as String] = prompt
        }
        
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status != errSecItemNotFound else { throw KeychainError.noPassword }
        guard status == errSecSuccess else { throw KeychainError.unhandledError(status: status) }
        
        
        
        guard let existingItem = item as? [String: Any],
              let passwordData = existingItem[kSecValueData as String] as? Data,
              let password = String(data: passwordData, encoding: .utf8),
              let username = existingItem[kSecAttrAccount as String] as? String
        else {
            throw KeychainError.unexpectedPasswordData
        }
        
        let credentials = Credentials(username: username, password: password)
        return credentials
    }
    
    // Delete user Credentials from Keychain
    func deleteCredentialsFromKeychain(_ server: String) throws {
        
        var query: [String: Any] = [kSecClass as String: kSecClassInternetPassword,
                                    kSecAttrServer as String: server]
        
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw KeychainError.unhandledError(status: status) }
    }
}
