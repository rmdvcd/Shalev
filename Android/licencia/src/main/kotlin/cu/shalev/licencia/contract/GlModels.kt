package cu.shalev.licencia.contract

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Envelope v1: idéntico a la ida y a la vuelta. */
@Serializable
data class Envelope(
    val v: Int,
    val alg: String,
    val epk: String,
    val iv: String,
    val ct: String,
    val tag: String,
    val sig: String,
    val kid: String,
)

@Serializable
enum class Via { WHATSAPP, SMS }

@Serializable
enum class TipoLicencia(val etiqueta: String, val precioCup: Long, val precioSecundariaCup: Long) {
    MENSUAL("Mensual", 6_000, 1_000),
    SEMESTRAL("Semestral", 30_000, 5_000),
    ANUAL("Anual", 50_000, 9_000),
    PERPETUA("Perpetua", 90_000, 17_000),
    ;

    /** 0.21.0 (C4): precio base + un importe fijo por cada app secundaria (P46). */
    fun precio(secundarias: Int): Long = precioCup + secundarias.coerceAtLeast(0) * precioSecundariaCup

    /** Tarifa fija por ampliar apps secundarias adicionales sobre una licencia vigente del mismo tipo. */
    fun precioAmpliacion(adicionales: Int): Long = adicionales.coerceAtLeast(0) * precioSecundariaCup
}

@Serializable
enum class EstadoLicencia { ACTIVA, VENCIDA, REVOCADA, PERPETUA }

/**
 * Payload de la solicitud (texto plano antes de cifrar). 0.23.0: versión 2, dentro de la solicitud cifrada compacta
 * ([SolicitudCifrada], `SPVIR1:`). Cambios frente a v1: `devicePub` es la clave pública del dispositivo como punto
 * COMPRIMIDO (33 B) en Base64url sin relleno; ya no van `appName` (lo dice el prefijo de `deviceId`) ni `licenciaCorta`
 * (GL responde SIEMPRE con la licencia corta cifrada).
 */
@Serializable
data class RequestPayload(
    val v: Int = GlContract.SOLICITUD_VERSION,
    val nombre: String,
    val apellidos: String,
    val ci: String,
    val via: Via,
    val telefono: String,
    val deviceId: String,
    val tipo: TipoLicencia,
    val solicitadaEn: String,
    val nonce: String,
    /** Punto comprimido P-256 (33 B) en Base64url sin relleno. GL cifra la licencia hacia esta clave. */
    val devicePub: String,
    /** 0.21.0 (C4): apps secundarias pedidas (0..[GlContract.SECUNDARIAS_MAX]); GL la copia a la licencia. */
    val secundarias: Int = GlContract.SECUNDARIAS_DEFECTO,
    /**
     * 0.22.0 (L-e): id de la licencia que se renueva (la instalada y verificada en este teléfono, no perpetua).
     * GL la busca en SUS registros (nunca se fía de fechas enviadas por el cliente) y, si es de este mismo deviceId y no
     * está revocada, empieza la nueva al terminar la actual: `venceEn = max(ahora, venceEn anterior) + duración`.
     * Ausente (no se escribe) en una primera licencia.
     */
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val renueva: String? = null,
    /**
     * 0.25.0: id de la licencia que se RECUPERA en este teléfono (nuevo o reinstalado). GL comprueba en SUS registros
     * que existe, no está revocada ni vencida y que el CI coincide; entonces la revoca y emite otra con el mismo tipo,
     * vencimiento y secundarias, gratis. Ausente (no se escribe) en las demás solicitudes; excluyente con [renueva].
     */
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val recupera: String? = null,
    /**
     * ID de la licencia instalada y vigente cuyas apps secundarias se AMPLÍAN hasta [secundarias] (≤ 10),
     * conservando el vencimiento original y cobrando únicamente las secundarias adicionales.
     * Excluyente con [renueva] y [recupera].
     */
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val amplia: String? = null,
)

/**
 * Payload de la licencia: campos de la solicitud + id, emitidaEn, venceEn, estado y un nonce nuevo.
 *
 * Campos del flujo **v1** (licencia larga, `SISTEMA_LICENCIAS_CLIENTES.md` §5.2): `licenciaObtenida`,
 * `codigoCorto`. La licencia corta de Shalev no los trae (el código de 16 caracteres solo existe en el flujo v1).
 */
@Serializable
data class LicensePayload(
    val v: Int = GlContract.VERSION,
    val nombre: String,
    val apellidos: String,
    val ci: String,
    val via: Via? = null,
    val telefono: String? = null,
    val deviceId: String,
    val tipo: TipoLicencia,
    val solicitadaEn: String? = null,
    val nonce: String? = null,
    val devicePub: String? = null,
    val id: String,
    val emitidaEn: String,
    val venceEn: String? = null,
    val estado: EstadoLicencia,
    /** 0.21.0 (C4): opcional; ausente (licencias de GL anteriores) = [GlContract.SECUNDARIAS_DEFECTO]. */
    val secundarias: Int? = null,
    /** Código de 16 caracteres emitido (`XXXX-XXXX-XXXX-XXXX`), flujo v1. */
    val licenciaObtenida: String? = null,
    /** Igual a [licenciaObtenida] (flujo v1). */
    val codigoCorto: String? = null,
)

object GlJson {
    /** Salida: compacta, con valores por defecto (v) y nulls explícitos (salvo `renueva`, que no se escribe si es null). */
    val encoder = Json { encodeDefaults = true; explicitNulls = true }

    /**
     * Payload de licencia (ya autenticado por firma + GCM): tolerante a campos que GL añada en el futuro;
     * la autenticidad la da la criptografía, no el esquema.
     */
    val decoder = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /**
     * Envelope: ESTRICTO, como exige el contrato v1 (`ignoreUnknownKeys = false`). Un envelope con claves
     * fuera de las 8 de v1 se rechaza antes de cualquier operación criptográfica.
     */
    val envelopeDecoder = Json { ignoreUnknownKeys = false; explicitNulls = false }
}
