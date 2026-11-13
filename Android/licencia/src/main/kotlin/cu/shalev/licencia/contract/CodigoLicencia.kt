package cu.shalev.licencia.contract

/**
 * Código de licencia del contrato GL: **16 caracteres alfanuméricos en 4 grupos de 4** (`XXXX-XXXX-XXXX-XXXX`).
 *
 * - Alfabeto `[0-9A-Z]`; se procesa **sin distinguir mayúsculas** e **ignorando guiones y espacios**.
 * - Es el valor de `licenciaObtenida` / `codigoCorto` de la licencia larga (flujo v1) y el formato de la clave
 *   maestra de desarrollo.
 * - Shalev no lo usa para activar: la licencia corta (`SPVI2:`) ya va cifrada y firmada. Sí lo usa para reconocer
 *   el código que GL escribe en el mensaje, y para comparar la clave maestra.
 *
 * Referencia: `SISTEMA_LICENCIAS_CLIENTES.md` §2.1 y §2.2 del repositorio GL.
 */
object CodigoLicencia {
    const val LARGO = 16

    /** Clave maestra de desarrollo. Solo se acepta en builds `DEBUG` ([ModoDesarrollo]). */
    const val MAESTRA = "GLMK-PERM-MAXS-9999"

    private val ALFABETO = Regex("[0-9A-Z]{$LARGO}")
    private val SEPARADORES = Regex("[\\s\\-]")

    /** `GLMKPERMMAXS9999`: la misma clave sin guiones (16 caracteres, dentro del alfabeto). */
    val MAESTRA_NORMALIZADA: String = MAESTRA.replace(SEPARADORES, "")

    /** Sin guiones ni espacios y en mayúsculas; `null` si no son 16 caracteres de `[0-9A-Z]`. */
    fun normalizar(texto: String): String? =
        texto.trim().uppercase().replace(SEPARADORES, "").takeIf { ALFABETO.matches(it) }

    fun valido(texto: String): Boolean = normalizar(texto) != null

    /**
     * ¿Es la clave maestra de desarrollo? **Solo cuando la app es un build `DEBUG`** ([esDebug]): en release,
     * cualquier texto devuelve `false` (mitigación 1, Opción A).
     */
    fun esMaestra(texto: String, esDebug: Boolean): Boolean = esDebug && normalizar(texto) == MAESTRA_NORMALIZADA
}
