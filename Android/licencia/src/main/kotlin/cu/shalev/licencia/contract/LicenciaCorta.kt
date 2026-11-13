package cu.shalev.licencia.contract

import cu.shalev.licencia.crypto.AesGcm
import cu.shalev.licencia.crypto.DeviceKey
import cu.shalev.licencia.crypto.EcP256
import cu.shalev.licencia.crypto.Hkdf
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.MessageDigest
import java.security.interfaces.ECPublicKey
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * 0.23.0: **licencia corta cifrada**, la ÚNICA forma que emite GL y que Shalev acepta al activar (Prompt 64). Sustituye
 * a la corta firmada de la 0.22.0 (`SPVI1:`, nunca emitida por GL) y a la licencia larga (envelope v1), que solo se
 * sigue re-verificando si ya estaba instalada.
 *
 * Texto: `SPVI2:` + Base64url sin relleno (210 caracteres) de `epk (33 B) ‖ ct (44 B) ‖ tag (16 B) ‖ firma (64 B)`
 * = **216 caracteres** GSM-7 (2 SMS si va sola).
 *
 * Cuerpo en claro (44 B, big-endian), lo que va cifrado en `ct`:
 * | byte | campo |
 * |---|---|
 * | 0 | formato = 2 |
 * | 1–16 | licenseId (UUID, 16 B) |
 * | 17–32 | huella del dispositivo = SHA-256(`"SPVI-L2|" + deviceId + "|"` UTF-8 ‖ devicePub comprimida 33 B)[0..16] |
 * | 33 | tipo: 0 MENSUAL, 1 SEMESTRAL, 2 ANUAL, 3 PERPETUA |
 * | 34 | estado: 0 ACTIVA, 1 VENCIDA, 2 REVOCADA, 3 PERPETUA |
 * | 35 | secundarias (0..10) |
 * | 36–39 | emitidaEn, segundos Unix (uint32) |
 * | 40–43 | venceEn, segundos Unix (uint32); 0 = sin vencimiento |
 *
 * Cifrado (GL → este dispositivo):
 * - `epk`: clave efímera P-256 de GL, punto comprimido (nueva en cada licencia).
 * - `okm = HKDF-SHA256(ikm = ECDH(efímera, devicePub), salt = epk, info = "SPVI-L2", 44 B)` → clave AES-256 ‖ IV (12 B).
 * - AES-256-GCM, AAD = `"SPVI-L2|"` ‖ epk.
 *
 * Firma (cifrar y después firmar, como el envelope v1): ECDSA P-256 / SHA-256 con la clave de firma de GL
 * (`gl-sign-v1`) sobre `"SPVI-L2|"` ‖ epk ‖ ct ‖ tag, en crudo r‖s (32 + 32 B). Así otro teléfono puede comprobar
 * que es de GL SIN descifrarla (Migrar), y el dominio impide reutilizar firmas de otros formatos.
 *
 * Seguridad: solo GL puede emitirla (firma fijada en el build); solo este teléfono puede leerla (la privada del
 * Keystore); un byte cambiado → rechazo; la huella cifrada ata además el contenido a este deviceId y esta clave.
 */
object LicenciaCorta {
    const val PREFIJO = "SPVI2:"
    const val FORMATO: Byte = 2
    const val DOMINIO = "SPVI-L2|"
    const val INFO = "SPVI-L2"
    const val LARGO_CUERPO = 44
    const val LARGO_EPK = 33
    const val LARGO_TAG = 16
    const val LARGO_CIFRADO = LARGO_CUERPO + LARGO_TAG // ct ‖ tag
    const val LARGO_FIRMA = 64
    const val LARGO_HUELLA = 16
    const val LARGO_BINARIO = LARGO_EPK + LARGO_CIFRADO + LARGO_FIRMA // 157 B
    const val LARGO_BASE64 = 210 // ⌈157 × 4 / 3⌉, sin relleno
    const val LARGO_TEXTO = 216 // con el prefijo

    /** Orden fijo del contrato (no depende del orden de los enums). */
    val TIPOS = listOf(TipoLicencia.MENSUAL, TipoLicencia.SEMESTRAL, TipoLicencia.ANUAL, TipoLicencia.PERPETUA)
    val ESTADOS = listOf(EstadoLicencia.ACTIVA, EstadoLicencia.VENCIDA, EstadoLicencia.REVOCADA, EstadoLicencia.PERPETUA)

    data class Campos(
        val id: String,
        val huella: ByteArray,
        val tipo: TipoLicencia,
        val estado: EstadoLicencia,
        val secundarias: Int,
        val emitidaEn: Instant,
        val venceEn: Instant?,
    ) {
        override fun equals(other: Any?) = other is Campos && id == other.id && huella.contentEquals(other.huella) &&
            tipo == other.tipo && estado == other.estado && secundarias == other.secundarias &&
            emitidaEn == other.emitidaEn && venceEn == other.venceEn
        override fun hashCode() = id.hashCode()
    }

    /** Código encontrado en un texto: [texto] es la forma canónica (sin espacios) que se guarda. */
    class Codigo(val epk: ByteArray, val cifrado: ByteArray, val firma: ByteArray) {
        val texto: String get() = PREFIJO + Base64.getUrlEncoder().withoutPadding().encodeToString(epk + cifrado + firma)
        /** Lo que firma GL: dominio ‖ epk ‖ ct ‖ tag. */
        val mensajeFirmado: ByteArray get() = DOMINIO.toByteArray(Charsets.UTF_8) + epk + cifrado
    }

