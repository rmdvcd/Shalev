package cu.shalev.licencia.contract

import cu.shalev.core.money.Money
import cu.shalev.licencia.crypto.AesGcm
import cu.shalev.licencia.crypto.EcP256
import cu.shalev.licencia.crypto.Hkdf
import cu.shalev.licencia.crypto.fail
import java.security.KeyPair
import java.security.PrivateKey
import java.security.interfaces.ECPublicKey
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64

/**
 * 0.23.0: **solicitud cifrada** compacta (Shalev → GL). Sustituye al envelope JSON v1 de la solicitud: ocupa unos 500
 * caracteres en vez de ~1 000, así que el mensaje completo (texto introductorio + solicitud) cabe como pie de la imagen
 * del QR en WhatsApp y el QR es legible.
 *
 * Texto: `SPVIR1:` + Base64url sin relleno de `epk (33 B) ‖ ct ‖ tag (16 B)`.
 * - `epk`: clave pública EFÍMERA P-256 de Shalev, punto comprimido SEC1 (nueva en cada solicitud).
 * - `okm = HKDF-SHA256(ikm = ECDH(efímera, ECDH de GL), salt = epk, info = "SPVI-R1", 44 B)` → clave AES-256 (32 B) ‖ IV (12 B).
 *   El IV sale del HKDF: la efímera es nueva cada vez, así que (clave, IV) nunca se repite.
 * - AES-256-GCM, AAD = `"SPVI-R1|"` ‖ epk, tag de 16 B. Texto plano = JSON de [RequestPayload] (UTF-8, `v = 2`).
 *
 * Sin firma de la efímera (la del envelope v1 no autenticaba a nadie: cualquiera puede crear una efímera). La
 * confidencialidad y la integridad las da GCM con la clave ECDH de GL fijada en el build.
 */
object SolicitudCifrada {
    const val PREFIJO = "SPVIR1:"
    const val INFO = "SPVI-R1"
    private const val LARGO_EPK = 33
    private const val LARGO_TAG = 16

    fun aad(epk: ByteArray): ByteArray = "$INFO|".toByteArray(Charsets.UTF_8) + epk

    private fun claveIv(ecdh: ByteArray, epk: ByteArray): Pair<ByteArray, ByteArray> {
        val okm = Hkdf.sha256(ecdh, epk, INFO.toByteArray(Charsets.UTF_8), 44)
        ecdh.fill(0)
        return okm.copyOfRange(0, 32) to okm.copyOfRange(32, 44)
    }

    /** Cifra [plano] hacia la clave ECDH de GL. */
    fun sellar(plano: ByteArray, glEcdh: ECPublicKey, efimera: KeyPair = EcP256.generate()): String {
        val epk = EcP256.comprimir(efimera.public as ECPublicKey)
        val (clave, iv) = claveIv(EcP256.ecdh(efimera.private, glEcdh), epk)
        val ct = AesGcm.encrypt(clave, iv, aad(epk), plano)
        clave.fill(0)
        return PREFIJO + Base64.getUrlEncoder().withoutPadding().encodeToString(epk + ct)
    }

    /** Lo que hace GL (y el emisor simulado de los tests): descifra con su clave privada ECDH. Falla si no es auténtica. */
    fun abrir(texto: String, glEcdhPrivada: PrivateKey): ByteArray {
        val datos = extraer(texto) ?: fail()
        if (datos.size <= LARGO_EPK + LARGO_TAG) fail()
        val epk = datos.copyOfRange(0, LARGO_EPK)
        val (clave, iv) = claveIv(EcP256.ecdh(glEcdhPrivada, EcP256.descomprimir(epk)), epk)
        return AesGcm.decrypt(clave, iv, aad(epk), datos.copyOfRange(LARGO_EPK, datos.size)).also { clave.fill(0) }
    }

    /**
     * Busca `SPVIR1:` en un mensaje y toma los caracteres Base64url que le siguen, ignorando espacios y saltos de línea
     * (la solicitud va al final del mensaje). null = no hay solicitud.
     */
    fun extraer(texto: String): ByteArray? {
        val i = texto.indexOf(PREFIJO).takeIf { it >= 0 } ?: return null
        // Tolerante al transporte (especificación GL): WhatsApp/SMS pueden partir la línea e insertar espacios o saltos.
        val b64 = buildString {
            for (c in texto.substring(i + PREFIJO.length)) {
                when {
                    c.isLetterOrDigit() && c.code < 128 || c == '-' || c == '_' -> append(c)
                    c.isWhitespace() -> Unit
                    else -> break
                }
            }
        }
        return runCatching { Base64.getUrlDecoder().decode(b64) }.getOrNull()?.takeIf { it.size > LARGO_EPK + LARGO_TAG }
    }
}

