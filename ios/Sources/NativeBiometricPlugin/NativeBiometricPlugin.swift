import Foundation
import Capacitor
import LocalAuthentication

// swiftlint:disable type_body_length cyclomatic_complexity

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitor.ionicframework.com/docs/plugins/ios
 */

@objc(NativeBiometricPlugin)
public class NativeBiometricPlugin: CAPPlugin, CAPBridgedPlugin {
    private let pluginVersion: String = "8.4.12"
    public let identifier = "NativeBiometricPlugin"
    public let jsName = "NativeBiometric"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "isAvailable", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "verifyIdentity", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getCredentials", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setCredentials", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "deleteCredentials", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getSecureCredentials", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isCredentialsSaved", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getPluginVersion", returnType: CAPPluginReturnPromise)
    ]

    override public func load() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleAppDidBecomeActive),
            name: UIApplication.didBecomeActiveNotification,
            object: nil
        )
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    @objc private func handleAppDidBecomeActive() {
        // Notify listeners when app becomes active (resumes from background)
        let result = checkBiometryAvailability(useFallback: false)
        notifyListeners("biometryChange", data: result)
    }

    private func checkBiometryAvailability(useFallback: Bool) -> JSObject {
        let context = LAContext()
        var error: NSError?
        var obj = JSObject()

        obj["isAvailable"] = false
        obj["authenticationStrength"] = 0 // NONE
        obj["biometryType"] = 0 // NONE
        obj["deviceIsSecure"] = false
        obj["strongBiometryIsAvailable"] = false

        // Check biometric-only policy first
        let biometricPolicy = LAPolicy.deviceOwnerAuthenticationWithBiometrics
        let hasBiometric = context.canEvaluatePolicy(biometricPolicy, error: &error)

        // Determine biometry type
        let biometryType: Int
        switch context.biometryType {
        case .touchID:
            biometryType = 1 // TOUCH_ID
        case .faceID:
            biometryType = 2 // FACE_ID
        case .opticID:
            biometryType = 2 // Treat opticID as FACE_ID for compatibility
        default:
            biometryType = 0 // NONE
        }
        obj["biometryType"] = biometryType

        // Check if device has passcode set (device is secure)
        let devicePolicy = LAPolicy.deviceOwnerAuthentication
        var deviceError: NSError?
        let deviceIsSecure = context.canEvaluatePolicy(devicePolicy, error: &deviceError)
        obj["deviceIsSecure"] = deviceIsSecure

        // Check device credentials policy if fallback is enabled
        var hasDeviceCredentials = false
        if useFallback {
            hasDeviceCredentials = deviceIsSecure
        }

        // Strong biometry is available if biometric authentication works
        // On iOS, both Face ID and Touch ID are considered STRONG
        obj["strongBiometryIsAvailable"] = hasBiometric

        if hasBiometric {
            obj["authenticationStrength"] = 1 // STRONG
            obj["isAvailable"] = true
        } else if hasDeviceCredentials {
            obj["authenticationStrength"] = 2 // WEAK
            obj["isAvailable"] = true
        } else {
            if let authError = error {
                let pluginErrorCode = convertToPluginErrorCode(authError.code)
                obj["errorCode"] = pluginErrorCode
            } else {
                obj["errorCode"] = 0
            }
        }

        return obj
    }
    struct Credentials {
        var username: String
        var password: String
    }

    enum KeychainError: Error {
        case noPassword
        case unexpectedPasswordData
        case duplicateItem
        case unhandledError(status: OSStatus)
    }

    typealias JSObject = [String: Any]

    @objc func isAvailable(_ call: CAPPluginCall) {
        let useFallback = call.getBool("useFallback", false)
        let result = checkBiometryAvailability(useFallback: useFallback)
        call.resolve(result)
    }

    @objc func verifyIdentity(_ call: CAPPluginCall) {
        let context = LAContext()
        var canEvaluateError: NSError?

        let useFallback = call.getBool("useFallback", false)
        context.localizedFallbackTitle = ""

        if useFallback {
            context.localizedFallbackTitle = nil
            if let fallbackTitle = call.getString("fallbackTitle") {
                context.localizedFallbackTitle = fallbackTitle
            }
        }

        let policy = useFallback ? LAPolicy.deviceOwnerAuthentication : LAPolicy.deviceOwnerAuthenticationWithBiometrics

        if context.canEvaluatePolicy(policy, error: &canEvaluateError) {

            let reason = call.getString("reason") ?? "For biometric authentication"

            context.evaluatePolicy(policy, localizedReason: reason) { (success, evaluateError) in

                if success {
                    call.resolve()
                } else {
                    guard let error = evaluateError else {
                        call.reject("Biometrics Error", "0")
                        return
                    }

                    var pluginErrorCode = self.convertToPluginErrorCode(error._code)
                    // use pluginErrorCode.description to convert Int to String
                    call.reject(error.localizedDescription, pluginErrorCode.description, error )
                }

            }

        } else {
            call.reject("Authentication not available")
        }
    }

    @objc func getCredentials(_ call: CAPPluginCall) {
        guard let server = call.getString("server") else {
            call.reject("No server name was provided")
            return
        }
        do {
            let credentials = try getCredentialsFromKeychain(server)
            var obj = JSObject()
            obj["username"] = credentials.username
            obj["password"] = credentials.password
            call.resolve(obj)
        } catch {
            call.reject(error.localizedDescription)
        }
    }

    @objc func setCredentials(_ call: CAPPluginCall) {

        guard let server = call.getString("server"), let username = call.getString("username"), let password = call.getString("password") else {
            call.reject("Missing properties")
            return
        }

        let accessControl = call.getInt("accessControl") ?? 0
        let credentials = Credentials(username: username, password: password)

        if accessControl > 0 {
            do {
                try storeProtectedCredentials(credentials, server, accessControl)
                call.resolve()
            } catch KeychainError.duplicateItem {
                do {
                    try deleteProtectedCredentials(server)
                    try storeProtectedCredentials(credentials, server, accessControl)
                    call.resolve()
                } catch {
                    call.reject(error.localizedDescription)
                }
            } catch {
                call.reject(error.localizedDescription)
            }
        } else {
            do {
                try storeCredentialsInKeychain(credentials, server)
                call.resolve()
            } catch KeychainError.duplicateItem {
                do {
                    try updateCredentialsInKeychain(credentials, server)
                    call.resolve()
                } catch {
                    call.reject(error.localizedDescription)
                }
            } catch {
                call.reject(error.localizedDescription)
            }
        }
    }

    @objc func getSecureCredentials(_ call: CAPPluginCall) {
        guard let server = call.getString("server") else {
            call.reject("No server name was provided")
            return
        }

        let context = LAContext()
        if let reason = call.getString("reason") {
            context.localizedReason = reason
        }

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: server,
            kSecMatchLimit as String: kSecMatchLimitOne,
            kSecReturnAttributes as String: true,
            kSecReturnData as String: true,
            kSecUseAuthenticationContext as String: context
        ]

        DispatchQueue.global(qos: .userInitiated).async {
            var item: CFTypeRef?
            let status = SecItemCopyMatching(query as CFDictionary, &item)

            DispatchQueue.main.async {
                if status == errSecUserCanceled {
                    call.reject("User canceled biometric authentication", "16")
                    return
                }
                guard status == errSecSuccess else {
                    if status == errSecItemNotFound {
                        call.reject("No protected credentials found for server", "21")
                    } else if status == errSecAuthFailed {
                        call.reject("Biometric authentication failed", "10")
                    } else {
                        call.reject("Failed to retrieve credentials: \(status)", "0")
                    }
                    return
                }

                guard let existingItem = item as? [String: Any],
                      let passwordData = existingItem[kSecValueData as String] as? Data,
                      let password = String(data: passwordData, encoding: .utf8),
                      let username = existingItem[kSecAttrAccount as String] as? String
                else {
                    call.reject("Unexpected credential data")
                    return
                }

                var obj = JSObject()
                obj["username"] = username
                obj["password"] = password
                call.resolve(obj)
            }
        }
    }

    @objc func deleteCredentials(_ call: CAPPluginCall) {
        guard let server = call.getString("server") else {
            call.reject("No server name was provided")
            return
        }

        do {
            try deleteCredentialsFromKeychain(server)
            try deleteProtectedCredentials(server)
            call.resolve()
        } catch {
            call.reject(error.localizedDescription)
        }
    }

    @objc func isCredentialsSaved(_ call: CAPPluginCall) {
        guard let server = call.getString("server") else {
            call.reject("No server name was provided")
            return
        }

        var obj = JSObject()
        obj["isSaved"] = checkCredentialsExist(server) || checkProtectedCredentialsExist(server)
        call.resolve(obj)
    }

    func checkCredentialsExist(_ server: String) -> Bool {
        let query: [String: Any] = [kSecClass as String: kSecClassInternetPassword,
                                    kSecAttrServer as String: server,
                                    kSecMatchLimit as String: kSecMatchLimitOne]

        let status = SecItemCopyMatching(query as CFDictionary, nil)
        return status == errSecSuccess
    }

    func checkProtectedCredentialsExist(_ server: String) -> Bool {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                    kSecAttrService as String: server,
                                    kSecMatchLimit as String: kSecMatchLimitOne]

        let status = SecItemCopyMatching(query as CFDictionary, nil)
        return status == errSecSuccess
    }

    func storeProtectedCredentials(_ credentials: Credentials, _ server: String, _ accessControl: Int) throws {
        guard let passwordData = credentials.password.data(using: .utf8) else {
            throw KeychainError.unexpectedPasswordData
        }

        let flags: SecAccessControlCreateFlags = accessControl == 1 ? .biometryCurrentSet : .biometryAny
        guard let access = SecAccessControlCreateWithFlags(
            kCFAllocatorDefault,
            kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly,
            flags,
            nil
        ) else {
            throw KeychainError.unhandledError(status: errSecParam)
        }

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: server,
            kSecAttrAccount as String: credentials.username,
            kSecValueData as String: passwordData,
            kSecAttrAccessControl as String: access
        ]

        let status = SecItemAdd(query as CFDictionary, nil)
        guard status != errSecDuplicateItem else { throw KeychainError.duplicateItem }
        guard status == errSecSuccess else { throw KeychainError.unhandledError(status: status) }
    }

    func deleteProtectedCredentials(_ server: String) throws {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                    kSecAttrService as String: server]

        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw KeychainError.unhandledError(status: status)
        }
    }

    func storeCredentialsInKeychain(_ credentials: Credentials, _ server: String) throws {
        guard let passwordData = credentials.password.data(using: .utf8) else {
            throw KeychainError.unexpectedPasswordData
        }

        let query: [String: Any] = [kSecClass as String: kSecClassInternetPassword,
                                    kSecAttrAccount as String: credentials.username,
                                    kSecAttrServer as String: server,
                                    kSecValueData as String: passwordData]

        let status = SecItemAdd(query as CFDictionary, nil)

        guard status != errSecDuplicateItem else { throw KeychainError.duplicateItem }
        guard status == errSecSuccess else { throw KeychainError.unhandledError(status: status) }
    }

    // Update user Credentials in Keychain
    func updateCredentialsInKeychain(_ credentials: Credentials, _ server: String) throws {
        let query: [String: Any] = [kSecClass as String: kSecClassInternetPassword,
                                    kSecAttrServer as String: server]

        let account = credentials.username
        guard let password = credentials.password.data(using: String.Encoding.utf8) else {
            throw KeychainError.unexpectedPasswordData
        }
        let attributes: [String: Any] = [kSecAttrAccount as String: account,
                                         kSecValueData as String: password]

        let status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        guard status != errSecItemNotFound else { throw KeychainError.noPassword }
        guard status == errSecSuccess else { throw KeychainError.unhandledError(status: status) }
    }

    // Get user Credentials from Keychain
    func getCredentialsFromKeychain(_ server: String) throws -> Credentials {
        let query: [String: Any] = [kSecClass as String: kSecClassInternetPassword,
                                    kSecAttrServer as String: server,
                                    kSecMatchLimit as String: kSecMatchLimitOne,
                                    kSecReturnAttributes as String: true,
                                    kSecReturnData as String: true]

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
    func deleteCredentialsFromKeychain(_ server: String)throws {
        let query: [String: Any] = [kSecClass as String: kSecClassInternetPassword,
                                    kSecAttrServer as String: server]

        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw KeychainError.unhandledError(status: status) }
    }

    /**
     * Convert Auth Error Codes to plugin expected Biometric Auth Errors (in README.md)
     * This way both iOS and Android return the same error codes for the soame authentication failure reasons.
     * !!IMPORTANT!!: Whenever this if modified, check if similar function in Android AuthActitivy.java needs to be modified as well.
     * @see https://developer.apple.com/documentation/localauthentication/laerror/code
     */
    func convertToPluginErrorCode(_ errorCode: Int) -> Int {
        switch errorCode {
        case LAError.biometryNotAvailable.rawValue:
            return 1

        case LAError.biometryLockout.rawValue:
            return 2

        case LAError.biometryNotEnrolled.rawValue:
            return 3

        case LAError.authenticationFailed.rawValue:
            return 10

        case LAError.appCancel.rawValue:
            return 11

        case LAError.invalidContext.rawValue:
            return 12

        case LAError.notInteractive.rawValue:
            return 13

        case LAError.passcodeNotSet.rawValue:
            return 14

        case LAError.systemCancel.rawValue:
            return 15

        case LAError.userCancel.rawValue:
            return 16

        case LAError.userFallback.rawValue:
            return 17

        default:
            return 0
        }
    }

    @objc func getPluginVersion(_ call: CAPPluginCall) {
        call.resolve(["version": self.pluginVersion])
    }
}

// swiftlint:enable type_body_length cyclomatic_complexity
