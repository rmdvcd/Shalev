package cu.shalev.licencia.contract

import cu.shalev.core.util.aHex
import cu.shalev.licencia.crypto.EcP256
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 0.25.0 (Prompt 69): **lista pública de licencias revocadas**, publicada por GL como recurso de la Release
 * `revocaciones` del repositorio de GitHub de Shalev (`revocadas.json`). Sirve para que el teléfono ANTIGUO se entere de
 * que su licencia se recuperó en otro (no hay servidor: es la única vía, y solo si se conecta a internet).
 *
 * Archivo (JSON): `{"datos":"<JSON de Datos como texto>","firma":"<Base64url r‖s>"}`.
 * - La firma (ECDSA P-256 / SHA-256, clave `gl-sign-v1`, en crudo r‖s) cubre `UTF-8("SPVI-REV1|") ‖ UTF-8(datos)`:
 *   se firma el TEXTO exacto, así no hace falta canonicalizar JSON.
 * - `Datos`: `{"v":1,"emitidaEn":<segundos Unix>,"revocadas":["<32 hex>", …]}`.
 * - Cada entrada = primeros 16 B (en hex minúsculas) de `SHA-256(UTF-8("SPVI-REV|" + licenseId en minúsculas))`.
 *   No contiene nombres, carnés, teléfonos ni ids en claro.
 *
 * Un archivo sin firma válida de GL, con otro formato o corrupto se IGNORA (nunca revoca nada).
 */
object ListaRevocaciones {
    const val DOMINIO_FIRMA = "SPVI-REV1|"
    const val DOMINIO_HUELLA = "SPVI-REV|"
    const val VERSION = 1
    private const val MAX_BYTES = 512 * 1024

    @Serializable
    data class Archivo(val datos: String, val firma: String)

    @Serializable
    data class Datos(val v: Int = VERSION, val emitidaEn: Long, val revocadas: List<String> = emptyList())

    private val json = Json { ignoreUnknownKeys = true }

    fun huella(licenseId: String): String =
        MessageDigest.getInstance("SHA-256").digest((DOMINIO_HUELLA + licenseId.trim().lowercase()).toByteArray(Charsets.UTF_8))
            .copyOf(16).aHex()

    /** Datos auténticos de la lista, o null si no verifica con ninguna de [claves]. */
    fun verificar(texto: String, claves: List<PublicKey>): Datos? = runCatching {
        if (texto.length > MAX_BYTES) return null
        val a = json.decodeFromString(Archivo.serializer(), texto)
        val firma = Base64.getUrlDecoder().decode(a.firma.trim())
        val der = EcP256.rawToDer(firma)
        val mensaje = (DOMINIO_FIRMA + a.datos).toByteArray(Charsets.UTF_8)
        if (claves.none { EcP256.verify(it, mensaje, der) }) return null
        json.decodeFromString(Datos.serializer(), a.datos).takeIf { d ->
            d.v == VERSION && d.revocadas.all { it.length == 32 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
        }
    }.getOrNull()

    /** ¿Está [licenseId] en la lista? */
    fun contiene(datos: Datos, licenseId: String): Boolean = huella(licenseId) in datos.revocadas

    /** Emisor de referencia (lo que hace GL; aquí, tests y `probar_gl.py`). [firmarDer] = ECDSA DER con gl-sign-v1. */
    fun firmar(datos: Datos, firmarDer: (ByteArray) -> ByteArray): String {
        val texto = Json.encodeToString(Datos.serializer(), datos)
        val raw = EcP256.derToRaw(firmarDer((DOMINIO_FIRMA + texto).toByteArray(Charsets.UTF_8)))
        return Json.encodeToString(Archivo.serializer(), Archivo(texto, Base64.getUrlEncoder().withoutPadding().encodeToString(raw)))
    }
}

/**
 * 0.25.0: id de licencia (UUID) dentro de un texto: el mensaje de GL («ID: 7c9e…»), solo el id, o cualquier texto que lo
 * contenga. Si hay varios, se prefiere el que sigue a «ID:». null = no hay ninguno. Devuelve la forma canónica.
 */
object IdLicencia {
    private val UUID_RE = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val TRAS_ID = Regex("ID\\s*:\\s*(" + UUID_RE.pattern + ")", RegexOption.IGNORE_CASE)

    fun extraer(texto: String): String? =
        (TRAS_ID.find(texto)?.groupValues?.get(1) ?: UUID_RE.find(texto)?.value)?.lowercase()
}
