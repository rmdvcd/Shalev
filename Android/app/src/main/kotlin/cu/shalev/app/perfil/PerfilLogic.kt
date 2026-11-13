package cu.shalev.app.perfil

import cu.shalev.core.result.AppError
import cu.shalev.core.validation.Validators
import cu.shalev.domain.model.Perfil

object TextosPerfil {
    const val TITULO = "Perfil"
    const val MODULOS_TITULO = "Tu negocio"
    const val MODULOS_EXPLICACION = "Lo que no marques desaparece de la barra inferior, de Inicio y de Registros. No se borra nada."
    const val EXPLICACION = "Tus datos se usan para pedir la licencia. Solo se guardan en este teléfono, protegidos."
    const val ERROR_NOMBRE = "Solo letras y espacios; se admiten apóstrofos y guiones (2 a 80)."
    const val ERROR_CI = "Entre 5 y 20 letras o números"
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
    val errorCi: String? get() = TextosPerfil.ERROR_CI.takeIf { ci.isNotBlank() && !Validators.ci(ci) }
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
