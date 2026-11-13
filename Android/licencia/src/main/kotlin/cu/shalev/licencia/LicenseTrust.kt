package cu.shalev.licencia

import cu.shalev.licencia.crypto.EcP256

/**
 * Anclas de confianza de GL, FIJADAS EN EL BUILD (decisión D3, enmienda D3.1).
 * No son secretas; SÍ son críticas: la de firma es la única que puede validar licencias.
 *  - SIGN_KEYS: obligatorias. NUNCA se leen de la configuración ni de archivos importables.
 *  - ECDH_KEY: también fijada (Prompt 17: se eliminó la pantalla «Clave del emisor»). Sin ella no se pueden
 *    cifrar solicitudes y el panel de Licencia avisa de que hace falta una versión actualizada.
 *
 * Origen: pestaña "Claves" de GL → «Copiar ambas claves» (contrato v1, kid gl-sign-v1).
 *  - Vigentes del emisor (GL 1.2.0, paquete dev.gl.license, teléfono 4bc9f8f3; leídas de su
 *    pantalla Claves el 2026-10-22 y huellas recalculadas desde el Base64): ECDH
 *    sha256:fb3a:…:078d y firma sha256:113f:…:4b1c. Son las que abren las solicitudes y firman
 *    las licencias hoy.
 *  - Las firmas de instalaciones anteriores (58d4… del bundle del 05/10/2026, e693… y d9c4…
 *    del 15/10/2026, 8a91… del 30/09/2026) se conservan para verificar licencias ya emitidas.
 *    Ninguna de ellas sirve para cifrar solicitudes: para eso solo vale [ECDH_KEY].
 * La huella se pega TAL CUAL la copia GL; también se admite hex plano. LicenseTrustTest
 * comprueba que las claves parsean como P-256 y que cada huella coincide.
 *
 * ATENCIÓN: GL genera el par en el Keystore de SU teléfono. Si se borran los datos de GL (`pm clear`), se
 * reinstala o se cambia de teléfono, sale otro par y hay que publicar un build nuevo:
 *  - Firma: AÑADIR la clave nueva a [SIGN_KEYS] SIN borrar la antigua, para que las licencias ya emitidas
 *    sigan verificando.
 *  - ECDH: SUSTITUIR [ECDH_KEY] (las solicitudes nuevas deben poder abrirse con el par nuevo).
 * Ambas cambian a la vez, así que fijar también la ECDH no añade builds extra.
 */
object LicenseTrust {

    data class PinnedKey(val spkiB64: String, val sha256: String)

    // ECDH vigente del emisor (GL 1.2.0 en el teléfono 4bc9f8f3, leída el 2026-10-22).
    // Es la única con la que se cifran las solicitudes: si GL cambia de par, hay que sustituirla.
    val ECDH_KEY = PinnedKey(
        spkiB64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE4UHhdTzS3X/2pk5wmEAibv760EBcnxDjERo8km4OCdIhVDc5D+N0yPQlq6UpiXV1exXVmMfD6vqezlNsYFEIYg==",
        sha256 = "sha256:fb3a:f7fc:e8a2:e86d:4e83:5351:d677:6aec:a9b6:5071:21d7:a417:9cd7:4b1b:0a53:078d",
    )

    // SPKI de firma GL: el del emisor vigente primero y después cada rotación conocida,
    // para verificar también las licencias emitidas con las anteriores.
    val SIGN_KEYS: List<PinnedKey> = listOf(
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

    /** DER validado o null si falta o no coincide con la huella. */
    fun ecdhSpki(): ByteArray? = ECDH_KEY.validated()

    fun signSpkis(): List<ByteArray> = SIGN_KEYS.mapNotNull { it.validated() }

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
