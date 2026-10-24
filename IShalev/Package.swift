// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "IShalevDomain",
    platforms: [.macOS(.v13)],
    products: [],
    targets: [
        .target(
            name: "IShalevDomain",
            path: "Sources/Domain",
            sources: ["Domain.swift"]
        ),
        .testTarget(
            name: "IShalevDomainTests",
            dependencies: ["IShalevDomain"],
            path: "Tests/IShalevDomainTests"
        )
    ]
)
