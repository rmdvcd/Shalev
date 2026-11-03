package cu.shalev.app.common

import cu.shalev.core.result.AppError

/**
 * Traductor central de errores: TODO [AppError] tiene aquí su texto en lenguaje llano, sin códigos, nombres de
 * campos ni reglas internas (la persona ve «Revisa los datos marcados», nunca `Validacion(campo=…, regla=…)`).
 *
 * El `when` es exhaustivo a propósito y NO tiene rama por defecto: si mañana nace un error nuevo, esto no compila
 * hasta escribir su frase comprensible. Las pantallas con textos específicos de su contexto (venta, respaldo…)
 * siguen mandando; este es el respaldo común y el que usan las pantallas nuevas.
 *
 * Regla de oro: los datos del error son identificadores internos y NO se intercalan… salvo datos DEL USUARIO
 * (nombres de sus productos/servicios en [AppError.EnUso] y [AppError.StockInsuficiente]) y el motivo de una
 * vinculación rechazada, que viaja por la red y pasa por [esTextoParaUsuario] antes de mostrarse.
 */
fun AppError.mensajeParaUsuario(): String = when (this) {
    is AppError.Validacion -> "Revisa los datos marcados e inténtalo de nuevo."
    AppError.NoEncontrado -> "Eso ya no existe. Actualiza la lista e inténtalo de nuevo."
    is AppError.Duplicado -> "Ya existe uno igual. Cambia el dato repetido e inténtalo de nuevo."
    is AppError.EnUso -> detalle.takeIf { esTextoParaUsuario(it) }
        ?.let { "No se puede quitar: se usa en $it." }
        ?: "No se puede quitar porque se está usando en otra parte."
    is AppError.StockInsuficiente -> if (faltantes.isEmpty()) {
        "No hay existencias suficientes. Ajusta las cantidades."
    } else {
        "No alcanza: ${faltantes.joinToString(", ")}. Ajusta las cantidades."
    }
    AppError.TurnoCerrado -> "El turno está cerrado. Ábrelo para continuar."
    AppError.TurnoYaAbierto -> "Ya hay un turno abierto."
    AppError.LicenciaBloqueada -> "Tu licencia no permite hacer esto ahora. Revísala en Ajustes → Licencia."
    AppError.Cripto -> "No se pudo proteger la información. Inténtalo de nuevo; si sigue pasando, contacta al soporte."
    AppError.ContrasenaIncorrecta -> "La contraseña no es correcta o el archivo está dañado."
    is AppError.ArchivoDanado -> if (incompleto) {
        "El archivo está incompleto: se cortó al copiarlo o al enviarlo. Pide que te lo envíen de nuevo."
    } else {
        "El archivo está dañado y no se puede abrir. Usa otra copia."
    }
    is AppError.FormatoInvalido -> "Este archivo no se puede usar en Shalev. Elige un archivo creado con Shalev."
    AppError.Almacenamiento -> "No se pudo guardar en el teléfono. Revisa que tengas espacio libre e inténtalo de nuevo."
    AppError.SinPermiso -> TextosSync.SIN_PERMISO
    is AppError.SinPrincipal -> TextosSync.sinPrincipal(pendientes)
    is AppError.VinculacionRechazada -> motivo.takeIf { esTextoParaUsuario(it) }
        ?: "La app principal rechazó la vinculación. Pide un código nuevo al dueño."
    AppError.Red.SinConexion -> TextosSync.SIN_RED
    AppError.Red.Timeout -> "La conexión tardó demasiado en responder. Inténtalo de nuevo con mejor señal."
    is AppError.Red.Http -> "No se pudo conectar con el servicio. Inténtalo de nuevo más tarde."
    is AppError.Desconocido -> "Algo salió mal. Inténtalo de nuevo; si sigue pasando, contacta al soporte."
}

/**
 * ¿Este texto puede enseñarse tal cual a una persona sin conocimientos técnicos? Solo si parece una frase:
 * un código interno (`codigo_vencido`, `sin-licencia-para-secundarias`, un nombre de clase…) se descarta y la
 * pantalla usa su texto genérico. Es la red de seguridad para textos que llegan de fuera de esta app (la red
 * local, un archivo de respaldo de otra versión): ante la duda, NO se muestra.
 */
internal fun esTextoParaUsuario(texto: String): Boolean {
    val limpio = texto.trim()
    if (limpio.isEmpty() || limpio.length > 200) return false
    if (limpio.contains('_')) return false // los códigos internos se escriben con guion bajo; las frases, no
    if (limpio.none { it == ' ' } && limpio.any { it == '-' || it == '.' || it == '/' }) return false // «sin-licencia», «cu.shalev…»
    return MARCAS_TECNICAS.none { limpio.contains(it, ignoreCase = true) }
}

private val MARCAS_TECNICAS = listOf("exception", "apperror", "cu.shalev", "http", "null", "stack", "{", "}", "[", "]")
