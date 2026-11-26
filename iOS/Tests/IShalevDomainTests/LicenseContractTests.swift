import XCTest
import Foundation
@testable import IShalevDomain

final class LicenseContractTests: XCTestCase {
    func testPinnedGLKeysParseAndMatchTheirFingerprints() {
        XCTAssertTrue(GLTrust.canValidate)
        XCTAssertTrue(GLTrust.canRequest)
        // Son seis: la firma vigente y cinco rotaciones anteriores que siguen validando licencias ya emitidas
        // (las mismas que `LicenseTrust.SIGN_KEYS` en Android). Si se añade o retira una rotación, se actualiza
        // aquí y en el directorio: las dos listas deben quedarse iguales.
        XCTAssertEqual(GLTrust.signingPublicKeys.count, GLTrust.signatures.count)
        XCTAssertEqual(GLTrust.signingPublicKeys.count, 6)
        XCTAssertNotNil(GLTrust.ecdhPublicKey)
    }

    func testLicensePricesMatchAndroidContract() {
        XCTAssertEqual(GLLicenseType.monthly.totalPriceCents(secondaries: 0), 600_000)
        XCTAssertEqual(GLLicenseType.monthly.totalPriceCents(secondaries: 2), 800_000)
        XCTAssertEqual(GLLicenseType.semiannual.totalPriceCents(secondaries: 1), 3_500_000)
        XCTAssertEqual(GLLicenseType.annual.totalPriceCents(secondaries: 2), 6_800_000)
        XCTAssertEqual(GLLicenseType.perpetual.totalPriceCents(secondaries: 1), 10_700_000)
        XCTAssertEqual(GLLicenseType.monthly.totalPriceCents(secondaries: -3), 600_000)
        XCTAssertEqual(GLLicenseType.monthly.extensionPriceCents(addedSecondaries: 3), 300_000)
        XCTAssertEqual(GLLicenseType.semiannual.extensionPriceCents(addedSecondaries: 2), 1_000_000)
        XCTAssertEqual(GLLicenseType.annual.extensionPriceCents(addedSecondaries: 4), 3_600_000)
        XCTAssertEqual(GLLicenseType.perpetual.extensionPriceCents(addedSecondaries: 2), 3_400_000)
    }

    func testLicenseProfileValidationAndPhoneNormalization() {
        XCTAssertTrue(validName("María"))
        XCTAssertTrue(validName("Pérez-González"))
        XCTAssertFalse(validName("A"))
        XCTAssertFalse(validName("María1"))
        XCTAssertTrue(validCI("85010112345"))
        XCTAssertFalse(validCI("85 0101"))
        XCTAssertEqual(normalizePhone("52123456"), "+5352123456")
        XCTAssertEqual(normalizePhone("005352123456"), "+5352123456")
        XCTAssertEqual(normalizePhone("+53 5212 3456"), "+5352123456")
        XCTAssertNil(normalizePhone("123"))
    }

    func testAvisoFrescuraIsSevenDaysGSM7() {
        let expected = "Vale 7 dias: enviala y paga cuanto antes; si pasa ese tiempo, genera otra solicitud."
        XCTAssertEqual(GLLicenseMessage.AVISO_FRESCURA, expected)
        let allowed = Set("@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ\u{001b}ÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà")
        for ch in GLLicenseMessage.AVISO_FRESCURA {
            XCTAssertTrue(allowed.contains(ch), "Character '\(ch)' is not in GSM-7 basic charset")
        }
    }

