package cu.shalev.licencia.contract

import cu.shalev.licencia.ModoDesarrollo

/**
 * Código de licencia del contrato GL: **16 caracteres alfanuméricos en 4 grupos de 4** (`XXXX-XXXX-XXXX-XXXX`).
 *
 * - Alfabeto `[0-9A-Z]`; se procesa **sin distinguir mayúsculas** e **ignorando guiones y espacios**.
 * - Es el valor de `licenciaObtenida` / `codigoCorto` de la licencia larga (flujo v1).
 * - En builds de depuración se puede registrar un validador de clave maestra de desarrollo;
 *   en release ningún literal de clave maestra viaja en el código fuente de main ni en el APK release (S5).
 *
 * Referencia: `SISTEMA_LICENCIAS_CLIENTES.md` §2.1 y §2.2 del repositorio GL.
 */
object CodigoLicencia {
    const val LARGO = 16

    private val ALFABETO = Regex("[0-9A-Z]{$LARGO}")
    private val SEPARADORES = Regex("[\\s\\-]")

    /** Validador de clave maestra registrado únicamente en tiempo de depuración / tests. */
    @Volatile
    var validadorMaestra: ((String) -> Boolean)? = null

    /** Sin guiones ni espacios y en mayúsculas; `null` si no son 16 caracteres de `[0-9A-Z]`. */
    fun normalizar(texto: String): String? =
        texto.trim().uppercase().replace(SEPARADORES, "").takeIf { ALFABETO.matches(it) }

    fun valido(texto: String): Boolean = normalizar(texto) != null

    /**
     * ¿Es la clave maestra de desarrollo?
     *
     * Unifica la verdad sobre depuración (S5): solo devuelve `true` si [esDebug] Y [ModoDesarrollo.esDebug]
     * son ciertos Y el validador registrado en depuración confirma el texto normalizado.
     * En release cualquier texto devuelve siempre `false` y ningún literal viaja en src/main.
     */
    fun esMaestra(texto: String, esDebug: Boolean = ModoDesarrollo.esDebug): Boolean {
        if (!esDebug || !ModoDesarrollo.esDebug) return false
        val fn = validadorMaestra ?: return false
        val norm = normalizar(texto) ?: return false
        return fn(norm)
    }
}