    /** [devicePubComprimida]: los 33 B del punto comprimido (lo que GL recibe en `devicePub`). */
    fun huella(deviceId: String, devicePubComprimida: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").run {
            update("$DOMINIO$deviceId|".toByteArray(Charsets.UTF_8)); update(devicePubComprimida); digest()
        }.copyOf(LARGO_HUELLA)

    fun aad(epk: ByteArray): ByteArray = DOMINIO.toByteArray(Charsets.UTF_8) + epk

    private fun claveIv(ecdh: ByteArray, epk: ByteArray): Pair<ByteArray, ByteArray> {
        val okm = Hkdf.sha256(ecdh, epk, INFO.toByteArray(Charsets.UTF_8), 44)
        ecdh.fill(0)
        return okm.copyOfRange(0, 32) to okm.copyOfRange(32, 44)
    }

    /** Codifica los campos (lo usa el emisor: GL; en Shalev, los tests y la referencia). */
    fun cuerpo(c: Campos): ByteArray {
        require(c.huella.size == LARGO_HUELLA && c.secundarias in 0..255)
        val uuid = UUID.fromString(c.id)
        return ByteBuffer.allocate(LARGO_CUERPO)
            .put(FORMATO)
            .putLong(uuid.mostSignificantBits).putLong(uuid.leastSignificantBits)
            .put(c.huella)
            .put(TIPOS.indexOf(c.tipo).toByte())
            .put(ESTADOS.indexOf(c.estado).toByte())
            .put(c.secundarias.toByte())
            .putInt(segundos(c.emitidaEn))
            .putInt(c.venceEn?.let(::segundos) ?: 0)
            .array()
    }

    /** null = cuerpo mal formado (formato, tipo o estado desconocidos, o tamaño incorrecto). */
    fun leer(cuerpo: ByteArray): Campos? {
        if (cuerpo.size != LARGO_CUERPO || cuerpo[0] != FORMATO) return null
        val b = ByteBuffer.wrap(cuerpo, 1, LARGO_CUERPO - 1)
        val id = UUID(b.long, b.long).toString()
        val huella = ByteArray(LARGO_HUELLA).also { b.get(it) }
        val tipo = TIPOS.getOrNull(b.get().toInt() and 0xFF) ?: return null
        val estado = ESTADOS.getOrNull(b.get().toInt() and 0xFF) ?: return null
        val secundarias = b.get().toInt() and 0xFF
        val emitida = Instant.ofEpochSecond(b.int.toLong() and 0xFFFF_FFFFL)
        val vence = (b.int.toLong() and 0xFFFF_FFFFL).takeIf { it != 0L }?.let(Instant::ofEpochSecond)
        return Campos(id, huella, tipo, estado, secundarias, emitida, vence)
    }

    /**
     * Emisor de referencia (lo que hace GL): cifra [c] hacia [devicePub] y firma con [firmarDer] (ECDSA DER de la
     * clave de firma de GL). Devuelve el código `SPVI2:…`.
     */
    fun emitir(c: Campos, devicePub: ECPublicKey, firmarDer: (ByteArray) -> ByteArray, efimera: KeyPair = EcP256.generate()): String {
        val epk = EcP256.comprimir(efimera.public as ECPublicKey)
        val (clave, iv) = claveIv(EcP256.ecdh(efimera.private, devicePub), epk)
        val cifrado = AesGcm.encrypt(clave, iv, aad(epk), cuerpo(c)).also { clave.fill(0) }
        val sinFirma = Codigo(epk, cifrado, ByteArray(LARGO_FIRMA))
        return Codigo(epk, cifrado, EcP256.derToRaw(firmarDer(sinFirma.mensajeFirmado))).texto
    }

    /** Descifra con la clave del dispositivo (el ECDH ocurre en el Keystore). Falla (genérico) si no es para esta clave. */
    fun descifrar(codigo: Codigo, deviceKey: DeviceKey): ByteArray {
        val (clave, iv) = claveIv(deviceKey.agree(EcP256.descomprimir(codigo.epk)), codigo.epk)
        return AesGcm.decrypt(clave, iv, aad(codigo.epk), codigo.cifrado).also { clave.fill(0) }
    }

    /**
     * Busca `SPVI2:` en un mensaje (WhatsApp, SMS o un QR) y toma los 210 caracteres Base64url siguientes, ignorando
     * espacios y saltos de línea que algunas apps insertan al partir líneas. null = no hay código completo.
     */
    fun extraer(texto: String): Codigo? {
        var desde = texto.indexOf(PREFIJO)
        while (desde >= 0) {
            val sb = StringBuilder(LARGO_BASE64)
            var i = desde + PREFIJO.length
            while (i < texto.length && sb.length < LARGO_BASE64) {
                val c = texto[i]
                when {
                    c.isLetterOrDigit() && c.code < 128 || c == '-' || c == '_' -> sb.append(c)
                    c.isWhitespace() -> Unit
                    else -> break
                }
                i++
            }
            if (sb.length == LARGO_BASE64) {
                val b = runCatching { Base64.getUrlDecoder().decode(sb.toString()) }.getOrNull()
                if (b != null && b.size == LARGO_BINARIO) {
                    return Codigo(
                        b.copyOfRange(0, LARGO_EPK),
                        b.copyOfRange(LARGO_EPK, LARGO_EPK + LARGO_CIFRADO),
                        b.copyOfRange(LARGO_EPK + LARGO_CIFRADO, LARGO_BINARIO),
                    )
                }
            }
            desde = texto.indexOf(PREFIJO, desde + 1)
        }
        return null
    }

    private fun segundos(i: Instant): Int {
        require(i.epochSecond in 1..0xFFFF_FFFFL)
        return i.epochSecond.toInt()
    }
}
