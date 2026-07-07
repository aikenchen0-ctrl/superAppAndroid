// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "BlinkVoiceVisual",
    platforms: [
        .iOS(.v13)
    ],
    products: [
        .library(
            name: "BlinkVoiceVisual",
            targets: ["BlinkVoiceVisual"]
        )
    ],
    targets: [
        .target(
            name: "BlinkVoiceVisual"
        ),
        .testTarget(
            name: "BlinkVoiceVisualTests",
            dependencies: ["BlinkVoiceVisual"]
        )
    ]
)
