package cu.shalev.data.sync

import cu.shalev.data.dto.InsumoDto
import cu.shalev.data.dto.MovimientoDto
import cu.shalev.data.dto.MovimientoCajaDto
import cu.shalev.data.dto.PerfilDto
import cu.shalev.data.dto.PreajusteDto
import cu.shalev.data.dto.PreferenciasDto
import cu.shalev.data.dto.ProductoDto
import cu.shalev.data.dto.RecetaDto
import cu.shalev.data.dto.ServicioDto
import cu.shalev.data.dto.TurnoDto
import cu.shalev.data.dto.VentaDto
import cu.shalev.data.local.ShalevJson
import cu.shalev.domain.model.CodigoVinculacion
import java.time.Instant
import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * P37: protocolo Principal ⇄ Secundaria por la red local (TCP). Versión 1.
 *
 *  1. Saludo en claro (JSON): la secundaria dice quién es y manda un nonce; la principal responde con el suyo.
 *     La primera vez, en vez de saludo va «Vincular»: acuerdo de clave ECDH P-256 autenticado con el token del QR.
 *  2. Después, todo va cifrado con AES-256-GCM con claves de sesión derivadas (HKDF) de la clave del empleado y de
 *     los dos nonces (ver [CanalCifrado]). Cada mensaje es un [Mensaje] en JSON comprimido.
 *
 * Los datos del negocio reutilizan los DTO del respaldo (formato estable y probado).
 *
 * 0.25.0: la versión SIGUE siendo 1 (la principal rechaza cualquier otra): todo lo nuevo (arqueo, caja, ventas anuladas,
 * APK por la red local) son campos y mensajes OPCIONALES que una app 0.24 ignora. Desviación documentada de P70
 * §5.4 («v2»): así una secundaria sin actualizar sigue vendiendo mientras se actualiza (convivencia).
 */

const val VERSION_PROTOCOLO = 1

/** JSON propio del protocolo: como el tolerante, más discriminador corto (versiones mezcladas). */
val SyncJson: Json = Json(ShalevJson) {
    classDiscriminator = "t"
}

// ---------------------------------------------------------------------------------------------- saludo (en claro)

@Serializable
sealed interface Saludo

/**
 * Secundaria ya vinculada → principal.
 *
 * 0.31.0 (mitigación 7): [device] es el `deviceId` (`SPVI:…`) de la secundaria. Es opcional para no romper la
 * convivencia con una principal anterior, y se repite en cada saludo para que la principal pueda firmarle su sub-token
 * aunque se haya reiniciado (el identificador no es ningún secreto: ya viaja en cada solicitud de licencia).
 */
@Serializable @SerialName("hola")
data class Hola(
    val v: Int = VERSION_PROTOCOLO,
    val negocio: String,
    val empleado: Long,
    val nonce: String,
    val device: String? = null,
) : Saludo

/** Principal → secundaria: saludo aceptado. */
@Serializable @SerialName("hola_ok")
data class HolaOk(
    val nonce: String,
    /**
     * 0.21.7 (P-e): la principal recuerda los comandos por [Comando.clave] y no repite uno ya aplicado. Solo entonces la
     * secundaria reenvía un comando cuya respuesta se perdió. Una principal anterior no lo manda (false: no se reenvía).
     */
    val comandosUnicos: Boolean = false,
) : Saludo

/**
 * Primera conexión: clave pública de la secundaria + HMAC(token) que demuestra que leyó el QR.
 * 0.31.0 (mitigación 7): [device] = `deviceId` de la secundaria, para poder firmarle su sub-token.
 */
@Serializable @SerialName("vincular")
data class Vincular(
    val v: Int = VERSION_PROTOCOLO,
    val negocio: String,
    val empleado: Long,
    val pub: String,
    val mac: String,
    val device: String? = null,
) : Saludo

@Serializable @SerialName("vincular_ok")
data class VincularOk(val pub: String, val mac: String, val nombreNegocio: String) : Saludo

/**
 * Rechazo. [mac] = HMAC(clave del empleado, …) cuando la principal la conoce: solo un rechazo AUTENTICADO de tipo
 * [Rechazo.QUITADA] hace que la secundaria borre sus datos (nadie en la red puede provocarlo falsificando el mensaje).
 */
@Serializable @SerialName("rechazo")
data class Rechazo(val motivo: String, val mac: String? = null) : Saludo {
    companion object {
        const val QUITADA = "quitada"
        const val DESCONOCIDA = "desconocida"
        const val OTRO_NEGOCIO = "otro_negocio"
        const val CODIGO_VENCIDO = "codigo_vencido"
        const val CODIGO_INVALIDO = "codigo_invalido"
        const val VERSION = "version"
        const val LIMITE = "limite"
    }
}

