package cu.shalev.domain.model

import cu.shalev.core.money.Cup
import cu.shalev.licencia.LicenseState
import cu.shalev.licencia.contract.TipoLicencia
import java.time.Duration
import java.time.Instant

/*
 * P37: dos tipos de app.
 *  - PRINCIPAL: la del dueño/gerente. Es un punto de venta COMPLETO (vende con su propio turno, edita inventario y
 *    servicios, precios, registros, exportaciones, respaldo…) y además guarda la base de datos general y activa la
 *    licencia. Es el valor de siempre: una instalación sin apps secundarias funciona exactamente igual que antes.
 *  - SECUNDARIA: la de un empleado, vinculada a una principal con un QR de un solo uso. Trabaja con una copia del
 *    catálogo de la principal y le envía sus turnos y ventas por la red local (wifi del local o zona wifi del
 *    teléfono principal), al momento o cuando el empleado lo pida.
 * Una app solo es SECUNDARIA mientras está vinculada: desvincularla (o que la principal la quite) borra sus datos
 * y la devuelve a PRINCIPAL.
 */
enum class TipoApp { PRINCIPAL, SECUNDARIA }

/** Lo que el dueño permite a cada app secundaria (se elige en la principal y se puede cambiar cuando quiera). */
enum class PermisoEmpleado(val etiqueta: String, val detalle: String) {
    VENDER_PRODUCTOS("Vender productos", "Abrir su turno y vender productos del inventario"),
    VENDER_SERVICIOS("Vender servicios", "Abrir su turno y vender servicios"),
    EDITAR_INVENTARIO("Editar inventario y servicios", "Crear, cambiar o eliminar productos, insumos y servicios, y sus existencias"),
    CAMBIAR_PRECIOS("Cambiar precios", "Ajustes de precio y cambios de precio de venta"),
    EXPORTAR("Exportar y compartir", "Sacar tablas y PDF del inventario, los servicios y sus registros"),
    ;

    companion object {
        /**
         * 0.21.0 (C3, P46): al agregar un empleado, todo menos «Editar inventario y servicios» y «Cambiar precios»;
         * el dueño los activa si quiere.
         */
        val PREDETERMINADOS: Set<PermisoEmpleado> = entries.toSet() - EDITAR_INVENTARIO - CAMBIAR_PRECIOS

        fun deNombres(nombres: Collection<String>): Set<PermisoEmpleado> =
            nombres.mapNotNull { n -> entries.firstOrNull { it.name == n } }.toSet()
    }
}

/**
 * Qué puede hacer ESTA app. La principal lo puede todo; una secundaria, lo que le dio el dueño.
 * 0.21.0 (C12): además, solo lo de los [modulos] del negocio (los elige el dueño; la secundaria los recibe al sincronizar).
 */
data class PermisosApp(
    val tipo: TipoApp,
    val permisos: Set<PermisoEmpleado>,
    val modulos: Set<Modulo> = Modulo.TODOS,
) {
    fun puede(p: PermisoEmpleado): Boolean = tipo == TipoApp.PRINCIPAL || p in permisos

    val esSecundaria: Boolean get() = tipo == TipoApp.SECUNDARIA
    val venderProductos: Boolean get() = puede(PermisoEmpleado.VENDER_PRODUCTOS) && Modulo.VENTAS in modulos
    val venderServicios: Boolean get() = puede(PermisoEmpleado.VENDER_SERVICIOS) && Modulo.SERVICIOS in modulos
    /** Sección Inventario (barra inferior, alertas). VENTAS siempre la incluye. */
    val verInventario: Boolean get() = Modulo.VENTAS in modulos || Modulo.INVENTARIO in modulos
    val verServicios: Boolean get() = Modulo.SERVICIOS in modulos
    /** Ventas de productos en Registros e Inicio. */
    val verVentas: Boolean get() = Modulo.VENTAS in modulos
    val vender: Boolean get() = venderProductos || venderServicios
    val editarInventario: Boolean get() = puede(PermisoEmpleado.EDITAR_INVENTARIO)
    val cambiarPrecios: Boolean get() = puede(PermisoEmpleado.CAMBIAR_PRECIOS)
    val exportar: Boolean get() = puede(PermisoEmpleado.EXPORTAR)

    companion object {
        val PRINCIPAL = PermisosApp(TipoApp.PRINCIPAL, PermisoEmpleado.entries.toSet())
    }
}

