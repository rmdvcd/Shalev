package cu.shalev.licencia

import cu.shalev.licencia.crypto.B64
import cu.shalev.licencia.crypto.EcP256
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.time.Instant

/**
 * Directorio de confianza v1 (Anexo A de IMPLEMENTACION_SHALEV.md).
 * Autenticado mediante claves raíz fijadas en el build (`RAIZ_A_SPKI`, `RAIZ_B_SPKI`).
 * Permite actualizar claves de firma, ECDH y canje sin recompilar la app.
 */
object TrustDirectory {
    const val DOMINIO_PREFIJO = "SPVI-DIR1|"
    const val MAX_SIZE_BYTES = 64 * 1024 // 64 KiB
    const val VERSION = 1

    // Marcadores para las raíces fijadas en compilación.
    // Mientras sean marcadores vacíos, las funciones de producción fallan cerrado.
    const val RAIZ_A_SPKI: String = ""
    const val RAIZ_B_SPKI: String = ""

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Serializable
    data class Envelope(
        val datos: String,
        val firma: String,
        val raiz: String? = null,
    )

    @Serializable
    data class ClaveDto(
        val kid: String,
        val uso: String,
        val spki: String,
        val huella: String,
        val desde: Long,
        val hasta: Long? = null,
        val estado: String,
    )

    @Serializable
    data class DatosDto(
        val v: Int,
        val seq: Int,
        val emitidaEn: Long,
        val claves: List<ClaveDto>,
    )

    data class Clave(
        val kid: String,
        val uso: String,
        val spkiDer: ByteArray,
        val huella: String,
        val desde: Long,
        val hasta: Long?,
        val estado: String,
    ) {
        val activa: Boolean get() = estado != "comprometida"

        fun cubreFecha(segundos: Long): Boolean {
            if (estado == "comprometida") return false
            if (segundos < desde) return false
            if (hasta != null && segundos > hasta) return false
            return true
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Clave) return false
            return kid == other.kid && uso == other.uso && spkiDer.contentEquals(other.spkiDer) && estado == other.estado
        }