// ---------------------------------------------------------------------------------------------- sesión (cifrada)

@Serializable
sealed interface Mensaje

/** Secundaria → principal: lo pendiente + hash del catálogo que ya tiene (para no reenviarlo si no cambió). */
@Serializable @SerialName("sync")
data class Sincronizar(
    val id: Long,
    val lote: Lote,
    val hash: String? = null,
    /** 0.21.0 (C2): teléfono del empleado (viaja cifrado). Una principal 0.20.x lo ignora. */
    val telefono: String? = null,
    /** 0.21.0 (C6): el empleado solicita cerrar su turno (se repite en cada sincronización hasta que se resuelva). */
    val solicitaCierre: Boolean = false,
    /** 0.25.0: uuids de [CambioVenta] ya aplicados aquí (la principal deja de enviarlos). Una principal 0.24 lo ignora. */
    val cambiosAplicados: List<String> = emptyList(),
    /** 0.25.0: versionCode instalado en la secundaria (para ofrecerle el APK que tenga la principal). null = 0.24 o anterior. */
    val versionCode: Int? = null,
) : Mensaje

@Serializable @SerialName("sync_ok")
data class SincronizarOk(
    val id: Long,
    val recibidos: Recibidos,
    val hash: String,
    val instantanea: Instantanea? = null,
    /** 0.20.0 (H5): el dueño pidió cerrar el turno de esta app. Una secundaria 0.19.x lo ignora (campo nuevo). */
    val cerrarTurno: Boolean = false,
    /** 0.21.0 (C6): el dueño rechazó la solicitud de cierre del empleado. */
    val cierreRechazado: Boolean = false,
    /** 0.25.0: ventas de esta secundaria anuladas o corregidas en la principal y aún no confirmadas. */
    val cambiosVentas: List<CambioVenta> = emptyList(),
    /** 0.25.0: APK más nuevo que la principal ya descargó y verificó (se pide por bloques con [PedirApk]). */
    val apk: VersionApk? = null,
    val conteoAprobado: cu.shalev.data.dto.ConteoCierreDto? = null,
    val conteoTurnoUuid: String? = null,
) : Mensaje

/**
 * 0.25.0: cambio hecho en la principal en una venta de la secundaria. [nueva] = null: la venta [uuid] quedó anulada.
 * [nueva] != null: venta corregida creada en la principal (con [uuid] como id global) que sustituye a [corrigeUuid].
 * Idempotente: la secundaria lo aplica por uuid y lo confirma en [Sincronizar.cambiosAplicados].
 */
@Serializable
data class CambioVenta(
    val uuid: String,
    val turnoUuid: String,
    val anuladaEn: Long? = null,
    val motivo: String? = null,
    val anuladaPor: String? = null,
    val nueva: VentaDto? = null,
    val corrigeUuid: String? = null,
)

/** 0.25.0: APK disponible en la principal. [sha256] en hex; [bytes] tamaño total. */
@Serializable
data class VersionApk(val nombre: String, val code: Int, val sha256: String, val bytes: Long)

/** 0.25.0: secundaria → principal: siguiente bloque del APK a partir de [desde]. */
@Serializable @SerialName("apk_pedir")
data class PedirApk(val id: Long, val sha256: String, val desde: Long) : Mensaje

/** 0.25.0: bloque del APK en Base64 (vacío + [error] si ya no está o cambió). */
@Serializable @SerialName("apk_bloque")
data class BloqueApk(val id: Long, val desde: Long, val datos: String = "", val total: Long = 0, val error: String? = null) : Mensaje

/** 0.32.0: secundaria → principal: siguiente bloque de una foto del catálogo (mismo mecanismo que el APK). */
@Serializable @SerialName("foto_pedir")
data class PedirFoto(val id: Long, val nombre: String, val desde: Long) : Mensaje

/** 0.32.0: bloque de la foto en Base64 (vacío + [error] si ya no está o cambió). [sha256] = hash del archivo completo. */
@Serializable @SerialName("foto_bloque")
data class BloqueFoto(
    val id: Long,
    val desde: Long,
    val datos: String = "",
    val total: Long = 0,
    val sha256: String? = null,
    val error: String? = null,
) : Mensaje

