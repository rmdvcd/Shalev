package cu.shalev.app.respaldo

import cu.shalev.core.result.AppError
import cu.shalev.core.time.Dates
import cu.shalev.domain.repository.EtapaRespaldo
import cu.shalev.domain.repository.InfoRespaldo
import cu.shalev.domain.repository.ResumenRespaldo
import cu.shalev.domain.validation.Validadores
import java.time.LocalDate
import java.time.ZoneId

object TextosRespaldo {
    const val TITULO = "Respaldo"
    const val EXPLICACION = "Guarda una copia protegida de todo el negocio."
    /** 0.27.0 (T10): contraseña opcional. Auditoría 2026-10-14 (S-01): ENCENDIDA por defecto. */
    const val PROTEGER = "Proteger con contraseña"
    const val SIN_CONTRASENA_AVISO = "Sin contraseña, quien consiga este archivo podrá leer tus ventas y los datos de tus clientes sin ninguna clave."
    const val LISTO_SIN_CONTRASENA = "Ahora elige dónde guardar el respaldo."
    const val SIN_LICENCIA = "La licencia no viaja en el respaldo: en otro teléfono hace falta una licencia nueva."
    const val ERROR_CONTRASENA = "Mínimo ${Validadores.MIN_CONTRASENA} caracteres"
    const val ERROR_REPETIR = "Las contraseñas no coinciden"
    const val CONTRASENA_INCORRECTA = "Contraseña incorrecta"
    const val FORMATO = "El archivo no es un respaldo de Shalev o está dañado"
    const val NO_SE_PUDO_LEER = "No se pudo abrir el archivo"
    const val NO_SE_PUDO_GUARDAR = "No se pudo guardar el respaldo"
    const val ERROR_GENERICO = "No se pudo completar. Inténtalo de nuevo."
    const val CONTENIDO = "Se guarda todo lo del negocio."
    const val AVISO_IMPORTAR = "Se reemplazarán TODOS los datos de este teléfono: productos, insumos, inventario, turnos, ventas y configuración. No se puede deshacer."
    const val CONTRASENA_LISTA = "Contraseña lista."
    const val IMPORTAR_AYUDA = "Elige un archivo .shalev."

    const val MIME = "application/octet-stream"
    const val EXTENSION = "shalev"
    const val ACEPTAR = "Aceptar"
}

/** "Shalev_respaldo_2026-09-30.shalev". Sin datos personales en el nombre. */
fun nombreRespaldo(fecha: LocalDate): String = "Shalev_respaldo_$fecha.${TextosRespaldo.EXTENSION}"


/**
 * Contraseña de exportación (se repite: si se olvida o se escribe mal, el respaldo es irrecuperable).
 * 0.27.0 (T10): [conContrasena] opcional. Auditoría 2026-10-14 (S-01): encendido por defecto — el modo sin
 * contraseña solo OFUSCA (su «secreto» se deriva de constantes publicadas en el código), no cifra de verdad;
 * apagarlo es una decisión expresa del usuario y la pantalla lo advierte. Apagado no se valida nada.
 */
data class ExportForm(
    val conContrasena: Boolean = true,
    val contrasena: String = "",
    val repetir: String = "",
    val mostrarErrores: Boolean = false,
) {
    val errorContrasena: String? get() =
        TextosRespaldo.ERROR_CONTRASENA.takeIf { conContrasena && contrasena.length < Validadores.MIN_CONTRASENA }
    val errorRepetir: String? get() =
        TextosRespaldo.ERROR_REPETIR.takeIf { conContrasena && errorContrasena == null && repetir != contrasena }
    val valido: Boolean get() = errorContrasena == null && errorRepetir == null
    /** Lo que recibe el repositorio: vacía = sin contraseña. */
    val contrasenaFinal: String get() = if (conContrasena) contrasena else ""
}

/**
 * Importación en curso: archivo ya comprobado (completo, intacto y de Shalev) y su contraseña.
 * [error] se muestra dentro del diálogo. [nombre] = nombre visible del archivo si se conoce.
 */
data class ImportForm(
    val uri: String,
    val info: InfoRespaldo? = null,
    val contrasena: String = "",
    val error: String? = null,
    val nombre: String? = null,
)

/** Diálogo final: confirmación de éxito o explicación del fallo (qué pasó y qué hacer). */
data class ResultadoRespaldo(val exito: Boolean, val titulo: String, val detalle: String)

/** Progreso visible: paso n de m con su texto. */
data class Progreso(val paso: Int, val total: Int, val texto: String) {
    val fraccion: Float get() = paso.toFloat() / total
    /** «Etapa», no «Paso»: así no se confunde con los pasos del asistente (P18, A09). */
    val etiqueta: String get() = "Etapa $paso de $total · $texto"
}

