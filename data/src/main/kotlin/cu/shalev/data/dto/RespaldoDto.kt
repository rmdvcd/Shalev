package cu.shalev.data.dto

import kotlinx.serialization.Serializable

/*
 * Formato portable de respaldo (JSON). Desacoplado del esquema Room: una migración de BD no rompe
 * respaldos antiguos. Importes en centavos, cantidades de insumo en milésimas, instantes en epoch ms,
 * fechas de calendario en ISO-8601 (yyyy-MM-dd). Campos nuevos siempre con valor por defecto.
 */

@Serializable
data class RespaldoDto(
    val formato: String = FORMATO,
    val version: Int = VERSION,
    val creadoEn: Long,
    val appVersion: String? = null,
    val productos: List<ProductoDto> = emptyList(),
    val recetas: List<RecetaDto> = emptyList(),
    val insumos: List<InsumoDto> = emptyList(),
    val turnos: List<TurnoDto> = emptyList(),
    val ventas: List<VentaDto> = emptyList(),
    val movimientos: List<MovimientoDto> = emptyList(),
    val perfil: PerfilDto? = null,
    val preajustes: List<PreajusteDto> = emptyList(),
    val preferencias: PreferenciasDto? = null,
    /** P29 (BD v4): vacío en respaldos anteriores, que siguen siendo v3 y se restauran igual. */
    val servicios: List<ServicioDto> = emptyList(),
    /**
     * 0.20.0 (H7): apps secundarias SIN claves ni códigos (al restaurar quedan pendientes de volver a vincular).
     * Vacío en respaldos anteriores; el formato sigue siendo v3.
     */
    val empleados: List<EmpleadoRespaldoDto> = emptyList(),
    /** v4 (0.25.0): entradas y salidas de efectivo de los turnos (vacío en v3: turnos sin arqueo). */
    val caja: List<MovimientoCajaDto> = emptyList(),
    /**
     * v4 (0.25.0): datos PÚBLICOS de la licencia (no la licencia: está atada a la clave del teléfono). Al restaurar en
     * un teléfono sin licencia, permiten pedir la recuperación sin escribir el ID (ver P70).
     */
    val licencia: LicenciaRespaldoDto? = null,
    /** 0.27.0 (N2): clientes fijos (campo opcional: el formato sigue en v4). */
    val clientesFijos: List<ClienteFijoDto> = emptyList(),
    val almacenesLocales: List<AlmacenLocalDto> = emptyList(),
) {
    companion object {
        const val FORMATO = "spvi-respaldo"
        /**
         * v4 (0.25.0): arqueo de caja, ventas anuladas y bloque de licencia. Campos nuevos con valor por defecto: un v3
         * se importa igual (turnos sin arqueo, ventas válidas). Acompaña al formato de archivo «Shalev-BACKUP» v3.
         */
        const val VERSION = 4
        /** Versión más antigua que se importa. */
        const val VERSION_MINIMA = 3
    }
}

/** 0.25.0: movimiento de efectivo de un turno. */
@Serializable
data class MovimientoCajaDto(
    val id: Long, val turnoId: Long, val fecha: Long, val tipo: String, val importeCent: Long, val motivo: String,
    val hechoPor: String = "", val uuid: String? = null, val sincronizado: Boolean = false,
)

/** 0.25.0: [venceEn] epoch ms (null = perpetua). */
@Serializable
data class LicenciaRespaldoDto(val id: String, val tipo: String, val secundarias: Int, val venceEn: Long? = null, val ci: String = "")

@Serializable
data class ProductoDto(
    val id: Long, val categoria: String, val nombre: String, val descripcion: String? = null, val fotoUri: String? = null,
    val fechaCaducidad: String? = null, val precioCostoCent: Long, val precioVentaCent: Long, val cantidad: Long,
    val nivelBajo: Long? = null, val nivelCritico: Long? = null,
    val creadoEn: Long, val actualizadoEn: Long, val eliminado: Boolean = false,
)

@Serializable data class RecetaLineaDto(val insumoId: Long, val cantidadMil: Long)
@Serializable data class RecetaDto(val productoId: Long, val lineas: List<RecetaLineaDto>)

@Serializable
data class InsumoDto(
    val id: Long, val nombre: String, val unidad: String, val precioCent: Long, val cantidadMil: Long,
    val nivelBajoMil: Long? = null, val nivelCriticoMil: Long? = null, val creadoEn: Long, val actualizadoEn: Long,
    /** P29: null = no se vende suelto (y en respaldos anteriores). */
    val precioVentaCent: Long? = null,
)

/** 0.20.0 (H7): ficha de una app secundaria: nombre, permisos y con qué cobra. Nunca la clave. */
@Serializable
data class EmpleadoRespaldoDto(
    val id: Long, val nombre: String, val permisos: List<String> = emptyList(), val creadoEn: Long,
    val tarjetaId: Long? = null, val telefonoId: Long? = null,
)

/** P29: servicio con los insumos que gasta por vez. */
@Serializable
data class ServicioDto(
    val id: Long, val nombre: String, val tipo: String, val importeCent: Long, val descripcion: String? = null,
    val fotoUri: String? = null, val creadoEn: Long, val actualizadoEn: Long, val eliminado: Boolean = false,
    val insumos: List<RecetaLineaDto> = emptyList(),
)