/**
 * 0.23.0: textos de los mensajes. Formato pedido: **texto introductorio + un renglón en blanco + parte cifrada**.
 * La parte de arriba es solo para leer: Shalev y GL usan únicamente el código (`SPVIR1:` / `SPVI2:`).
 */
object MensajesLicencia {
    /**
     * GL PR #3 (2026-10-14, commit `005288d`): la emisión Shalev2 ahora exige frescura exacta de la solicitud
     * (≤ 72 h de antigüedad y ≤ 2 h de adelanto contra el reloj del emisor; antes el flujo corto no la aplicaba).
     * Esta línea va en la parte legible del mensaje para que cliente y operador lo sepan antes de pagar.
     */
    const val AVISO_FRESCURA = "Vale 72 horas: envíala y paga cuanto antes; si pasa ese tiempo, genera otra solicitud."

    /** Solicitud: datos del solicitante y de lo que pide (todos en claro, decisión del Prompt 64). */
    fun solicitud(p: RequestPayload, codigo: String, secundariasPrevias: Int = 0): String = when {
        p.recupera != null -> recuperacion(p, codigo)
        p.amplia != null -> ampliacion(p, codigo, secundariasPrevias)
        else -> buildString {
            appendLine("Solicitud de licencia Shalev")
            appendLine("Nombre: ${p.nombre} ${p.apellidos}")
            appendLine("Carné de identidad: ${p.ci}")
            appendLine("Teléfono: ${p.telefono}")
            appendLine("Tipo: ${p.tipo.etiqueta}")
            appendLine("Apps secundarias: ${p.secundarias}")
            appendLine("Precio: ${Money.cup(p.tipo.precio(p.secundarias))}")
            p.renueva?.let { appendLine("Renueva: $it") }
            appendLine(AVISO_FRESCURA)
            appendLine()
            append(codigo)
        }
    }

    /** Solicitud de ampliación de apps secundarias sobre una licencia activa o perpetua (conserva su vencimiento). */
    fun ampliacion(p: RequestPayload, codigo: String, secundariasPrevias: Int = 0): String {
        val previas = secundariasPrevias.coerceIn(0, (p.secundarias - 1).coerceAtLeast(0))
        val adicionales = (p.secundarias - previas).coerceAtLeast(1)
        return buildString {
            appendLine("Ampliar secundarias Shalev")
            appendLine("Nombre: ${p.nombre} ${p.apellidos}")
            appendLine("Carné de identidad: ${p.ci}")
            appendLine("Teléfono: ${p.telefono}")
            appendLine("Tipo: ${p.tipo.etiqueta}")
            appendLine("Apps secundarias actuales: $previas")
            appendLine("Secundarias adicionales: $adicionales")
            appendLine("Apps secundarias: ${p.secundarias}")
            appendLine("Precio: ${Money.cup(p.tipo.precioAmpliacion(adicionales))}")
            appendLine("Amplía: ${p.amplia}")
            appendLine(AVISO_FRESCURA)
            appendLine()
            append(codigo)
        }
    }

    /** 0.25.0: solicitud de recuperación (gratis: tipo, vencimiento y secundarias los decide GL con sus registros). */
    fun recuperacion(p: RequestPayload, codigo: String): String = buildString {
        appendLine("Recuperar licencia Shalev")
        appendLine("Nombre: ${p.nombre} ${p.apellidos}")
        appendLine("Carné de identidad: ${p.ci}")
        appendLine("Teléfono: ${p.telefono}")
        appendLine("Licencia anterior: ${p.recupera}")
        appendLine(AVISO_FRESCURA)
        appendLine()
        append(codigo)
    }

    /**
     * Licencia (lo escribe GL; aquí es la REFERENCIA para GL y los tests): características + renglón en blanco + código.
     * Sin datos personales y sin tildes salvo las del alfabeto GSM-7, para que por SMS no pase a UCS-2.
     * Shalev no lee estas líneas: todo lo que cuenta va cifrado y firmado en el código.
     */
    fun licencia(c: LicenciaCorta.Campos, codigo: String, zona: ZoneId = ZONA_CUBA): String = buildString {
        val dia = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(zona)
        appendLine("Licencia Shalev")
        appendLine("Tipo: ${c.tipo.etiqueta}")
        appendLine("Apps secundarias: ${c.secundarias}")
        appendLine("Emitida: ${dia.format(c.emitidaEn)}")
        appendLine("Vence: ${c.venceEn?.let(dia::format) ?: "nunca"}")
        appendLine("ID: ${c.id}")
        appendLine()
        append(codigo)
    }

    val ZONA_CUBA: ZoneId = ZoneId.of("America/Havana")
}