/** Una app secundaria registrada en la principal. [codigoVence] != null = QR generado y aún sin usar. */
data class Empleado(
    val id: Long,
    val nombre: String,
    val permisos: Set<PermisoEmpleado>,
    val creadoEn: Instant,
    val vinculadoEn: Instant? = null,
    val ultimaSincronizacion: Instant? = null,
    val codigoVence: Instant? = null,
    /** 0.20.0 (H4): tarjeta y teléfono de cobro de este empleado (ids del Perfil del dueño). null = los predeterminados. */
    val tarjetaId: Long? = null,
    val telefonoId: Long? = null,
    /** 0.20.0 (H5): el dueño pidió cerrar su turno; se borra cuando la principal recibe el turno cerrado. */
    val cierreSolicitadoEn: Instant? = null,
    /** 0.20.0 (H5): turno de esta secundaria abierto según lo último recibido (null = ninguno). No se guarda. */
    val turnoAbiertoDesde: Instant? = null,
    /** 0.21.0 (C2): teléfono que escribió el empleado al vincularse; va en su QR de cobro. */
    val telefono: String? = null,
    /** 0.21.0 (C6): el empleado pidió cerrar su turno (espera la aprobación del dueño). */
    val cierrePedidoPorEmpleadoEn: Instant? = null,
    /** 0.25.0: arqueo del turno abierto según lo recibido (con el contado si el empleado ya lo declaró). No se guarda. */
    val arqueoTurno: Arqueo? = null,
    val conteoCierre: ConteoCierre? = null,
    /** 0.26.0: versionCode de su app (null = aún no lo envió: 0.24 o anterior). */
    val versionCode: Int? = null,
) {
    val vinculado: Boolean get() = vinculadoEn != null
    val cierrePedido: Boolean get() = cierreSolicitadoEn != null && turnoAbiertoDesde != null
    /** 0.21.0 (C6): solicitud del empleado pendiente de Aprobar/Rechazar (solo con su turno abierto). */
    val solicitaCierre: Boolean get() = cierrePedidoPorEmpleadoEn != null && cierreSolicitadoEn == null && turnoAbiertoDesde != null
    fun codigoVigente(ahora: Instant): Boolean = codigoVence != null && ahora.isBefore(codigoVence)
}

/** Lo que lleva el QR de vinculación (lo genera la principal; lo lee la secundaria). */
data class CodigoVinculacion(
    val negocioId: String,
    val nombreNegocio: String,
    val direcciones: List<String>,
    val puerto: Int,
    val empleadoId: Long,
    val nombreEmpleado: String,
    /** Secreto de un solo uso (16 B). Solo sirve para el primer acuerdo de clave y caduca a los [Vinculacion.VALIDEZ_CODIGO]. */
    val token: ByteArray,
    val venceEn: Instant,
) {
    override fun equals(other: Any?): Boolean = other is CodigoVinculacion && negocioId == other.negocioId &&
        empleadoId == other.empleadoId && token.contentEquals(other.token) && venceEn == other.venceEn &&
        direcciones == other.direcciones && puerto == other.puerto && nombreNegocio == other.nombreNegocio &&
        nombreEmpleado == other.nombreEmpleado

    override fun hashCode(): Int = 31 * (31 * negocioId.hashCode() + empleadoId.hashCode()) + token.contentHashCode()

    override fun toString(): String = "CodigoVinculacion(empleado=$empleadoId, vence=$venceEn)" // sin el token
}

/**
 * Estado de la licencia de la principal tal como lo recibió la secundaria en la última sincronización.
 *
 * 0.31.0 (mitigación 7, Opción A): [subToken]/[firmaPub]/[ranura]/[permitidas] son el permiso **firmado** por la
 * principal para esta secundaria. Cuando vienen (principal 0.31+) se verifican antes de dar la licencia por buena;
 * `null` = una principal anterior no lo manda (convivencia).
 */
