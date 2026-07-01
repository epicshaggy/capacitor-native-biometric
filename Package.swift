// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "CapgoCapacitorNativeBiometric",
    platforms: [.iOS(.v15)],
    products: [
        .library(
            name: "CapgoCapacitorNativeBiometric",
            targets: ["NativeBiometricPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "8.0.0")
    ],
    targets: [
        .target(
            name: "NativeBiometricPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/NativeBiometricPlugin"),
        .testTarget(
            name: "NativeBiometricPluginTests",
            dependencies: ["NativeBiometricPlugin"],
            path: "ios/Tests/CapgoNativeBiometricPluginTests")
    ]
)