/** Secundaria → principal: acción que cambia el catálogo (solo con conexión y con permiso). */
@Serializable @SerialName("cmd")
data class Comando(
    val id: Long,
    val accion: Accion,
    /** 0.21.7 (P-e): identificador único de la acción (UUID); el reenvío lleva la misma. null = secundaria anterior. */
    val clave: String? = null,
) : Mensaje

/** [error] = código de [ErroresRemotos] (null = bien). [valor] = id nuevo al crear, existencia al ajustar… */
@Serializable @SerialName("cmd_ok")
data class ResultadoComando(val id: Long, val valor: Long? = null, val error: String? = null) : Mensaje

/** Principal → secundarias conectadas: algo cambió, conviene sincronizar. */
@Serializable @SerialName("aviso")
data object Aviso : Mensaje

/** Principal → secundaria durante la sesión: el dueño la quitó. */
@Serializable @SerialName("quitada")
data object Quitada : Mensaje

@Serializable @SerialName("ping")
data class Ping(val id: Long) : Mensaje

@Serializable @SerialName("pong")
data class Pong(val id: Long) : Mensaje

@Serializable
data class Lote(
    val turnos: List<TurnoSync> = emptyList(),
    val ventas: List<VentaSync> = emptyList(),
    /** 0.25.0: entradas y salidas de efectivo (una principal 0.24 las ignora y no las confirma: se reenvían). */
    val caja: List<CajaSync> = emptyList(),
)

/** 0.25.0: movimiento de efectivo de la secundaria. */
@Serializable
data class CajaSync(val uuid: String, val turnoUuid: String, val mov: MovimientoCajaDto)

/** Turno de la secundaria (con su resumen congelado si ya cerró). */
@Serializable
data class TurnoSync(val uuid: String, val turno: TurnoDto)

/** Venta + sus movimientos de inventario (salidas y consumos de insumos) tal como se hicieron en la secundaria. */
@Serializable
data class VentaSync(val uuid: String, val turnoUuid: String, val venta: VentaDto, val movimientos: List<MovimientoDto> = emptyList())

@Serializable
data class TurnoRecibido(val uuid: String, val cerrado: Boolean)

@Serializable
data class Recibidos(
    val turnos: List<TurnoRecibido> = emptyList(),
    val ventas: List<String> = emptyList(),
    /** 0.25.0: uuids de [CajaSync] guardados. */
    val caja: List<String> = emptyList(),
)

/** Catálogo de la principal para la secundaria (sin historial de ventas: cada secundaria guarda solo lo suyo). */
@Serializable
data class Instantanea(
    val nombreNegocio: String,
    val empleado: EmpleadoInfo,
    val licencia: LicenciaDto,
    val productos: List<ProductoDto> = emptyList(),
    val recetas: List<RecetaDto> = emptyList(),
    val insumos: List<InsumoDto> = emptyList(),
    val servicios: List<ServicioDto> = emptyList(),
    val preajustes: List<PreajusteDto> = emptyList(),
    /** Solo tarjetas y teléfonos de cobro (para el QR de Transfermóvil); el nombre y el carné del dueño NO viajan. */
    val perfil: PerfilDto? = null,
    val preferencias: PreferenciasDto? = null,
    /** 0.32.0: fotos del catálogo. Los bytes NO van aquí: viajan aparte, por bloques ([PedirFoto]). */
    val fotos: List<FotoCatalogoDto> = emptyList(),
    /** v16: local cuyo catálogo contiene esta instantánea. Vacío en clientes anteriores a este campo. */
    val local: String = "",
)

@Serializable
data class EmpleadoInfo(val nombre: String, val permisos: List<String>)

/**
 * 0.32.0: una foto del catálogo. [entidad] = "producto" o "servicio"; [nombre] = el archivo en el álbum de fotos
 * (un UUID .jpg). Los bytes viajan por bloques ([PedirFoto]); la secundaria los guarda con el mismo nombre.
 */
@Serializable
data class FotoCatalogoDto(
    val entidad: String,
    val id: Long,
    val nombre: String,
    val bytes: Long,
)

/**
 * [clase] = nombre de LicenciaPrincipal.Clase; [tipo] = nombre de TipoLicencia; [venceEn] epoch ms (null = perpetua).
 *
 * 0.31.0 (mitigación 7): los cuatro campos del **sub-token** (`SISTEMA_LICENCIAS_CLIENTES.md` §8.4) son opcionales:
 * una principal anterior no los manda y una secundaria anterior los ignora. `subToken` es el permiso firmado por la
 * principal para ESTA secundaria, `firmaPub` la clave pública (SPKI Base64) con la que se comprueba, `ranura` el
 * puesto que ocupa (1..[permitidas]) y [permitidas] la cuota de secundarias de la licencia de la principal.
 */