data class LicenciaPrincipal(
    val clase: Clase,
    val tipo: TipoLicencia? = null,
    /** null = perpetua. */
    val venceEn: Instant? = null,
    val subToken: String? = null,
    val firmaPub: String? = null,
    val ranura: Int? = null,
    val permitidas: Int? = null,
) {
    enum class Clase { PRUEBA, ACTIVA, PERPETUA, BLOQUEADA }

    /** Permiso firmado presente (una principal 0.31+ lo manda). */
    val conPermiso: Boolean get() = !subToken.isNullOrBlank() && !firmaPub.isNullOrBlank()

    /** Copia bloqueada (la secundaria no acepta lo que no puede comprobar). */
    fun bloqueada(): LicenciaPrincipal = LicenciaPrincipal(Clase.BLOQUEADA)

    /** Estado equivalente para la secundaria (mismo banner y mismo bloqueo que la principal). */
    fun estado(ahora: Instant): LicenseState = when (clase) {
        Clase.PERPETUA -> LicenseState.Perpetual(ID)
        Clase.BLOQUEADA -> LicenseState.Revoked
        Clase.PRUEBA -> {
            val fin = venceEn ?: return LicenseState.TrialExpired
            val horas = Duration.between(ahora, fin).toHours()
            if (horas <= 0) LicenseState.TrialExpired else LicenseState.Trial(((horas + 23) / 24).toInt())
        }
        Clase.ACTIVA -> {
            val t = tipo ?: TipoLicencia.entries.first()
            val fin = venceEn
            if (fin == null || !ahora.isBefore(fin)) LicenseState.Expired(t) else LicenseState.Active(t, fin, ID)
        }
    }

    companion object {
        const val ID = "principal"

        /** Lo que la principal envía de su propia licencia (sin ids ni datos del dispositivo). */
        fun de(estado: LicenseState, ahora: Instant): LicenciaPrincipal = when (estado) {
            is LicenseState.Trial -> LicenciaPrincipal(Clase.PRUEBA, venceEn = ahora.plus(Duration.ofDays(estado.daysLeft.toLong())))
            is LicenseState.Active -> LicenciaPrincipal(Clase.ACTIVA, estado.tipo, estado.venceEn)
            is LicenseState.Perpetual -> LicenciaPrincipal(Clase.PERPETUA)
            else -> LicenciaPrincipal(Clase.BLOQUEADA)
        }
    }
}

sealed interface EstadoConexion {
    data object Desconectada : EstadoConexion
    data object Conectando : EstadoConexion
    data class Conectada(val desde: Instant) : EstadoConexion
    /** [motivo] ya es texto para el usuario. */
    data class SinConexion(val motivo: String) : EstadoConexion
}

/** Avance de una sincronización, en bytes. [fase] = en qué parte va. */
data class ProgresoSync(
    val actual: Long,
    val total: Long,
    val fase: FaseSync = FaseSync.APLICANDO,
) {
    /** 0..1. Sin total conocido (0) no se puede pintar una barra. */
    val fraccion: Float get() = if (total <= 0L) 0f else (actual.toDouble() / total).coerceIn(0.0, 1.0).toFloat()
    val conTotal: Boolean get() = total > 0L
}

/** En qué parte de la sincronización está el avance. */
enum class FaseSync { RECIBIENDO, ENVIANDO, APLICANDO }