@Serializable
data class ResumenTurnoDto(
    val numVentas: Int, val unidades: Long, val totalCent: Long, val efectivoCent: Long,
    val transferenciaCent: Long, val costoCent: Long, val numMovimientos: Int,
    // Desde 0.6.0 (null en respaldos anteriores).
    val ventasEfectivo: Int? = null, val ventasTransferencia: Int? = null,
    val movimientosProducto: Int? = null, val movimientosInsumo: Int? = null,
    /** 0.25.0. */
    val entradasCent: Long? = null, val salidasCent: Long? = null, val numAnuladas: Int? = null,
)

@Serializable
data class TurnoDto(
    val id: Long, val abiertoEn: Long, val cerradoEn: Long? = null, val resumen: ResumenTurnoDto? = null,
    // Desde 0.6.0 (null en respaldos anteriores).
    val abiertoPor: String? = null, val cerradoPor: String? = null,
    /** P37: turno de una app secundaria (null = de la principal y en respaldos anteriores). */
    val empleadoId: Long? = null,
    /** 0.19.0: id global del turno (evita duplicarlo al sincronizar tras restaurar). null en respaldos anteriores. */
    val uuid: String? = null,
    val sincronizado: Boolean = false,
    /** 0.25.0: arqueo (null = turno sin arqueo, y en respaldos anteriores). */
    val fondoCent: Long? = null,
    val contadoCent: Long? = null,
    val conteoCierre: ConteoCierreDto? = null,
)

@Serializable
data class DetalleVentaDto(
    val id: Long, val productoId: Long, val nombre: String, val categoria: String, val cantidad: Long,
    val precioBaseCent: Long, val precioUnitarioCent: Long, val costoUnitarioCent: Long,
    /** P29: PRODUCTO / INSUMO / SERVICIO (PRODUCTO en respaldos anteriores). */
    val clase: String = "PRODUCTO",
)

@Serializable
data class TransaccionDto(
    val id: Long, val fecha: Long, val importeCent: Long, val numero: String,
    val clienteNombre: String, val clienteCi: String, val clienteTelefono: String,
    val tarjetaCobro: String? = null, val telefonoCobro: String? = null,
    /** 0.27.0 (N2): «Cliente fijo» marcado (false en respaldos y apps anteriores). */
    val clienteFijo: Boolean = false,
)

/** 0.27.0 (N2): cliente fijo en el respaldo (lista vacía en respaldos anteriores). */
@Serializable
data class ClienteFijoDto(
    val nombreApellidos: String, val ci: String, val telefono: String, val creadoEn: Long, val actualizadoEn: Long,
)

@Serializable
data class VentaDto(
    val id: Long, val turnoId: Long, val fecha: Long, val metodoPago: String,
    val detalles: List<DetalleVentaDto>, val transaccion: TransaccionDto? = null,
    /** 0.19.0: id global, app secundaria de origen y marca de enviado (null/false en respaldos anteriores). */
    val uuid: String? = null, val empleadoId: Long? = null, val sincronizado: Boolean = false,
    /** 0.25.0: anulación (null = válida, y en respaldos anteriores) y venta original a la que corrige. */
    val anuladaEn: Long? = null, val motivoAnulacion: String? = null, val anuladaPor: String? = null,
    val corrigeVentaId: Long? = null,
)

@Serializable
data class MovimientoDto(
    val id: Long, val fecha: Long, val tipo: String, val entidad: String, val entidadId: Long, val nombre: String,
    val delta: Long, val existencia: Long, val turnoId: Long? = null, val ventaId: Long? = null, val nota: String? = null,
    /** 0.21.0 (C7). Ausente en respaldos anteriores. */
    val hechoPor: String = "",
)


@Serializable data class TarjetaDto(val id: Long, val numero: String, val alias: String? = null)
@Serializable data class TelefonoDto(val id: Long, val numero: String, val alias: String? = null)

@Serializable
data class PerfilDto(
    val nombre: String, val apellidos: String, val ci: String,
    val tarjetas: List<TarjetaDto> = emptyList(), val telefonos: List<TelefonoDto> = emptyList(),
    val pagoTarjetaId: Long? = null, val pagoTelefonoId: Long? = null,
)

@Serializable
data class PreajusteDto(
    val id: Long, val nombre: String, val puntosBasicos: Int, val productoIds: List<Long>,
    val servicioIds: List<Long> = emptyList(),
    val metodoPago: String? = null, val importeMinimoCent: Long? = null, val activo: Boolean = true,
)

/** También es el formato persistido en DataStore (clave pref.v1). */
@Serializable
data class PreferenciasDto(
    val productoBajo: Long = 5,
    val productoCritico: Long = 1,
    val insumoBajoMil: Long = 5000,
    val insumoCriticoMil: Long = 1000,
    /** 0.21.0 (C12): ausente (respaldos y principales anteriores) = todos. Viaja a las secundarias en la instantánea. */
    val modulos: List<String>? = null,
    val empleadosPrevistos: Int = 0,
)

@Serializable
data class AlmacenLocalDto(val nombre: String, val inventario: Boolean = false, val servicios: Boolean = false)