object EtapasRespaldo {
    private val EXPORTAR = listOf(EtapaRespaldo.PREPARANDO, EtapaRespaldo.CIFRANDO, EtapaRespaldo.ESCRIBIENDO)
    private val IMPORTAR = listOf(EtapaRespaldo.LEYENDO, EtapaRespaldo.VERIFICANDO, EtapaRespaldo.DESCIFRANDO, EtapaRespaldo.IMPORTANDO)

    fun texto(e: EtapaRespaldo): String = when (e) {
        EtapaRespaldo.PREPARANDO -> "Reuniendo los datos…"
        EtapaRespaldo.CIFRANDO -> "Cifrando con tu contraseña…"
        EtapaRespaldo.ESCRIBIENDO -> "Guardando el archivo…"
        EtapaRespaldo.LEYENDO -> "Leyendo el archivo…"
        EtapaRespaldo.VERIFICANDO -> "Comprobando que el archivo esté completo…"
        EtapaRespaldo.DESCIFRANDO -> "Comprobando la contraseña…"
        EtapaRespaldo.IMPORTANDO -> "Importando los datos…"
    }

    fun progreso(e: EtapaRespaldo): Progreso {
        val lista = if (e in EXPORTAR) EXPORTAR else IMPORTAR
        return Progreso(lista.indexOf(e) + 1, lista.size, texto(e))
    }

    /** Progreso de un paso único (PDF, comprobación del archivo recibido). */
    fun unico(texto: String) = Progreso(1, 1, texto)

    const val COMPROBANDO = "Comprobando el archivo…"
}


/** "Respaldo del 30/09/2026". */
fun describirArchivo(info: InfoRespaldo?, zona: ZoneId): String =
    info?.creadoEn?.let { "Respaldo del ${Dates.day(it, zona)}" } ?: "Respaldo de Shalev"

fun mensajeRespaldo(e: AppError): String = when (e) {
    AppError.ContrasenaIncorrecta -> TextosRespaldo.CONTRASENA_INCORRECTA
    is AppError.ArchivoDanado, is AppError.FormatoInvalido -> fallo(e).titulo
    is AppError.Validacion -> TextosRespaldo.ERROR_CONTRASENA
    AppError.Almacenamiento -> TextosRespaldo.NO_SE_PUDO_GUARDAR
    else -> TextosRespaldo.ERROR_GENERICO
}

/** Fallo de importación explicado en lenguaje llano. Siempre recuerda que no se tocó nada. */
fun fallo(e: AppError): ResultadoRespaldo {
    val intacto = "Tus datos no se han tocado."
    return when {
        e is AppError.ArchivoDanado && e.incompleto -> ResultadoRespaldo(
            false, "El archivo está incompleto",
            "Se cortó al descargarlo o enviarlo (por ejemplo, un envío por Bluetooth interrumpido). Pide que te lo envíen de nuevo o vuelve a copiarlo. $intacto",
        )
        e is AppError.ArchivoDanado -> ResultadoRespaldo(
            false, "El archivo está dañado",
            "Cambió después de guardarse y no se puede importar. Usa otra copia del respaldo. $intacto",
        )
        e is AppError.FormatoInvalido && e.detalle.contains("más nueva") -> ResultadoRespaldo(
            false, "Respaldo de una versión más nueva",
            "Se creó con una versión de Shalev más reciente. Actualiza la app e inténtalo de nuevo. $intacto",
        )
        e is AppError.FormatoInvalido && e.detalle.contains("versión anterior") -> ResultadoRespaldo(
            false, "Respaldo de una versión anterior",
            "Se creó con una versión muy antigua de Shalev que esta versión ya no abre. Crea un respaldo nuevo desde el teléfono de origen. $intacto",
        )
        e is AppError.FormatoInvalido -> ResultadoRespaldo(
            false, TextosRespaldo.FORMATO,
            "Elige un archivo .shalev creado con Shalev (Respaldo → Guardar o Enviar). $intacto",
        )
        e == AppError.Almacenamiento -> ResultadoRespaldo(false, TextosRespaldo.NO_SE_PUDO_LEER, "Vuelve a elegir el archivo o cópialo al teléfono primero. $intacto")
        else -> ResultadoRespaldo(false, "No se pudo importar", "${TextosRespaldo.ERROR_GENERICO} $intacto")
    }
}

private fun plural(n: Int, uno: String, varios: String) = "$n ${if (n == 1) uno else varios}"

