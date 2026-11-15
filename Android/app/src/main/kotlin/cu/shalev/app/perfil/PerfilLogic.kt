package cu.shalev.app.perfil

import cu.shalev.core.result.AppError
import cu.shalev.core.validation.Validators
import cu.shalev.domain.model.Perfil

object TextosPerfil {
    const val TITULO = "Perfil"
    const val MODULOS_TITULO = "Tu negocio"
    const val MODULOS_EXPLICACION = "Lo que no marques se oculta en la barra, Inicio y Registros. No se borra nada."
    const val EXPLICACION = "Se usan para pedir la licencia. Solo se guardan, protegidos, en este teléfono."
    const val ERROR_NOMBRE = "Solo letras y espacios; se admiten apóstrofos y guiones (2 a 80)."
    const val ERROR_CI = "11 dígitos: empieza por tu fecha de nacimiento (AAMMDD)"
    const val ERROR_MOVIL = "Móvil de 8 dígitos que empiece por 5 o 6"
    const val GUARDADO = "Datos guardados"
    const val ERROR_GENERICO = "No se pudo guardar. Inténtalo de nuevo."
    const val DESCARTAR_TITULO = "¿Salir sin guardar?"
    const val DESCARTAR_TEXTO = "Los cambios en tus datos personales se perderán."
}

/**
 * Formulario de datos personales. Los tres campos son opcionales (se pueden completar más tarde), pero si
 * tienen texto deben cumplir las reglas del contrato GL: así la solicitud de licencia no falla después.
 */
data class DatosPerfilForm(
    val nombre: String = "",
    val apellidos: String = "",
    val ci: String = "",
    val mostrarErrores: Boolean = false,
) {
    val errorNombre: String? get() = TextosPerfil.ERROR_NOMBRE.takeIf { nombre.isNotBlank() && !Validators.nombre(nombre) }
    val errorApellidos: String? get() = TextosPerfil.ERROR_NOMBRE.takeIf { apellidos.isNotBlank() && !Validators.nombre(apellidos) }
    val errorCi: String? get() = TextosPerfil.ERROR_CI.takeIf { ci.isNotBlank() && !Validators.carneCubano(ci) }
    val valido: Boolean get() = errorNombre == null && errorApellidos == null && errorCi == null

    /** Solo los datos personales: las listas se conservan tal como estén guardadas. */
    fun aplicarA(p: Perfil): Perfil = p.copy(nombre = nombre.trim(), apellidos = apellidos.trim(), ci = ci.trim().uppercase())

    fun cambiado(p: Perfil): Boolean =
        nombre.trim() != p.nombre || apellidos.trim() != p.apellidos || ci.trim().uppercase() != p.ci

    companion object {
        fun desde(p: Perfil) = DatosPerfilForm(p.nombre, p.apellidos, p.ci)
    }
}

/** Mensaje genérico: nunca se muestra el dato rechazado. */
fun mensajePerfil(e: AppError): String = when {
    e is AppError.Validacion && e.campo in setOf("nombre", "apellidos") -> TextosPerfil.ERROR_NOMBRE
    e is AppError.Validacion && e.campo == "ci" -> TextosPerfil.ERROR_CI
    else -> TextosPerfil.ERROR_GENERICO
}

/** Textos del ID de la licencia (Perfil → «ID de la licencia»). */
object TextosIdLicencia {
    const val TITULO = "ID de la licencia"
    const val EXPLICACION = "Si pierdes o se rompe este teléfono, con este ID pasas gratis tu licencia pagada al nuevo."
    const val BOTON = "Mostrar"
    const val DIALOGO = "ID de tu licencia"
    const val DETALLE = "Guárdalo fuera de este teléfono. En el nuevo, pégalo en Licencia → «ID de la licencia anterior»."
    const val AVISO = "No lo compartas: al usarlo, este teléfono se bloquea y borra sus datos."
    const val SIN_LICENCIA = "Este teléfono aún no tiene una licencia activada. El ID aparece al activarla."
    const val COPIAR = "Copiar"
    const val COPIAR_DESCRIPCION = "Copiar el ID al portapapeles"
    const val COPIADO = "ID copiado al portapapeles"
}

/**
 * ID con el que se recupera la licencia pagada en otro teléfono: el `licenseId` (UUID) de la licencia instalada,
 * aunque esté vencida. null si no hay licencia (prueba) o si es la licencia local de desarrollo (no viene de GL).
 */
fun idRecuperacion(lic: cu.shalev.domain.model.Licencia?): String? =
    lic?.instalada?.id?.let { id -> cu.shalev.licencia.contract.IdLicencia.extraer(id)?.takeIf { it == id.trim().lowercase() } }

/** Lo que muestra la tarjeta del ID; null en la app secundaria (usa la licencia de la principal, no tiene ID propio). */
data class IdLicenciaUi(val id: String?)
