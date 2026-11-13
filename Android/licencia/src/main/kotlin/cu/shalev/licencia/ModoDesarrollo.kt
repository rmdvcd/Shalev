package cu.shalev.licencia

/**
 * Modo desarrollo (mitigación 1 del sistema de licencias de GL, Opción A).
 *
 * La **clave maestra** `GLMK-PERM-MAXS-9999` solo vale en builds de depuración (`BuildConfig.DEBUG`). En la app de
 * release la comprobación devuelve siempre `false`, así que nadie puede activar Shalev con la clave de pruebas.
 *
 * El valor por defecto es `false` (**fail-closed**): si nadie lo activa —o si algún día se olvida hacerlo— la clave
 * maestra no funciona. Lo pone el arranque de la app (`ShalevApplication`, con el `BuildConfig.DEBUG` de `:app`) y los
 * tests que lo comprueban.
 */
object ModoDesarrollo {
    @Volatile
    var esDebug: Boolean = false

    /** Ejecuta [bloque] como build de depuración y deja el valor anterior (solo tests). */
    inline fun <T> comoDebug(bloque: () -> T): T {
        val previo = esDebug
        esDebug = true
        try {
            return bloque()
        } finally {
            esDebug = previo
        }
    }
}
