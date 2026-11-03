package cu.shalev.app.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import cu.shalev.designsystem.icon.ShalevIcons
import kotlinx.serialization.Serializable

/**
 * Rutas type-safe (Navigation 2.8). Bloqueo y Onboarding NO están aquí: son puertas de [cu.shalev.app.root.ShalevRoot].
 */
sealed interface Route {
    @Serializable data object Inicio : Route
    /**
     * [venta] = nombre de TipoVenta → modo «elegir qué vender» (Prompt 13): checkbox + Continuar, sin exportar ni
     * borrar; [seleccion] = ids ya elegidos (CSV) al volver a «Agregar productos».
     *
     * Una alerta de Inicio no pasa por aquí: abre la pestaña y el ViewModel la recibe por
     * [cu.shalev.app.inicio.AlertaPendiente] (0.31.0).
     */
    @Serializable data class Inventario(val venta: String? = null, val seleccion: String? = null) : Route
    /**
     * P29: Servicios (sustituye a Elaboración). [venta] = true → modo «elegir qué vender» de una venta de servicios
     * (checkbox + Continuar); [seleccion] = ids ya elegidos (CSV).
     */
    @Serializable data class Servicios(val venta: Boolean = false, val seleccion: String? = null) : Route
    /** Crear (id = 0) o editar un servicio. */
    @Serializable data class ServicioForm(val id: Long = 0) : Route
    @Serializable data object Registros : Route
    @Serializable data object Herramientas : Route
    @Serializable data object FichaCosto : Route
    /** Registro de un turno (Registros → Turnos o "Ver" tras cerrar en Inicio). */
    @Serializable data class TurnoDetalle(val id: Long) : Route
    @Serializable data object Ajustes : Route
    /** [tipo] = nombre de TipoVenta elegido en el diálogo "Nueva venta". */
    @Serializable data class Venta(val tipo: String = "VENTA") : Route
    @Serializable data object Precios : Route
    @Serializable data object PagoElectronico : Route
    @Serializable data object Licencia : Route
    /** P37: Principal / Secundaria (Ajustes → Sistema, debajo de Licencia). */
    @Serializable data object Vinculacion : Route
    /** 0.21.0 (C12): la misma pantalla, abriendo directamente «Usar como secundaria» (teléfono + QR del dueño). */
    @Serializable data object VincularSecundaria : Route
    /** Lector QR mínimo (solo vinculación): devuelve el texto tal cual; lo inválido lo informa quien lo usa. */
    @Serializable data object QrVinculacion : Route
    /**
     * Crear (id = 0) o editar producto: [nombre] inicial y [foto]/[categoria] opcionales;
     * el usuario completa el resto.
     */
    @Serializable data class ProductoForm(
        val id: Long = 0,
        val nombre: String? = null,
        val foto: String? = null,
        /** Categoría inicial de un producto nuevo (p. ej. "Elaborado"). */
        val categoria: String? = null,
    ) : Route
    /**
     * Crear (id = 0) o editar un insumo (P29: viven en Inventario, categoría «Insumos»).
     * P31: [nombre] llega del formulario de producto al elegir la categoría «Insumos».
     */
    @Serializable data class InsumoForm(val id: Long = 0, val nombre: String? = null) : Route
    /** Asistente desde Ajustes. [paso] = nombre de PasoConfiguracion para abrir solo ese paso (null = pendientes). */
    @Serializable data class ConfiguracionInicial(val paso: String? = null) : Route
    @Serializable data object Perfil : Route
    /** Respaldo siempre completo (P17): base de datos + configuración. */
    @Serializable data object Respaldo : Route
    @Serializable data object Ayuda : Route
    @Serializable data object Soporte : Route
}

/** Destinos de negocio de la barra inferior; Ajustes se abre desde la esquina superior derecha de Inicio. */
enum class TopLevel(val route: Route, val icon: ImageVector, val label: String) {
    INICIO(Route.Inicio, ShalevIcons.Inicio, "Inicio"),
    INVENTARIO(Route.Inventario(), ShalevIcons.Inventario, "Inventario"),
    SERVICIOS(Route.Servicios(), ShalevIcons.Servicios, "Servicios"),
    REGISTROS(Route.Registros, ShalevIcons.Registros, "Registros"),
    HERRAMIENTAS(Route.Herramientas, ShalevIcons.Herramientas, "Herramientas"),
}

/**
 * 0.21.0 (C12): secciones de la barra inferior según los módulos del negocio. Inventario solo con Ventas o Inventario;
 * Servicios solo con Servicios. Inicio y Registros siempre (Ajustes vive en Inicio y no forma parte del deslizado).
 */
fun topLevelVisibles(p: cu.shalev.domain.model.PermisosApp): List<TopLevel> = TopLevel.entries.filter {
    when (it) {
        TopLevel.INVENTARIO -> p.verInventario
        TopLevel.SERVICIOS -> p.verServicios
        else -> true
    }
}
