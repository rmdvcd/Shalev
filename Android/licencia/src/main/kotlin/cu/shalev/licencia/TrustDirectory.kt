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

    /**
     * Bloque opcional de canje del directorio (Anexo A / B). Solo contiene datos públicos:
     * la base HTTPS autorizada del servicio GL y el mapa de `appId` por producto.
     */
    @Serializable
    data class CanjeDto(
        val endpoint_base: String = "",
        val app_ids: Map<String, String> = emptyMap(),
    )

    @Serializable
    data class DatosDto(
        val v: Int,
        val seq: Int,
        val emitidaEn: Long,
        val claves: List<ClaveDto>,
        val canje: CanjeDto? = null,
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
        /** Base HTTPS autorizada del servicio de canje (`canje.endpoint_base`); vacía = canje cerrado. */
        val endpointBase: String = "",
        /** `appId` autorizados por producto (`canje.app_ids`). Vacío = usar el del build. */
        val appIds: Map<String, String> = emptyMap(),
    ) {
        fun signKeysFor(emitidaEn: Instant?): List<ByteArray> {
            val seg = emitidaEn?.epochSecond ?: Instant.now().epochSecond
            return claves.filter { it.uso == "firma" && it.cubreFecha(seg) }.map { it.spkiDer }
        }

        fun ecdhVigente(): ByteArray? =
            claves.firstOrNull { it.uso == "ecdh" && it.estado == "vigente" }?.spkiDer

        fun redeemKey(kid: String): ByteArray? =
            claves.firstOrNull { it.uso == "canje" && it.kid == kid && it.activa }?.spkiDer

        fun canjeKeys(): List<ByteArray> =
            claves.filter { it.uso == "canje" && it.activa }.map { it.spkiDer }

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

    var effectiveDirectory: Directorio?
        get() = getDirectorioEfectivo()
        set(value) = setDirectorioEfectivo(value)

    fun parseAndVerify(contenido: String): Directorio = parsear(contenido)

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
            endpointBase = datos.canje?.endpoint_base.orEmpty().trimEnd('/'),
            appIds = datos.canje?.app_ids.orEmpty(),
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

    const val URL_DIRECTORIO_OFICIAL = "https://github.com/rmdvcd/Shalev/releases/download/revocaciones/directorio.json"
    const val HOST_PERMITIDO = "github.com"
}

/**
 * Gestor del ciclo de vida del directorio de confianza en Android (Fase 1.3).
 */
object TrustDirectoryManager {
    var cacheFile: java.io.File? = null

    fun loadEffective(cache: java.io.File? = cacheFile): TrustDirectory.Directorio? {
        val bootstrap = try {
            val stream = TrustDirectory::class.java.getResourceAsStream("/directorio-bootstrap.json")
            val text = stream?.bufferedReader()?.use { it.readText() }
            if (text != null) TrustDirectory.parseAndVerify(text) else null
        } catch (_: Exception) {
            null
        }

        val cached = try {
            if (cache != null && cache.isFile) {
                TrustDirectory.parseAndVerify(cache.readText(Charsets.UTF_8))
            } else null
        } catch (_: Exception) {
            null
        }

        var result = bootstrap
        if (cached != null) {
            try {
                result = TrustDirectory.aplicarAntiRetroceso(bootstrap, cached)
            } catch (_: Exception) { }
        }
        TrustDirectory.effectiveDirectory = result
        return result
    }

    fun updateFromNetwork(
        cache: java.io.File? = cacheFile,
        urlString: String = TrustDirectory.URL_DIRECTORIO_OFICIAL,
    ): Boolean {
        try {
            val url = java.net.URI(urlString).toURL()
            if (url.protocol != "https" || url.host != TrustDirectory.HOST_PERMITIDO) return false
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.instanceFollowRedirects = true
            conn.connect()
            if (conn.responseCode != 200) return false
            val bytes = conn.inputStream.use { it.readNBytes(TrustDirectory.MAX_SIZE_BYTES + 1) }
            if (bytes.size > TrustDirectory.MAX_SIZE_BYTES) return false
            val text = String(bytes, Charsets.UTF_8)
            val nuevo = TrustDirectory.parseAndVerify(text)
            synchronized(this) {
                val actual = TrustDirectory.effectiveDirectory ?: loadEffective(cache)
                val actualizado = TrustDirectory.aplicarAntiRetroceso(actual, nuevo)
                if (cache != null) {
                    val tmp = java.io.File(cache.parentFile, cache.name + ".tmp")
                    tmp.writeText(text, Charsets.UTF_8)
                    tmp.renameTo(cache)
                }
                TrustDirectory.effectiveDirectory = actualizado
            }
            return true
        } catch (_: Exception) {
            return false
        }
    }
}
