package cu.shalev.licencia

import cu.shalev.licencia.crypto.EcP256

/**
 * Anclas de confianza de GL fijadas en el build y puente hacia el Directorio de Confianza v1.
 *
 * En la arquitectura v1 (IMPLEMENTACION_SHALEV.md):
 *  - [TrustDirectory] gestiona las claves dinámicas (ECDH, firmas y canje) autenticadas por las raíces del build.
 *  - [LicenseTrust] actúa como envoltorio compatible: si existe un directorio efectivo cargado,
 *    consulta sus claves; de lo contrario, recurre a las claves ancladas del build.
 *  - Claves ancladas vigentes: ECDH sha256:0289:... y firma sha256:d252:...
 *    Las firmas de rotaciones anteriores (113f..., 58d4..., e693..., d9c4..., 8a91...) se conservan
 *    para seguir verificando licencias ya emitidas.
 */
object LicenseTrust {

    data class PinnedKey(val spkiB64: String, val sha256: String)

    // ECDH vigente del emisor GL (sustituida con la instalación actual).
    // Es la única con la que se cifran las solicitudes si no hay un directorio efectivo.
    val ECDH_KEY = PinnedKey(
        spkiB64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAELQaSJKdqp6b/uqGc+0juRctWGdb9mHQZj2CZRHsrKNsux9UhBbTHirLhF7q6KupNGc9PwwiqNAVcpAT7MR2EvA==",
        sha256 = "sha256:0289:7025:2266:8423:1613:2ccd:18a7:3ecd:e0ec:878f:767f:e9a4:916b:d356:6771:a2e0",
    )

    // SPKI de firma GL: el del emisor vigente primero y después cada rotación conocida,
    // para verificar también las licencias emitidas con las anteriores.
    val SIGN_KEYS: List<PinnedKey> = listOf(
        PinnedKey(
            spkiB64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAED3b2+vZldNNAkjW9X6hKZebUnQ4ycS7lqCK1Z6IMdRHlhhXq1/h7eBRj/4VYAzIjdSFKRknFaATttWM4NTlEQg==",
            sha256 = "sha256:d252:9bd9:0aa3:e8bb:d120:06e6:b4d0:7a23:9092:9a9e:7501:1a05:a80c:d994:aacb:64be",
        ),
        PinnedKey(
            spkiB64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE8TwMudgF0ioPFGp+VOQDU+xBDEQQQRHErwPyKQupkBGS8WL/dQVAFN4t3PIddOBl3Kg60ucThoOicZFctdtjIQ==",
            sha256 = "sha256:113f:a5f6:af7f:f43e:a87e:5f16:c578:36bb:58d1:d73a:c7a8:bb65:568a:b0bf:6d1a:4b1c",
        ),
        PinnedKey(
            spkiB64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE1LFKFD7Af1xhbza5CrD7ArwOwq5h4qG70edEp54zozXoUa/r7P7xRQwwV82rXcy0mPznM3TNMk8wGxYCwR7ubg==",
            sha256 = "sha256:58d4:3aac:6f1b:099a:e86c:4375:b7c7:ffb5:8631:2399:246c:1f26:f861:e302:9c83:8c4e",
        ),
        PinnedKey(
            spkiB64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPjc7tw29A0TaynecwlYDphDAtavpfNQ6njeGIX59hIDO+ID9KxTLo4ZaxlPJ/enkCzlBo+5JEMn5AUN8bneC7Q==",
            sha256 = "sha256:e693:6c20:2461:57af:0f91:2313:94a4:80a5:0d8c:d150:34a1:091b:08af:b121:2828:0a6e",
        ),
        PinnedKey(
            spkiB64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE/Kx1wh2WpM/JcPeNs7e7a5QvRMARHAPreFZ9YauzFguI85mT7/wC/jmKxR1ml5SGrGWCfA88Qo+PQ4efSUSsdg==",
            sha256 = "sha256:d9c4:d821:1880:bc79:dda6:f92a:0c0d:f6a4:dc0d:a00a:191f:9c05:229d:4c1f:3be7:998e",
        ),
        PinnedKey(
            spkiB64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE+UpBZNnTcsbL5yR5LQRD1qATSub6tV7dgaVKNgXjFUbJFbxWmUQWOQvqsOBZmt7bKDdu3iLW02Z/RtiPfa3OWQ==",
            sha256 = "sha256:8a91:bcfb:19dc:af1b:af6e:0bb7:c82a:9410:e9b5:aa1d:fea0:cab8:241c:52d1:bcbd:6888",
        ),
    )

    /** DER validado o null si falta o no coincide con la huella. Consulta primero el directorio efectivo.
     * Si hay un directorio efectivo válido, su respuesta (incluido null/vacío) manda (D2).
     * El respaldo de claves fijadas en el build solo aplica cuando NO hay directorio efectivo.
     */
    fun ecdhSpki(): ByteArray? {
        val dir = TrustDirectory.effectiveDirectory
        if (dir != null) {
            return dir.ecdhVigente()
        }
        return ECDH_KEY.validated()
    }

    fun signSpkis(emitidaEn: java.time.Instant? = null): List<ByteArray> {
        val dir = TrustDirectory.effectiveDirectory
        if (dir != null) {
            return dir.signKeysFor(emitidaEn)
        }
        return SIGN_KEYS.mapNotNull { it.validated() }
    }

    /** El build puede VALIDAR licencias (necesita al menos una clave de firma correcta). */
    val canValidate: Boolean get() = signSpkis().isNotEmpty()

    fun PinnedKey.validated(): ByteArray? = runCatching {
        if (spkiB64.isBlank()) return null
        val der = EcP256.decodeSpkiText(spkiB64)
        EcP256.publicKey(der)
        der.takeIf { EcP256.fingerprint(it) == normalizarHuella(sha256) }
    }.getOrNull()

    /** "sha256:7B51:9e55 …" (formato de GL) o hex plano → 64 hex en minúsculas. */
    fun normalizarHuella(texto: String): String =
        texto.trim().lowercase().removePrefix("sha256:").filter { it in '0'..'9' || it in 'a'..'f' }
}