@Serializable
data class LicenciaDto(
    val clase: String,
    val tipo: String? = null,
    val venceEn: Long? = null,
    val subToken: String? = null,
    val firmaPub: String? = null,
    val ranura: Int? = null,
    val permitidas: Int? = null,
)

// ---------------------------------------------------------------------------------------------- acciones remotas

@Serializable
sealed interface Accion

@Serializable @SerialName("producto_guardar")
data class GuardarProducto(
    val producto: ProductoDto,
    val receta: RecetaDto? = null,
    /** 0.21.6: existencia que vio la secundaria al abrir la ficha (null = versión anterior: manda la escrita). */
    val cantidadLeida: Long? = null,
) : Accion

@Serializable @SerialName("producto_eliminar")
data class EliminarProducto(val id: Long) : Accion

@Serializable @SerialName("producto_stock")
data class AjustarStockProducto(val id: Long, val delta: Long, val nota: String? = null) : Accion

@Serializable @SerialName("producto_precios")
data class CambiarPrecios(val precios: Map<Long, Long>) : Accion

@Serializable @SerialName("insumo_guardar")
data class GuardarInsumo(val insumo: InsumoDto, /** 0.21.6: ver [GuardarProducto.cantidadLeida]. */ val cantidadLeidaMil: Long? = null) : Accion

@Serializable @SerialName("insumo_eliminar")
data class EliminarInsumo(val id: Long) : Accion

@Serializable @SerialName("insumo_stock")
data class AjustarStockInsumo(val id: Long, val deltaMil: Long, val nota: String? = null) : Accion

@Serializable @SerialName("servicio_guardar")
data class GuardarServicio(val servicio: ServicioDto) : Accion

@Serializable @SerialName("servicio_eliminar")
data class EliminarServicio(val id: Long) : Accion

@Serializable @SerialName("preajuste_guardar")
data class GuardarPreajuste(val preajuste: PreajusteDto) : Accion

@Serializable @SerialName("preajuste_eliminar")
data class EliminarPreajuste(val id: Long) : Accion

// ---------------------------------------------------------------------------------------------- QR de vinculación

/**
 * Texto del QR: `SPVI-VINC1:` + Base64url(JSON). Lleva la dirección de la principal y un token de un solo uso que
 * caduca a los 10 minutos; la clave definitiva se acuerda después por ECDH y nunca aparece en el QR.
 */
object CodigoQr {
    const val PREFIJO = "SPVI-VINC1:"
    private const val MAX = 2_000

    @Serializable
    private data class Contenido(
        val n: String, val nn: String, val h: List<String>, val p: Int,
        val e: Long, val en: String, val t: String, val x: Long,
    )

    fun codificar(c: CodigoVinculacion): String {
        val json = SyncJson.encodeToString(
            Contenido.serializer(),
            Contenido(c.negocioId, c.nombreNegocio, c.direcciones, c.puerto, c.empleadoId, c.nombreEmpleado,
                Base64.getUrlEncoder().withoutPadding().encodeToString(c.token), c.venceEn.toEpochMilli()),
        )
        return PREFIJO + Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray(Charsets.UTF_8))
    }

    /** null = no es un QR de vinculación de Shalev (o está dañado). */
    fun decodificar(texto: String): CodigoVinculacion? {
        val t = texto.trim()
        if (!t.startsWith(PREFIJO) || t.length > MAX) return null
        return runCatching {
            val json = String(Base64.getUrlDecoder().decode(t.removePrefix(PREFIJO)), Charsets.UTF_8)
            val c = SyncJson.decodeFromString(Contenido.serializer(), json)
            val token = Base64.getUrlDecoder().decode(c.t)
            require(token.size == TOKEN_BYTES && c.p in 1..65_535 && (c.h.isNotEmpty() || RelaySync.habilitado) && c.e > 0 && c.n.isNotBlank())
            CodigoVinculacion(c.n, c.nn, c.h, c.p, c.e, c.en, token, Instant.ofEpochMilli(c.x))
        }.getOrNull()
    }

    const val TOKEN_BYTES = 16
}

/** Traducción de errores entre apps (texto estable, sin datos). */
object ErroresRemotos {
    const val PERMISO = "permiso"
    const val NO_ENCONTRADO = "no_encontrado"
    const val STOCK = "stock"
    const val EN_USO = "en_uso"
    const val DUPLICADO = "duplicado:" // + campo
    const val VALIDACION = "validacion:" // + campo:regla
    const val OTRO = "otro"
}
