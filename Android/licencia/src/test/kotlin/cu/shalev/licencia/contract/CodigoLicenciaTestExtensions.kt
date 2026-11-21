package cu.shalev.licencia.contract

/**
 * Extensiones y constantes de clave maestra para pruebas unitarias.
 *
 * El literal `GLMK-PERM-MAXS-9999` vive en el sourceSet de tests/debug y nunca en `src/main` (S5).
 */
val CodigoLicencia.MAESTRA: String
    get() {
        CodigoLicencia.validadorMaestra = { it == "GLMKPERMMAXS9999" }
        return "GLMK-PERM-MAXS-9999"
    }

fun CodigoLicencia.habilitarMaestraParaTest() {
    CodigoLicencia.validadorMaestra = { it == "GLMKPERMMAXS9999" }
}