/** Lo que muestra la pantalla de vinculación en una app secundaria. */
data class EstadoSecundaria(
    /** 0.21.0: true solo en una app SECUNDARIA vinculada (el valor por defecto describe a la principal). */
    val vinculada: Boolean = false,
    val nombreNegocio: String = "",
    val nombreEmpleado: String = "",
    val permisos: Set<PermisoEmpleado> = emptySet(),
    val conexion: EstadoConexion = EstadoConexion.Desconectada,
    val ultimaSincronizacion: Instant? = null,
    /** Turnos y ventas de esta app que la principal aún no ha recibido. */
    val pendientes: Int = 0,
    val licencia: LicenciaPrincipal? = null,
    /** 0.20.0 (H5): el dueño pidió cerrar el turno; se cierra en cuanto no haya una venta en curso. */
    val cierrePendiente: Boolean = false,
    /** 0.20.0 (H5): el último turno lo cerró la petición del dueño (aviso hasta que el empleado lo lea). */
    val turnoCerradoPorPrincipal: Boolean = false,
    /** 0.21.0 (C6): el empleado pidió cerrar su turno; espera a que el dueño lo apruebe (sigue vendiendo). */
    val cierreSolicitado: Boolean = false,
    /** 0.21.0 (C6): el dueño no aprobó el cierre (aviso hasta que el empleado lo lea). */
    val cierreRechazado: Boolean = false,
    /** 0.21.0 (C2): su número de teléfono (lo escribió al vincularse). */
    val telefono: String? = null,
    /** 0.25.0: la principal tiene un APK más nuevo para esta app (se descarga por la red local). */
    val apk: ApkEnPrincipal? = null,
    /** 0.32.0: la principal avisó de que cambió el catálogo: hay que sincronizar para recibirlo. */
    val cambiosPendientes: Boolean = false,
    /** 0.32.0: avance de la sincronización en curso (null = no hay ninguna). */
    val progreso: ProgresoSync? = null,
    val operacionPendiente: Boolean = false,
    val operacionRecuperada: Boolean? = null,
)

/** Lo que muestra la pantalla de vinculación en la principal. */
data class EstadoPrincipal(
    val escuchando: Boolean = false,
    /** IPv4 de este teléfono en la red local (wifi o zona wifi). Vacío = sin red local. */
    val direcciones: List<String> = emptyList(),
    val puerto: Int = 0,
    /** Ids de las secundarias conectadas ahora mismo. */
    val conectadas: Set<Long> = emptySet(),
    /** 0.32.0: avance de la sincronización de cada app conectada (id de empleado → avance). */
    val progresos: Map<Long, ProgresoSync> = emptyMap(),
)

object Vinculacion {
    /**
     * 0.27.0 (T9): una app principal nunca pasa a secundaria. Solo puede vincularse como secundaria la app que eligió
     * «Secundaria» en el recorrido inicial, mientras no esté vinculada y no tenga apps de empleados. Para cambiar de
     * tipo hay que borrar los datos de la app (antes, exportar el respaldo).
     */
    fun puedeVincularseComoSecundaria(tipo: TipoApp, eligioSecundaria: Boolean, empleados: Int): Boolean =
        tipo == TipoApp.PRINCIPAL && eligioSecundaria && empleados == 0

    /**
     * 0.21.0 (C4, P46): cuántas apps secundarias cubre la licencia lo decide la propia licencia
     * ([cu.shalev.licencia.secundariasPermitidas]: la prueba y las licencias sin el campo, 5; como máximo 10).
     */
    const val SECUNDARIAS_DEFECTO = cu.shalev.licencia.contract.GlContract.SECUNDARIAS_DEFECTO
    const val SECUNDARIAS_MAX = cu.shalev.licencia.contract.GlContract.SECUNDARIAS_MAX
    val VALIDEZ_CODIGO: Duration = Duration.ofMinutes(10)
    const val MAX_NOMBRE = 40

    /** Nombre del empleado: obligatorio, ≤ [MAX_NOMBRE], sin repetir entre las secundarias de la principal. */
    fun validarNombre(nombre: String, existentes: Collection<String>): ErrorNombre? {
        val n = nombre.trim()
        return when {
            n.isEmpty() -> ErrorNombre.VACIO
            n.length > MAX_NOMBRE -> ErrorNombre.LARGO
            existentes.any { it.trim().equals(n, ignoreCase = true) } -> ErrorNombre.REPETIDO
            else -> null
        }
    }

    enum class ErrorNombre { VACIO, LARGO, REPETIDO }

    /** 0.21.0 (C2): teléfono móvil cubano del empleado: 8 dígitos que empiezan por 5 o 6 (sin +53). */
    fun telefonoValido(t: String): Boolean = Regex("[56][0-9]{7}").matches(t.trim())

    fun puedeAgregar(actuales: Int, limite: Int): Boolean = actuales < limite
}