        override fun hashCode(): Int = kid.hashCode() * 31 + spkiDer.contentHashCode()
    }

    data class Directorio(
        val v: Int,
        val seq: Int,
        val emitidaEn: Long,
        val claves: List<Clave>,
    ) {
        fun signKeysFor(emitidaEn: Instant?): List<ByteArray> {
            val seg = emitidaEn?.epochSecond ?: Instant.now().epochSecond
            return claves.filter { it.uso == "firma" && it.cubreFecha(seg) }.map { it.spkiDer }
        }

        fun ecdhVigente(): ByteArray? =
            claves.firstOrNull { it.uso == "ecdh" && it.estado == "vigente" }?.spkiDer

        fun redeemKey(kid: String): ByteArray? =
            claves.firstOrNull { it.uso == "canje" && it.kid == kid && it.activa }?.spkiDer

        fun esComprometida(spkiDer: ByteArray): Boolean =
            claves.any { it.spkiDer.contentEquals(spkiDer) && it.estado == "comprometida" }
    }

    private var directorioEfectivo: Directorio? = null

    @Synchronized
    fun setDirectorioEfectivo(dir: Directorio?) {
        directorioEfectivo = dir
    }

    @Synchronized
    fun getDirectorioEfectivo(): Directorio? = directorioEfectivo

    fun effectiveEcdhSpki(): ByteArray? =
        directorioEfectivo?.ecdhVigente()

    fun effectiveSignSpkis(emitidaEn: Instant? = null): List<ByteArray> =
        directorioEfectivo?.signKeysFor(emitidaEn) ?: emptyList()

    fun effectiveRedeemKey(kid: String): ByteArray? =
        directorioEfectivo?.redeemKey(kid)

    fun raicesCompilacion(): List<ByteArray> {
        val list = mutableListOf<ByteArray>()
        for (b64 in listOf(RAIZ_A_SPKI, RAIZ_B_SPKI)) {
            if (b64.isNotBlank()) {
                val der = runCatching { EcP256.decodeSpkiText(b64) }.getOrNull()
                if (der != null && runCatching { EcP256.publicKey(der) }.isSuccess) {
                    list.add(der)
                }
            }
        }
        return list
    }

    /**
     * Valida y parsea el contenido de `directorio.json`.
     * Falla si la firma no corresponde con ninguna de las claves raíz dadas.
     */
    fun parsear(
        contenido: String,
        raices: List<ByteArray> = raicesCompilacion(),
    ): Directorio {
        val bytes = contenido.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_SIZE_BYTES) { "Directorio excede 64 KiB" }

        val envelope = runCatching { json.decodeFromString<Envelope>(contenido) }
            .getOrElse { throw IllegalArgumentException("Sobre inválido", it) }
        require(envelope.datos.isNotBlank() && envelope.firma.isNotBlank()) { "Campos datos/firma faltantes" }

        require(raices.isNotEmpty()) { "Sin claves raíz autorizadas" }

        val rawSig = runCatching { B64.urlDec(envelope.firma.trim()) }.getOrElse {
            throw IllegalArgumentException("Firma no es Base64url", it)
        }
        require(rawSig.size == 64) { "Firma debe ser r||s de 64 bytes" }
        val derSig = EcP256.rawToDer(rawSig)

        val mensaje = (DOMINIO_PREFIJO + envelope.datos).toByteArray(Charsets.UTF_8)
        val verificada = raices.any { raizDer ->
            val pub = runCatching { EcP256.publicKey(raizDer) }.getOrNull() ?: return@any false
            EcP256.verify(pub, mensaje, derSig)
        }
        require(verificada) { "Firma del directorio no verificó con ninguna raíz" }

        val datos = runCatching { json.decodeFromString<DatosDto>(envelope.datos) }
            .getOrElse { throw IllegalArgumentException("Datos del directorio inválidos", it) }

        require(datos.v == VERSION) { "Versión no soportada: ${datos.v}" }
        require(datos.seq >= 0) { "seq debe ser >= 0" }
        require(datos.emitidaEn > 0) { "emitidaEn inválida" }

        var ecdhVigenteCount = 0
        val claves = datos.claves.map { c ->
            require(c.uso in setOf("firma", "ecdh", "canje")) { "Uso inválido: ${c.uso}" }
            require(c.estado in setOf("vigente", "retirada", "comprometida")) { "Estado inválido: ${c.estado}" }
            val spkiDer = EcP256.decodeSpkiText(c.spki)
            EcP256.publicKey(spkiDer) // Valida que sea P-256

            val fp = EcP256.fingerprint(spkiDer)
            val normalizada = LicenseTrust.normalizarHuella(c.huella)
            require(fp == normalizada) { "Huella no coincide para ${c.kid}" }

            if (c.uso == "ecdh" && c.estado == "vigente") {
                ecdhVigenteCount++
                require(ecdhVigenteCount <= 1) { "Más de una clave ECDH vigente" }
            }

            Clave(
                kid = c.kid,
                uso = c.uso,
                spkiDer = spkiDer,
                huella = c.huella,
                desde = c.desde,
                hasta = c.hasta,
                estado = c.estado,
            )
        }

        return Directorio(
            v = datos.v,
            seq = datos.seq,
            emitidaEn = datos.emitidaEn,
            claves = claves,
        )
    }

    /**
     * Aplica anti-retroceso: rechaza `nuevo` si `seq < actual.seq`.
     * Si `seq == actual.seq`, es idempotente (acepta si idéntico).
     */
    fun aplicarAntiRetroceso(actual: Directorio?, nuevo: Directorio): Directorio {
        if (actual == null) return nuevo
        if (nuevo.seq < actual.seq) {
            throw IllegalArgumentException("Anti-retroceso: seq ${nuevo.seq} < ${actual.seq}")
        }
        return nuevo
    }
}
