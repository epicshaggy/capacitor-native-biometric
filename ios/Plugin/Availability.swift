//
//  Availability.swift
//  Plugin
//
//  Created by Ramo, Davide on 10/02/23.
//  Copyright © 2023 Max Lynch. All rights reserved.
//

import Foundation


@objc(Availability)
public class Availability : NSObject, Codable {
    let isAvailable : Bool
    let biometryType : Int
    let errorCode : Int?
    
    init(isAvailable: Bool = true, biometryType: Int = 0, errorCode : Int? = nil) {
        self.isAvailable = isAvailable
        self.biometryType = biometryType
        self.errorCode = errorCode
    }
    
    @objc func toJSONObject() -> JSObject {
        var obj = JSObject()

        obj["isAvailable"] = isAvailable
        obj["biometryType"] = biometryType
        obj["errorCode"] = errorCode
        
        return obj
    }
}
