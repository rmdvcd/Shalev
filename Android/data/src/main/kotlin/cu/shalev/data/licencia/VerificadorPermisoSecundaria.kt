package cu.shalev.data.licencia

import cu.shalev.core.time.Clock
import cu.shalev.data.sync.LicenciaDto
import cu.shalev.domain.model.LicenciaPrincipal
import cu.shalev.licencia.contract.GlContract
import cu.shalev.licencia.crypto.B64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 0.31.0 (mitigación 7, Opción A): comprueba el **permiso firmado** que la app principal manda junto con su licencia.
 *
 * La cuota de apps secundarias deja de ser una auto-declaración: la principal firma un
 * [cu.shalev.licencia.secondary.SubToken] por ranura y dispositivo, y esta app no da la licencia por buena si el permiso
 * no la autoriza a ella. Reglas (fail-closed, sin decirle al usuario qué comprobación falló):
 *
 *  - Sin permiso = la principal es anterior a la 0.31.0: se acepta como siempre (convivencia de versiones).
 *  - Con permiso y permiso inválido (firma, dispositivo, ranura, cuota o vencimiento): la licencia queda **bloqueada**.
 *  - Los campos sin firma del mensaje (`ranura`, `permitidas`) tienen que coincidir con los del permiso; los que mandan
 *    son los firmados.
 */
@Singleton
class VerificadorPermisoSecundaria @Inject constructor(
    private val deviceIds: DeviceIdProvider,
    private val clock: Clock,
) {

    fun verificar(licencia: LicenciaDto): LicenciaDto {
        // Una licencia ya bloqueada se queda como está: la principal no autoriza a nadie con ella.
        if (licencia.clase == LicenciaPrincipal.Clase.BLOQUEADA.name) return licencia
        // Sin permiso: principal anterior a la 0.31.0. Se acepta la licencia tal cual (misma confianza que antes).
        val token = licencia.subToken?.takeIf { it.isNotBlank() } ?: return licencia
        val firma = licencia.firmaPub?.takeIf { it.isNotBlank() } ?: return bloquear()
        val datos = runCatching {
            cu.shalev.licencia.secondary.SubToken.verificar(
                token = token,
                firmaSpki = B64.dec(firma),
                dispositivoEsperado = deviceIds.deviceId,
                ahora = clock.now(),
                cuotaMaxima = GlContract.SECUNDARIAS_MAX,
            )
        }.getOrNull() ?: return bloquear()
        // Los campos sin firma, si vienen, no pueden contradecir al permiso.
        if (licencia.permitidas != null && licencia.permitidas != datos.permitidas) return bloquear()
        if (licencia.ranura != null && licencia.ranura != datos.ranura) return bloquear()
        // Vale la licencia, con la ranura y la cuota que dice el permiso (lo firmado manda sobre lo declarado).
        return licencia.copy(ranura = datos.ranura, permitidas = datos.permitidas)
    }

    /** Lo único que la secundaria puede afirmar sin un permiso válido: la licencia de la principal no la cubre. */
    private fun bloquear() = LicenciaDto(LicenciaPrincipal.Clase.BLOQUEADA.name)
}
