package cu.shalev.domain.model

import cu.shalev.licencia.LicenseState

/**
 * Pasos del asistente de primera ejecución (la bienvenida no se persiste: es solo presentación).
 * Todos son OPCIONALES: la app nunca queda bloqueada por no completarlos.
 */
enum class PasoConfiguracion { DATOS, ALERTAS, PRUEBA }

/**
 * Lo que se guarda del asistente. Solo se persisten los pasos que no se pueden deducir de los datos reales:
 * - DATOS se da por hecho si el Perfil tiene datos.
 * - ALERTAS y PRUEBA necesitan confirmación explícita (los niveles tienen valores por defecto; la prueba es informativa).
 * - [camaraSolicitada]: el sistema ya mostró su diálogo de cámara alguna vez (para distinguir "denegado para siempre").
 * - [permisosInicialesPedidos] (0.31.0, P9): el sistema ya mostró UNA vez su diálogo de permisos al abrir la app por
 *   primera vez. No se vuelve a pedir en masa: los permisos concretos se piden donde hacen falta (cámara, fotos…).
 */
data class ConfiguracionInicial(
    val confirmados: Set<PasoConfiguracion> = emptySet(),
    val camaraSolicitada: Boolean = false,
    val permisosInicialesPedidos: Boolean = false,
)

/** Datos mínimos del usuario que pide el asistente. Todos opcionales; los que se escriban deben ser válidos. */
data class DatosIniciales(
    val nombre: String = "",
    val apellidos: String = "",
    val ci: String = "",
    val telefono: String = "",
) {
    val vacios: Boolean get() = nombre.isBlank() && apellidos.isBlank() && ci.isBlank() && telefono.isBlank()

    companion object {
        fun desde(p: Perfil) = DatosIniciales(p.nombre, p.apellidos, p.ci, p.telefonos.firstOrNull()?.numero.orEmpty())
    }
}

/** Resumen para Ajustes ("Faltan 2 pasos") y para decidir qué pasos mostrar al retomar el asistente. */
data class ResumenConfiguracion(
    val pasos: List<PasoConfiguracion>,
    val pendientes: List<PasoConfiguracion>,
) {
    val completa: Boolean get() = pendientes.isEmpty()
}

/** Lógica pura del plan de configuración (testeable sin Android). */
object PlanConfiguracion {

    /**
     * Pasos aplicables: PRUEBA solo tiene sentido durante el periodo de prueba.
     */
    fun pasos(licencia: Licencia?): List<PasoConfiguracion> = buildList {
        add(PasoConfiguracion.DATOS)
        add(PasoConfiguracion.ALERTAS)
        if (licencia == null || licencia.estado is LicenseState.Trial) add(PasoConfiguracion.PRUEBA)
    }

    fun hecho(paso: PasoConfiguracion, perfil: Perfil, licencia: Licencia?, conf: ConfiguracionInicial): Boolean = when (paso) {
        PasoConfiguracion.DATOS -> !perfil.vacio
        PasoConfiguracion.ALERTAS, PasoConfiguracion.PRUEBA -> paso in conf.confirmados
    }

    fun resumen(perfil: Perfil, licencia: Licencia?, conf: ConfiguracionInicial): ResumenConfiguracion {
        val pasos = pasos(licencia)
        return ResumenConfiguracion(pasos, pasos.filterNot { hecho(it, perfil, licencia, conf) })
    }
}
