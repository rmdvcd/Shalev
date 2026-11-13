package cu.shalev.licencia.crypto

import cu.shalev.licencia.contract.Envelope
import cu.shalev.licencia.contract.GlContract
import java.security.PublicKey

/**
 * Verifica y descifra licencias LARGAS de GL (envelope v1). 0.23.0: ya no se activan; solo se re-verifica la que
 * estuviera instalada antes (hasta que venza).
 * Orden: estructura → firma con la clave de firma de GL (fijada en el build) → ECDH con la clave del dispositivo → GCM.
 */
class GlLicenseOpener(
    private val glSignKeys: List<PublicKey>,
    private val deviceKey: DeviceKey,
) {
    /**
     * Firma del emisor sobre `epk||iv||ct||tag`. Se comprueba SIN descifrar: sirve para reconocer una
     * licencia auténtica de GL emitida para OTRO dispositivo.
     */
    fun signedByIssuer(env: Envelope): Boolean = runCatching {
        if (env.v != GlContract.VERSION || env.alg != GlContract.ALG) return@runCatching false
        val epkDer = B64.dec(env.epk)
        val iv = B64.dec(env.iv)
        val ct = B64.dec(env.ct)
        val tag = B64.dec(env.tag)
        val sig = B64.dec(env.sig)
        if (iv.size != GlContract.IV_LEN || tag.size != GlContract.TAG_LEN) return@runCatching false
        val transcript = epkDer + iv + ct + tag
        glSignKeys.any { EcP256.verify(it, transcript, sig) }
    }.getOrDefault(false)

    fun open(env: Envelope, licenseId: String): ByteArray {
        if (!signedByIssuer(env)) fail()
        val epkDer = B64.dec(env.epk)
        val iv = B64.dec(env.iv)
        val ct = B64.dec(env.ct)
        val tag = B64.dec(env.tag)

        val epk = EcP256.publicKey(epkDer)
        val shared = deviceKey.agree(epk)
        val key = Hkdf.sha256(shared, iv, GlContract.HKDF_INFO_LICENSE.toByteArray(), GlContract.AES_KEY_LEN)
        return try {
            // AAD canonical con longitud prefijada (mitigación 8, Opción A). Se acepta además la AAD heredada
            // (`1|alg|<id>`) para las licencias largas que GL emitió antes de cambiar a la canonicalización.
            runCatching { AesGcm.decrypt(key, iv, GlContract.licenseAad(licenseId), ct + tag) }
                .getOrElse { AesGcm.decrypt(key, iv, GlContract.licenseAadHeredada(licenseId), ct + tag) }
        } finally {
            shared.fill(0); key.fill(0)
        }
    }
}
