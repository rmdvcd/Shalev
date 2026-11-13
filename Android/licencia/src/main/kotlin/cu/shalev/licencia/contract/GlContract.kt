package cu.shalev.licencia.contract

/**
 * Constantes del contrato v1 de GL (CONTEXTO_LICENCIAS.md).
 * NO modificar: cualquier cambio rompe la compatibilidad con el emisor.
 */
object GlContract {
    const val VERSION = 1
    /** 0.23.0: versión del payload de la solicitud (dentro de `SPVIR1:`). La licencia larga v1 solo se re-verifica. */
    const val SOLICITUD_VERSION = 2
    const val ALG = "ECIES-P256-AES256GCM-v1"
    const val KID = "gl-sign-v1"
    const val HKDF_INFO_REQUEST = "gl-req-v1"
    const val HKDF_INFO_LICENSE = "gl-lic-v1"
    const val IV_LEN = 12
    const val TAG_LEN = 16
    const val AES_KEY_LEN = 32

    /** AAD de la solicitud: `v|alg|epk|kid`, con `epk` como el MISMO string Base64 del JSON. */
    /** 0.21.0 (C4): apps secundarias cubiertas si la licencia no lo dice (licencias anteriores y la prueba). */
    const val SECUNDARIAS_DEFECTO = 5
    /** 0.21.0 (C4): máximo que se puede pedir en una solicitud. */
    const val SECUNDARIAS_MAX = 10

    /**
     * AAD **heredada** (antes de la mitigación 8): `1|ECIES-P256-AES256GCM-v1|<licenseId>`. Solo se usa para
     * re-verificar licencias largas emitidas por GL antes de cambiar a la canonicalización con longitud prefijada.
     */
    fun licenseAadHeredada(licenseId: String): ByteArray = "$VERSION|$ALG|$licenseId".toByteArray(Charsets.UTF_8)

    /** AAD **heredada** del envelope v1 (el `epk` es el mismo texto Base64 del JSON). Igual que [licenseAadHeredada]. */
    fun requestAadHeredada(epkB64: String): ByteArray = "$VERSION|$ALG|$epkB64|$KID".toByteArray(Charsets.UTF_8)

    /**
     * Canonicalización de la AAD con **longitud prefijada** (4 bytes big-endian por campo), la que usa GL
     * (`CryptoEngine.canonical`). Inmune a colisiones de delimitadores (mitigación 8, Opción A): `1|a|b` y `1|a|b`
     * escritos de otra forma ya no pueden producir el mismo texto.
     */
    fun canonical(partes: List<String>): ByteArray {
        val salida = java.io.ByteArrayOutputStream()
        for (parte in partes) {
            val bytes = parte.toByteArray(Charsets.UTF_8)
            val n = bytes.size
            salida.write(byteArrayOf(((n ushr 24) and 0xff).toByte(), ((n ushr 16) and 0xff).toByte(), ((n ushr 8) and 0xff).toByte(), (n and 0xff).toByte()))
            salida.write(bytes)
        }
        return salida.toByteArray()
    }

    /** AAD de la solicitud v1 (envelope): `v ‖ alg ‖ epk ‖ kid`, con longitud prefijada. */
    fun requestAad(epkB64: String): ByteArray = canonical(listOf(VERSION.toString(), ALG, epkB64, KID))

    /** AAD de la licencia v1 (envelope): `v ‖ alg ‖ licenseId`, con longitud prefijada. */
    fun licenseAad(licenseId: String): ByteArray = canonical(listOf(VERSION.toString(), ALG, licenseId))
}

/**
 * Identificador de la app: prefijo de `deviceId` (desde 0.23.0 ya no se envía `appName`).
 * Se queda en `SPVI` a propósito: es el contrato con GL y con las licencias ya emitidas, no la marca visible.
 */
const val APP_NAME = "SPVI"
const val DEVICE_ID_PREFIX = "$APP_NAME:"
