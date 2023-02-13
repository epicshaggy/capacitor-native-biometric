//
//  CryptoManager.swift
//  CapacitorNativeBiometric
//
//  Created by Ramo, Davide on 10/02/23.
//

import Foundation
import LocalAuthentication

typealias VerifyIdentityCallback = (_ success: Bool, _ errorMessage: String?, _ errorCode: String?, _ error : Error?) -> Void
typealias GetCredentialsCallback = (_ credentials: Credentials?, _ errorMessage: String?, _ errorCode: String?, _ error : Error?) -> Void
typealias SetCredentialsCallback = (_ success: Bool, _ errorMessage: String?, _ errorCode: String?, _ error : Error?) -> Void
typealias DeleteCredentialsCallback = (_ success: Bool, _ errorMessage: String?, _ errorCode: String?, _ error : Error?) -> Void

public enum KeychainError: Error{
    case noPassword
    case unexpectedPasswordData
    case duplicateItem
    case unhandledError(status: OSStatus)
}

@objc(CryptoManager)
public class CryptoManager : NSObject {
    
    private func getBioSecAccessControl() throws -> SecAccessControl {
        var error: Unmanaged<CFError>?
        
        let flags: SecAccessControlCreateFlags
        if #available(iOS 11.3, *) {
            flags = [.biometryCurrentSet]
        } else {
            flags = [.touchIDCurrentSet]
        }
        
        guard let access = SecAccessControlCreateWithFlags(
            kCFAllocatorDefault,
            kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly,
            flags,
            &error) else {
            throw KeychainError.unhandledError(status: 0)
        }
        
        return access
    }
    
    @objc public func isAvailable(withFallback useFallback : Bool) -> Availability {
        let context = LAContext()
        var error: NSError?
        
        let policy = useFallback ? LAPolicy.deviceOwnerAuthentication : LAPolicy.deviceOwnerAuthenticationWithBiometrics
        
        if context.canEvaluatePolicy(policy, error: &error){
            
            let result : Availability
            
            switch context.biometryType {
            case .touchID:
                result = Availability(biometryType: 1)
            case .faceID:
                result = Availability(biometryType: 2)
            default:
                result = Availability()
            }
            
            return result
        } else {
            guard let authError = error else {
                return Availability(errorCode: 0)
            }
            let result : Availability
            
            switch authError.code {
            case LAError.biometryNotAvailable.rawValue:
                result = Availability(errorCode: 1)
                
            case LAError.biometryLockout.rawValue:
                result = Availability(errorCode: 2) //"Authentication could not continue because the user has been locked out of biometric authentication, due to failing authentication too many times."
                
            case LAError.biometryNotEnrolled.rawValue:
                result = Availability(errorCode: 3) //message = "Authentication could not start because the user has not enrolled in biometric authentication."
            default:
                result = Availability(errorCode: 0)
            }
            
            return result
        }
    }
    
    @objc func verifyIdentity(withFallback useFallback : Bool, reason : String, callback: @escaping VerifyIdentityCallback) {
        let context = LAContext()
        var canEvaluateError: NSError?
        
        
        let policy = useFallback ? LAPolicy.deviceOwnerAuthentication : LAPolicy.deviceOwnerAuthenticationWithBiometrics
        
        if context.canEvaluatePolicy(policy, error: &canEvaluateError){
            
            context.evaluatePolicy(policy, localizedReason: reason) { (success, evaluateError) in
                
                if success {
                    callback(true, nil, nil, nil)
                    return
                }else{
                    var errorCode = "0"
                    guard let error = evaluateError else {
                        callback(false, "Biometrics Error", "0", nil)
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
                    callback(false, error.localizedDescription, errorCode, error)
                    return
                }
                
            }
            
        } else {
            callback(false, "Authentication not available", "0", nil)
        }
    }
    
    @objc func getCredentials(forServer server: String, reason: String, callback: @escaping GetCredentialsCallback) {
        
        let authContext = LAContext()
        
        do {
            let accessControl = try getBioSecAccessControl()
            
            authContext.evaluateAccessControl(
                accessControl,
                operation: .useItem,
                localizedReason: reason
            ) { (laSuccess, laError) in
                
                do {
                    if !laSuccess {
                        callback(nil,laError?.localizedDescription ?? "Biometric error", nil, nil)
                        return
                    }
                        
                    let credentials = try self.getCredentialsFromKeychain(
                        server,
                        context: authContext
                    )
                    
                    callback(credentials, nil, nil, nil)
                    
                } catch {
                    callback(nil,error.localizedDescription, nil, nil)
                }
                
            }
        } catch {
            callback(nil, error.localizedDescription, nil, nil)
        }
    }
    
    @objc func setCredentials(_ credentials: Credentials, reason: String, callback: @escaping SetCredentialsCallback) {
        
        do{
            try? deleteCredentialsFromKeychain(credentials.server)
            try storeCredentialsInKeychain(credentials, prompt:reason)
            callback(true,nil,nil,nil)
        } catch {
            callback(false,error.localizedDescription,nil,error)
        }
    }
    
    @objc func deleteCredentials(forServer server: String, callback: @escaping DeleteCredentialsCallback) {
        
        do{
            try deleteCredentialsFromKeychain(server)
            callback(true,nil,nil,nil)
        } catch {
            callback(false,error.localizedDescription,nil,error)
        }
    }
    
    // Get user Credentials from Keychain
    private func getCredentialsFromKeychain(_ server: String,
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
        
        let credentials = Credentials(username: username, password: password, server: server)
        return credentials
    }
    
    // Store user Credentials in Keychain
    func storeCredentialsInKeychain(_ credentials: Credentials,
                                    context: LAContext? = nil,
                                    prompt: String? = nil) throws {
        
        let acl = try getBioSecAccessControl()
        var query: [String: Any] = [kSecClass as String: kSecClassInternetPassword,
                                    kSecAttrAccount as String: credentials.username,
                                    kSecAttrServer as String: credentials.server,
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
    
    // Delete user Credentials from Keychain
    func deleteCredentialsFromKeychain(_ server: String) throws {
        
        let query: [String: Any] = [kSecClass as String: kSecClassInternetPassword,
                                    kSecAttrServer as String: server]
        
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw KeychainError.unhandledError(status: status) }
    }
}
