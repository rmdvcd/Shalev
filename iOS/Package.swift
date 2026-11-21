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
            sources: ["Domain.swift", "License.swift", "AndroidBackup.swift", "SyncProtocol.swift", "SyncNetwork.swift", "SyncRepository.swift", "SyncManager.swift", "OcrDocumentParser.swift", "TrustDirectory.swift", "Redeem/RedeemClient.swift", "Redeem/RedeemContract.swift"],
            resources: [.process("directorio-bootstrap.json")],
            linkerSettings: [.linkedLibrary("z")]
        ),
        .testTarget(
            name: "IShalevDomainTests",
            dependencies: ["IShalevDomain"],
            path: "Tests/IShalevDomainTests"
        )
    ]
)