    @MainActor
    func testTransferenciaValidationAndTxFlag() {
        // `ENVIAR_TX_EN_SOLICITUD` vive en `GLLicenseManager`, que es `@MainActor`: la prueba lo respeta en vez
        // de leerlo desde una autoclosura nonisolated (eso no compila).
        let enviarTx = GLLicenseManager.ENVIAR_TX_EN_SOLICITUD
        XCTAssertFalse(enviarTx)
        let pattern = "^[A-Za-z0-9-]{6,30}$"
        XCTAssertNotNil("BR601ADLM8997".range(of: pattern, options: .regularExpression))
        XCTAssertNotNil("TX-123456".range(of: pattern, options: .regularExpression))
        XCTAssertNil("12345".range(of: pattern, options: .regularExpression))
        XCTAssertNil(String(repeating: "A", count: 31).range(of: pattern, options: .regularExpression))
        XCTAssertNil("TX@123".range(of: pattern, options: .regularExpression))
    }

    func testRedeemContractNormalizationAndTranscriptVector() {
        XCTAssertEqual(RedeemContract.normalizeCode("4K7N-9X2M-B8PQ-3T5V"), "4K7N9X2MB8PQ3T5V")
        XCTAssertEqual(RedeemContract.normalizeCode(" 4k7n 9x2m b8pq 3t5v "), "4K7N9X2MB8PQ3T5V")
        XCTAssertEqual(RedeemContract.normalizeCode("4K7N9X2MB8PQ3T5V"), "4K7N9X2MB8PQ3T5V")

        XCTAssertNil(RedeemContract.normalizeCode("4K7N-9X2M-B8PQ-3T5")) // 15
        XCTAssertNil(RedeemContract.normalizeCode("4K7N-9X2M-B8PQ-3T5VV")) // 17
        XCTAssertNil(RedeemContract.normalizeCode("ABCD-EFGH-JKLM-NPQI")) // I
        XCTAssertNil(RedeemContract.normalizeCode("ABCD-EFGH-JKLM-NPQO")) // O

        let proofBytes = RedeemContract.redeemProofBytes(
            appID: "DSHALEV",
            code: "4K7N9X2MB8PQ3T5V",
            deviceKeyBase64: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEsg/+aDd+3cH8ZhZK5n77JMi/+seJn1aVw+l7XmJsOAbQRsvlmYx5suxdvhkhIbte6N8pFn196CH/jv0I9ipxog==",
            requestID: "11111111-1111-4111-8111-111111111111",
            requestedAt: "2026-11-10T12:00:00.000Z",
            nonceBase64: "AAECAwQFBgcICQoLDA0ODw=="
        )
        let expectedB64 = "AAAAEkdMLVJFREVFTS1SRVFVRVNUMQAAAAExAAAAB0RTSEFMRVYAAAAQNEs3TjlYMk1COFBRM1Q1VgAAAHxNRmt3RXdZSEtvWkl6ajBDQVFZSUtvWkl6ajBEQVFjRFFnQUVzZy8rYURkKzNjSDhaaFpLNW43N0pNaS8rc2VKbjFhVncrbDdYbUpzT0FiUVJzdmxtWXg1c3V4ZHZoa2hJYnRlNk44cEZuMTk2Q0gvanYwSTlpcHhvZz09AAAAJDExMTExMTExLTExMTEtNDExMS04MTExLTExMTExMTExMTExMQAAABgyMDI2LTExLTEwVDEyOjAwOjAwLjAwMFoAAAAYQUFFQ0F3UUZCZ2NJQ1FvTERBME9Edz09"
        XCTAssertEqual(proofBytes.base64EncodedString(), expectedB64)

        XCTAssertEqual(
            RedeemContract.mensajeCodigoInvalidoOUsado,
            "Código no válido para esta aplicación o ya utilizado"
        )
        XCTAssertEqual(
            RedeemContract.motivoAunNoDisponible,
            "Aún no disponible en esta edición"
        )
    }
}

    func testRedeemClientStaysClosedWithoutBuildFlagDirectoryBaseOrRedeemKey() {
        // T4: las tres condiciones son obligatorias; sin ellas el canje queda cerrado (no se inventa endpoint).
        let base = "https://licencias.ejemplo.test"
        let abierto = RedeemClient.make(appID: "SHALEV-IOS", enabled: true,
                                        endpointBase: base, hasActiveRedeemKey: true)
        XCTAssertTrue(abierto.canRedeem)
        XCTAssertEqual(abierto.endpointRedeem?.absoluteString, "\(base)/v1/redeem")
        XCTAssertEqual(abierto.endpointStatus?.absoluteString, "\(base)/v1/status")

        let cerradoPorBuild = RedeemClient.make(appID: "SHALEV-IOS", enabled: false,
                                                endpointBase: base, hasActiveRedeemKey: true)
        XCTAssertFalse(cerradoPorBuild.canRedeem)
        XCTAssertNil(cerradoPorBuild.endpointRedeem)

        let cerradoPorAppID = RedeemClient.make(appID: "", enabled: true,
                                               endpointBase: base, hasActiveRedeemKey: true)
        XCTAssertFalse(cerradoPorAppID.canRedeem)

        let cerradoPorClave = RedeemClient.make(appID: "SHALEV-IOS", enabled: true,
                                                endpointBase: base, hasActiveRedeemKey: false)
        XCTAssertFalse(cerradoPorClave.canRedeem)

        let cerradoPorHttp = RedeemClient.make(appID: "SHALEV-IOS", enabled: true,
                                               endpointBase: "http://licencias.ejemplo.test", hasActiveRedeemKey: true)
        XCTAssertFalse(cerradoPorHttp.canRedeem)
    }

    func testEndpointBaseDropsTrailingSlashesWithoutBreakingTheScheme() {
        let conBarras = RedeemClient.make(appID: "SHALEV-IOS", enabled: true,
                                          endpointBase: "https://licencias.ejemplo.test///",
                                          hasActiveRedeemKey: true)
        XCTAssertEqual(conBarras.endpointRedeem?.absoluteString, "https://licencias.ejemplo.test/v1/redeem")
    }

    func testBootstrapDirectoryNowPublishesRedeemBaseAndAppIDs() throws {
        // Fase 1.3: sin `canje.endpoint_base` ni clave `uso == "canje"`, APK e IPA no pueden canjear.
        // El mismo localizador que usa el arranque: cubre el bundle de la app y el del paquete de SwiftPM.
        let url = try XCTUnwrap(
            TrustDirectoryManager.shared.bundleDirectoryURL(in: Bundle(for: TrustDirectoryManager.self))
                ?? TrustDirectoryManager.shared.bundleDirectoryURL(in: .main),
            "Falta directorio-bootstrap.json en el bundle"
        )
        let directorio = try TrustDirectory.parseAndVerify(Data(contentsOf: url))
        XCTAssertEqual(directorio.v, 1)
        XCTAssertEqual(directorio.seq, 1)
        XCTAssertEqual(directorio.endpointBase, "https://gl-redeem.example.test")
        XCTAssertEqual(directorio.appIds["SHALEV-IOS"], "SHALEV-IOS")
        XCTAssertTrue(directorio.hasActiveRedeemKey)
        // `hasActiveRedeemKey` y `RedeemClient.configured()` deben coincidir para el appId del IPA.
        let cliente = RedeemClient.make(appID: "SHALEV-IOS", enabled: true,
                                        endpointBase: directorio.endpointBase,
                                        hasActiveRedeemKey: directorio.hasActiveRedeemKey)
        XCTAssertTrue(cliente.canRedeem)

        // Al ser el directorio autoritativo (D2), debe conservar las firmas históricas para no invalidar
        // licencias ya emitidas: dentro de su intervalo se suman a la vigente, después solo queda la vigente.
        let emitidaAntes = Date(timeIntervalSince1970: 1_759_622_400)
        XCTAssertEqual(directorio.signKeys(issuedAt: emitidaAntes).count, 6)
        let emitidaDespues = Date(timeIntervalSince1970: 1_761_177_601)
        XCTAssertEqual(directorio.signKeys(issuedAt: emitidaDespues).count, 1)
    }
