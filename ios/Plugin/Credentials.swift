//
//  Credential.swift
//  Plugin
//
//  Created by Ramo, Davide on 10/02/23.
//  Copyright © 2023 Max Lynch. All rights reserved.
//

import Foundation

@objc(Credentials)
public class Credentials : NSObject {
    let username: String
    let password: String
    let server: String
    
    init(username: String, password: String, server: String) {
        self.username = username
        self.password = password
        self.server = server
    }
    
    @objc func toJSONObject() -> JSObject {
        var obj = JSObject()

        obj["username"] = username
        obj["password"] = password
        obj["server"] = server
        
        return obj
    }
}
