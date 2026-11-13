package cu.shalev.licencia

import cu.shalev.licencia.contract.CodigoLicencia
import cu.shalev.licencia.contract.EstadoLicencia
import cu.shalev.licencia.contract.GlContract
import cu.shalev.licencia.contract.LicensePayload
import cu.shalev.licencia.contract.TipoLicencia
import cu.shalev.licencia.crypto.EcP256
import cu.shalev.licencia.crypto.fail
import java.time.Instant

/**
 * Reglas semánticas sobre una licencia YA autenticada (firma + GCM correctos).
 * Cualquier incoherencia → LicenseCryptoException (rechazo genérico).
 */
object LicenseValidator {

    fun evaluate(
        p: LicensePayload,
        aadLicenseId: String,
        expectedDeviceId: String,
        devicePubDer: ByteArray,
        now: Instant,
    ): LicenseState {
        if (p.v != GlContract.VERSION) fail()
        if (!p.id.equals(aadLicenseId, ignoreCase = true)) fail()
        if (p.deviceId != expectedDeviceId) fail()
        p.devicePub?.let { if (!EcP256.decodeSpkiText(it).contentEquals(devicePubDer)) fail() }

        val emitida = parseInstant(p.emitidaEn)
        // 0.21.0 (C4): campo opcional; fuera de rango = licencia mal formada.
        val secundarias = p.secundarias ?: GlContract.SECUNDARIAS_DEFECTO
        if (secundarias !in 0..GlContract.SECUNDARIAS_MAX) fail()
        // 0.31.0: el código de 16 caracteres del flujo v1 (`SISTEMA_LICENCIAS_CLIENTES.md` §2.1 y §5.1): si viaja,
        // tiene que ser del contrato, y `codigoCorto` tiene que ser idéntico a `licenciaObtenida`.
        val codigo = p.licenciaObtenida ?: p.codigoCorto
        if (codigo != null && !CodigoLicencia.valido(codigo)) fail()
        if (p.licenciaObtenida != null && p.codigoCorto != null && p.licenciaObtenida != p.codigoCorto) fail()
        val perpetua = p.tipo == TipoLicencia.PERPETUA

        return when (p.estado) {
            EstadoLicencia.REVOCADA -> LicenseState.Revoked
            EstadoLicencia.VENCIDA -> LicenseState.Expired(p.tipo)
            EstadoLicencia.PERPETUA -> {
                if (!perpetua || p.venceEn != null) fail()
                LicenseState.Perpetual(p.id, secundarias)
            }
            EstadoLicencia.ACTIVA -> {
                if (perpetua) {
                    if (p.venceEn != null) fail()
                    LicenseState.Perpetual(p.id, secundarias)
                } else {
                    val vence = parseInstant(p.venceEn ?: fail())
                    if (!vence.isAfter(emitida)) fail()
                    if (now.isBefore(vence)) LicenseState.Active(p.tipo, vence, p.id, secundarias) else LicenseState.Expired(p.tipo)
                }
            }
        }
    }

    private fun parseInstant(s: String): Instant = try { Instant.parse(s) } catch (e: Exception) { fail() }
}