/** Resumen tras exportar o importar (solo conteos, nunca contenido). */
fun resumenRespaldo(r: ResumenRespaldo, importado: Boolean): String {
    val verbo = if (importado) "importad" else "guardad"
    return "Respaldo ${verbo}o: " + listOf(
        plural(r.productos, "producto", "productos"),
        plural(r.insumos, "insumo", "insumos"),
        plural(r.ventas, "venta", "ventas"),
    ).joinToString(", ")
}

/** Confirmación final con el nombre del archivo y, al guardar con contraseña, su recordatorio. */
fun exitoRespaldo(r: ResumenRespaldo, importado: Boolean, nombre: String?, conContrasena: Boolean = true): ResultadoRespaldo {
    val resumen = resumenRespaldo(r, importado)
    return if (importado) {
        ResultadoRespaldo(true, "Respaldo importado", "$resumen.")
    } else {
        ResultadoRespaldo(
            true, "Respaldo guardado",
            listOfNotNull(
                nombre, "$resumen.",
                if (conContrasena) "Sin la contraseña no se puede abrir: guárdala en un lugar seguro." else TextosRespaldo.SIN_CONTRASENA_AVISO,
            ).joinToString("\n"),
        )
    }
}

/**
 * P18 (A09) + pregunta 3 (opción 1: asistente de 3 o 4 pasos en la misma pantalla). Solo presentación; los casos de uso no cambian.
 * - Exportar: 1 Proteger (opcional, 0.27.0) → 2 Destino (guardar o enviar) → 3 Hecho («Respaldo listo», con «Hacer otro respaldo»).
 *   El paso 2 solo se muestra con una contraseña válida.
 * - Importar: 1 Elegir archivo → 2 Comprobar (formato e integridad, sin contraseña) → 3 Contraseña → 4 Confirmar
 *   (reemplaza los datos). Los pasos 3 y 4 se hacen en el diálogo de importación.
 */
object PasosRespaldo {
    const val TOTAL_EXPORTAR = 3
    const val TOTAL_IMPORTAR = 4
    const val EXPORTAR_1 = "Protégelo (opcional)"
    const val EXPORTAR_2 = "Elige dónde guardarlo"
    const val EXPORTAR_3 = "Respaldo listo"
    const val IMPORTAR_1 = "Elige el archivo .shalev"
    const val IMPORTAR_2 = "Comprobando el archivo"
    const val IMPORTAR_3 = "Escribe la contraseña del respaldo"
    const val IMPORTAR_4 = "Pulsa Importar para reemplazar tus datos"
    const val COMPROBADO = "Paso 2 de $TOTAL_IMPORTAR: archivo comprobado"
    const val OTRO_RESPALDO = "Hacer otro respaldo"

    fun pasoExportar(elegido: Int, form: ExportForm, hecho: Boolean = false): Int = when {
        hecho -> 3
        elegido >= 2 && form.valido -> 2
        else -> 1
    }

    fun tituloExportar(paso: Int): String = when (paso) {
        3 -> EXPORTAR_3
        2 -> EXPORTAR_2
        else -> EXPORTAR_1
    }

    /** Paso del diálogo de importación (el archivo ya está comprobado). */
    fun pasoImportar(imp: ImportForm): Int = if (pideContrasena(imp) && imp.contrasena.isEmpty()) 3 else 4

    /** 0.27.0 (T10): un archivo sin contraseña no la pide (v3 siempre la tiene). */
    fun pideContrasena(imp: ImportForm): Boolean = imp.info?.conContrasena ?: true

    /** Paso que muestra la tarjeta «Importar»: 1 sin archivo, 2 mientras se comprueba, 3–4 con el diálogo abierto. */
    fun pasoImportarTarjeta(comprobando: Boolean, imp: ImportForm?): Int = when {
        imp != null -> pasoImportar(imp)
        comprobando -> 2
        else -> 1
    }

    fun tituloImportar(paso: Int): String = when (paso) {
        4 -> IMPORTAR_4
        3 -> IMPORTAR_3
        2 -> IMPORTAR_2
        else -> IMPORTAR_1
    }

    fun textoImportar(imp: ImportForm): String = pasoImportar(imp).let { p ->
        "Paso $p de $TOTAL_IMPORTAR: ${tituloImportar(p).replaceFirstChar { it.lowercase() }}"
    }

    /** Texto del paso 3 de exportar: nombre del archivo y recordatorio de la contraseña. */
    fun textoHecho(nombre: String, enviado: Boolean, conContrasena: Boolean = true): String =
        (if (enviado) "$nombre se ha preparado para enviarlo." else "$nombre guardado.") +
            (if (conContrasena) " Guárdalo junto con la contraseña: sin ella no se puede abrir." else " ${TextosRespaldo.SIN_CONTRASENA_AVISO}")
}
