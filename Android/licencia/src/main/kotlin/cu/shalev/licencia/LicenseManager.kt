package cu.shalev.licencia

import cu.shalev.licencia.contract.IdLicencia
import cu.shalev.licencia.contract.ListaRevocaciones

import cu.shalev.core.validation.Phone
import cu.shalev.core.validation.Validators
import cu.shalev.licencia.contract.Envelope
import cu.shalev.licencia.contract.GlJson
import cu.shalev.licencia.contract.LicenciaCorta
import cu.shalev.licencia.contract.LicensePayload
import cu.shalev.licencia.contract.MensajesLicencia
import cu.shalev.licencia.contract.SolicitudCifrada
import cu.shalev.licencia.contract.RequestPayload
import cu.shalev.licencia.contract.TipoLicencia
import cu.shalev.licencia.contract.Via
import cu.shalev.licencia.crypto.B64
import cu.shalev.licencia.crypto.DeviceKey
import cu.shalev.licencia.crypto.EcP256
import cu.shalev.licencia.crypto.GlLicenseOpener
import cu.shalev.licencia.crypto.fail
import cu.shalev.core.time.Clock
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import cu.shalev.licencia.contract.CodigoLicencia
import cu.shalev.licencia.contract.GlContract

/** id local de la licencia que instala la clave maestra de desarrollo (no viene de GL). */
const val ID_MAESTRA_LICENCIA: String = "master-license-local"

/**
 * Licencia guardada: el envelope COMPLETO + los metadatos que pide el contrato (checklist §5: id, venceEn,
 * tipo, deviceId). Los metadatos son una caché de lectura: en cada arranque se vuelve a verificar el
 * envelope y, si no coinciden con lo descifrado, la licencia se descarta. Nunca un flag "isLicensed".
 * 0.23.0: [envelopeJson] guarda la licencia corta cifrada (`SPVI2:…`, se reconoce por el prefijo); así el
 * almacenamiento no cambia. Una licencia larga (envelope v1) instalada antes se sigue re-verificando hasta que venza.
 */
data class StoredLicense(
    val envelopeJson: String,
    val licenseId: String,
    val emitidaEn: String,
    val tipo: TipoLicencia? = null,
    val venceEn: String? = null,
    val deviceId: String? = null,
    /**
     * 0.31.0: la licencia se activó con la clave maestra de desarrollo (`GLMK-PERM-MAXS-9999`). Solo vale en builds
     * `DEBUG`: en release se descarta al evaluar (mitigación 1, Opción A).
     */
    val maestra: Boolean = false,
)

/** Licencia instalada y verificada (para el panel de Licencia). */
data class InstalledLicense(
    val id: String,
    val tipo: TipoLicencia,
    val emitidaEn: Instant,
    val venceEn: Instant?,
    /** 0.21.0 (C4): apps secundarias que cubre (ausente en la licencia = [GlContract.SECUNDARIAS_DEFECTO]). */
    val secundarias: Int = GlContract.SECUNDARIAS_DEFECTO,
)

/** Resultado de evaluar el estado: [license] solo si hay una licencia auténtica instalada. */
data class Evaluation(val state: LicenseState, val license: InstalledLicense?)

interface LicenseStore {
    suspend fun license(): StoredLicense?
    suspend fun saveLicense(license: StoredLicense)
    suspend fun trialStart(): Instant?
    suspend fun setTrialStart(instant: Instant)
    suspend fun lastSeen(): Instant?
    suspend fun setLastSeen(instant: Instant)
    /** Instante en que la licencia de este teléfono se transfirió a otro (recuperación). null = nunca. */
    suspend fun migratedAt(): Instant?
    /** Borra la licencia guardada y registra la transferencia. Lo emitido antes de [at] ya no se acepta aquí. */
    suspend fun markMigrated(at: Instant)
    /**
     * 0.25.0: instante en que la licencia de este teléfono apareció en la lista pública de revocadas (se recuperó en
     * otro teléfono). null = nunca. Se borra al activar una licencia emitida después.
     */
    suspend fun revokedAt(): Instant? = null
    /** 0.25.0: como [markMigrated] y además deja constancia de la revocación (pantalla «Licencia transferida»). */
    suspend fun markRevoked(at: Instant) = markMigrated(at)
    suspend fun clearRevoked() = Unit
}

data class SolicitudInput(
    val nombre: String,
    val apellidos: String,
    val ci: String,
    val telefono: String,
    val tipo: TipoLicencia,
    val via: Via,
    /** 0.21.0 (C4): apps secundarias que se pagan con esta licencia. */
    val secundarias: Int = GlContract.SECUNDARIAS_DEFECTO,
    /** 0.22.0 (L-e): id de la licencia instalada que se renueva ([LicenseManager.licenciaRenovable]); null = primera. */
    val renueva: String? = null,
    /** 0.25.0: id de la licencia que se RECUPERA (teléfono nuevo o reinstalado). Excluyente con [renueva]. */
    val recupera: String? = null,
    /** ID de la licencia vigente cuyas apps secundarias se amplían. Excluyente con [renueva] y [recupera]. */
    val amplia: String? = null,
    /** Secundarias actuales de la licencia que se amplía (para el texto legible de [MensajesLicencia.ampliacion]). */
    val secundariasPrevias: Int = 0,
)

/**
 * 0.23.0: solicitud lista para enviar. [texto] = texto introductorio + renglón en blanco + [codigo]; [codigo] = la
 * solicitud cifrada `SPVIR1:…` (lo que va en el QR por WhatsApp).
 */
data class SolicitudGenerada(val texto: String, val codigo: String)

sealed interface ActivationResult {
    data class Accepted(val state: LicenseState) : ActivationResult
    data object NotFound : ActivationResult
    data object Rejected : ActivationResult
    /** Auténtica pero más antigua que la instalada: se ignora. */
    data object Outdated : ActivationResult
}

class LicenseManager(
    private val store: LicenseStore,
    private val deviceKey: DeviceKey,
    val deviceId: String,
    glEcdhSpki: ByteArray?,
    glSignSpkis: List<ByteArray>,
    private val clock: Clock,
    private val random: SecureRandom = SecureRandom(),
    private val trialDays: Int = TRIAL_DAYS,
    /** 0.26.0 (P74): copias de la fecha de la prueba fuera de la app (sobreviven a desinstalar). null = solo la interna. */
    private val registroExterno: cu.shalev.licencia.prueba.RegistroExterno? = null,
    /** 0.26.0 (P74): reloj monótono (mismo arranque); se suma a la detección por `lastSeen`. */
    private val detectorRetroceso: cu.shalev.licencia.prueba.DetectorRetroceso? = null,
    /**
     * 0.31.0: build de depuración. Habilita la clave maestra de desarrollo (mitigación 1, Opción A). Por defecto
     * `false` (fail-closed); la app de release no la activa nunca.
     */
    private val esDebug: Boolean = false,
    /** 0.31.0: firma los sub-tokens de las apps secundarias. Sin firmante no se puede emitir ninguno. */
    private val firmante: cu.shalev.licencia.secondary.FirmanteLicencia? = null,
) {
    private val glEcdh = glEcdhSpki?.let(EcP256::publicKey)
    private val signKeys = glSignSpkis.map(EcP256::publicKey)
    private val opener = GlLicenseOpener(signKeys, deviceKey)

    /** false si el build no trae las claves de GL: no se pueden emitir solicitudes. */
    val canRequest: Boolean = glEcdh != null && glSignSpkis.isNotEmpty()

    val glEcdhFingerprint: String? = glEcdhSpki?.let(EcP256::fingerprint)

    init {
        require(deviceId.startsWith(cu.shalev.licencia.contract.DEVICE_ID_PREFIX) && Validators.deviceId(deviceId)) {
            "deviceId inválido"
        }
    }

    suspend fun state(): LicenseState = evaluate().state

    /**
     * Estado + licencia instalada. Orden: reloj (anti-retroceso) → licencia guardada (re-verificada
     * criptográficamente) → periodo de prueba.
     */
    suspend fun evaluate(): Evaluation {
        val now = clock.now()
        // 0.26.0 (P74): antes de nada, juntar con el registro externo: gana el inicio de prueba MÁS ANTIGUO y la última
        // fecha vista MÁS RECIENTE (así ni reinstalar reinicia la prueba ni esconde un reloj atrasado). Las copias que no
        // descifran se ignoran y se reescriben: nunca bloquean.
        registroExterno?.let { reg ->
            // Una sola pasada por arranque/refresco: si aún no hay inicio de prueba, el de ahora (el mismo que se guardaría abajo).
            val inicio = store.trialStart() ?: now
            val ext = runCatching { reg.sincronizar(inicio.toEpochMilli(), store.lastSeen()?.toEpochMilli(), trialDays) }.getOrNull()
            ext?.firstInstall?.let(Instant::ofEpochMilli)?.let { f -> if (store.trialStart()?.isAfter(f) != false) store.setTrialStart(f) }
            ext?.lastSeen?.let(Instant::ofEpochMilli)?.let { l -> if (store.lastSeen()?.isBefore(l) != false) store.setLastSeen(l) }
        }
        val last = store.lastSeen()
        val migrated = store.migratedAt()
        val stored = store.license()
        val verified = stored?.let { verifyStored(it, now) }
            ?.takeIf { migrated == null || it.second.emitidaEn.isAfter(migrated) }
        if (last != null && now.isBefore(last.minus(CLOCK_TOLERANCE))) {
            return Evaluation(LicenseState.ClockTampered, verified?.second)
        }
        val detector = detectorRetroceso
        if (detector != null && runCatching { detector.retrocedio(now.toEpochMilli()) }.getOrDefault(false)) {
            return Evaluation(LicenseState.ClockTampered, verified?.second)
        }
        if (last == null || now.isAfter(last)) store.setLastSeen(now)

        verified?.let { return Evaluation(it.first, it.second) }

        // Tras una transferencia, este teléfono ya no tiene periodo de prueba: solo una licencia emitida después.
        if (migrated != null) return Evaluation(if (store.revokedAt() != null) LicenseState.Revoked else LicenseState.TrialExpired, null)
        val start = store.trialStart() ?: now.also { store.setTrialStart(it) }
        val left = trialDays - Duration.between(start, now).toDays()
        return Evaluation(if (left > 0) LicenseState.Trial(left.toInt()) else LicenseState.TrialExpired, null)
    }

    /**
     * 0.23.0: construye el mensaje de solicitud listo para enviar por [SolicitudInput.via]: texto introductorio con los
     * datos del solicitante + renglón en blanco + solicitud cifrada (`SPVIR1:`, [SolicitudCifrada]).
     */
    fun buildRequest(input: SolicitudInput): SolicitudGenerada {
        val gl = requireNotNull(glEcdh) { "Claves de GL no configuradas en este build" }
        val telefono = requireNotNull(Phone.normalize(input.telefono)) { "Teléfono inválido" }
        require(Validators.nombre(input.nombre)) { "Nombre inválido" }
        require(Validators.nombre(input.apellidos)) { "Apellidos inválidos" }
        require(Validators.ci(input.ci)) { "Carnet de identidad inválido" }
        require(input.secundarias in 0..GlContract.SECUNDARIAS_MAX) { "Número de apps secundarias inválido" }
        require(input.renueva == null || input.renueva.length in 1..64) { "Licencia a renovar inválida" }
        require(input.recupera == null || IdLicencia.extraer(input.recupera) == input.recupera.trim().lowercase()) { "Licencia a recuperar inválida" }
        require(input.amplia == null || input.amplia.length in 1..64) { "Licencia a ampliar inválida" }
        require(listOf(input.renueva, input.recupera, input.amplia).count { it != null } <= 1) {
            "Renovar, recuperar y ampliar son excluyentes"
        }
        require(
            input.amplia == null ||
                (input.secundariasPrevias in 0 until GlContract.SECUNDARIAS_MAX &&
                    input.secundarias in (input.secundariasPrevias + 1)..GlContract.SECUNDARIAS_MAX),
        ) { "La ampliación debe incrementar las apps secundarias actuales (máx. ${GlContract.SECUNDARIAS_MAX})" }

        val nonce = B64.urlNoPad(ByteArray(16).also(random::nextBytes))
        val payload = RequestPayload(
            nombre = input.nombre.trim(),
            apellidos = input.apellidos.trim(),
            ci = input.ci.trim().uppercase(),
            via = input.via,
            telefono = telefono,
            deviceId = deviceId,
            tipo = input.tipo,
            solicitadaEn = clock.now().truncatedTo(ChronoUnit.SECONDS).toString(),
            nonce = nonce,
            devicePub = B64.urlNoPad(EcP256.comprimir(deviceKey.publicSpki())),
            secundarias = input.secundarias,
            renueva = input.renueva,
            recupera = input.recupera?.trim()?.lowercase(),
            amplia = input.amplia,
        )
        val plain = GlJson.encoder.encodeToString(RequestPayload.serializer(), payload).toByteArray(Charsets.UTF_8)
        val codigo = SolicitudCifrada.sellar(plain, gl)
        plain.fill(0)
        return SolicitudGenerada(MensajesLicencia.solicitud(payload, codigo, input.secundariasPrevias), codigo)
    }

    /**
     * 0.22.0 (L-e): id de la licencia que se renovaría con una solicitud nueva: la instalada, auténtica y con
     * vencimiento (activa o vencida). null si no hay licencia, si es perpetua o si no verifica.
     */
    suspend fun licenciaRenovable(): String? = evaluate().license?.takeIf { it.venceEn != null }?.id

    /**
     * Licencia instalada y vigente cuyas apps secundarias aún admiten ampliación (< [GlContract.SECUNDARIAS_MAX]).
     * Excluye la clave maestra de desarrollo (`ID_MAESTRA`), que ya trae el máximo y no proviene de GL.
     */
    suspend fun licenciaAmpliable(): InstalledLicense? {
        val ev = evaluate()
        val lic = ev.license ?: return null
        val vigente = ev.state is LicenseState.Active || ev.state is LicenseState.Perpetual
        return lic.takeIf { vigente && it.id != ID_MAESTRA_LICENCIA && it.secundarias < GlContract.SECUNDARIAS_MAX }
    }

    /**
     * Procesa el mensaje de licencia pegado, compartido o leído de un QR. Acepta, en este orden:
     *  1. la **clave maestra de desarrollo** (`GLMK-PERM-MAXS-9999`), solo en builds `DEBUG` (mitigación 1);
     *  2. la licencia corta cifrada `SPVI2:…` (el formato de Shalev ≥ 0.23.1); el texto de arriba es informativo.
     * Una licencia **larga** (envelope v1) ya no se activa: [ActivationResult.NotFound] (decisión de la 0.23.0; si
     * estaba instalada, se sigue re-verificando en cada evaluación, ver [verifyStored]). Cualquier otra cosa:
     * [ActivationResult.NotFound] o [ActivationResult.Rejected], nunca una excepción.
     */
    suspend fun activate(message: String): ActivationResult {
        if (esClaveMaestra(message)) return activarMaestra()
        val corta = LicenciaCorta.extraer(message) ?: return ActivationResult.NotFound
        return activarCorta(corta)
    }

    /** ¿El texto es la clave maestra de desarrollo? Solo en builds `DEBUG` (en release siempre `false`). */
    fun esClaveMaestra(texto: String): Boolean = CodigoLicencia.esMaestra(texto, esDebug)

    /**
     * Activa la clave maestra: licencia PERPETUA con el máximo de apps secundarias (10), sin envelope ni firma (no
     * viaja por WhatsApp: la escribe el desarrollador). Queda marcada como [StoredLicense.maestra] para que un build
     * de release la descarte al evaluar.
     */
    private suspend fun activarMaestra(): ActivationResult {
        val current = store.license()
        if (current != null && current.maestra) return ActivationResult.Accepted(LicenseState.Perpetual(ID_MAESTRA, GlContract.SECUNDARIAS_MAX))
        store.clearRevoked()
        store.saveLicense(
            StoredLicense(
                envelopeJson = CodigoLicencia.MAESTRA,
                licenseId = ID_MAESTRA,
                emitidaEn = clock.now().toString(),
                tipo = TipoLicencia.PERPETUA,
                venceEn = null,
                deviceId = deviceId,
                maestra = true,
            ),
        )
        return ActivationResult.Accepted(LicenseState.Perpetual(ID_MAESTRA, GlContract.SECUNDARIAS_MAX))
    }

    private suspend fun activarCorta(codigo: LicenciaCorta.Codigo): ActivationResult {
        val payload = runCatching { abrirCorta(codigo) }.getOrNull() ?: return ActivationResult.Rejected
        val state = runCatching {
            LicenseValidator.evaluate(payload, payload.id, deviceId, deviceKey.publicSpki(), clock.now())
        }.getOrNull() ?: return ActivationResult.Rejected
        return instalar(payload, state, envelopeJson = codigo.texto, licenseId = payload.id)
    }

    /**
     * 0.31.0 (mitigación 7, Opción A): emite el **sub-token** que autoriza a [dispositivo] como app secundaria en la
     * ranura [ranura]. Solo con una licencia propia auténtica y desbloqueada (`Adquirida`/`Prueba`), y solo si hay
     * firmante en el build. `null` = no se puede emitir (sin firmante, app bloqueada, cuota 0 o ranura fuera de rango).
     */
    suspend fun emitirSubToken(dispositivo: String, ranura: Int): String? {
        val f = firmante ?: return null
        val ev = evaluate()
        if (!ev.state.unlocked) return null
        val cuota = ev.state.secundariasPermitidas
        if (cuota <= 0 || ranura !in 1..cuota) return null
        val id = ev.license?.id ?: ID_PRUEBA
        val vence = ev.license?.venceEn
        return runCatching {
            cu.shalev.licencia.secondary.SubToken.emitir(f, id, cuota, vence, dispositivo, ranura)
        }.getOrNull()
    }

    /** Cuota de apps secundarias de la licencia instalada (0 si no hay licencia válida). */
    suspend fun cuotaSecundarias(): Int {
        val ev = evaluate()
        return if (ev.state.unlocked) ev.state.secundariasPermitidas else 0
    }

    /** No se instala una licencia más antigua que la actual ni anterior a la transferencia. */
    private suspend fun instalar(payload: LicensePayload, state: LicenseState, envelopeJson: String, licenseId: String): ActivationResult {
        val current = store.license()
        // Una licencia auténtica de GL siempre manda sobre la clave maestra de desarrollo (que es solo una ayuda de
        // depuración), aunque se instalara después.
        if (current != null && !current.maestra && Instant.parse(payload.emitidaEn).isBefore(Instant.parse(current.emitidaEn))) {
            return ActivationResult.Outdated
        }
        // Una licencia anterior a la transferencia (p. ej. el mensaje viejo aún en WhatsApp) ya no vale aquí.
        val migrated = store.migratedAt()
        if (migrated != null && !Instant.parse(payload.emitidaEn).isAfter(migrated)) {
            return ActivationResult.Outdated
        }
        store.clearRevoked()
        store.saveLicense(
            StoredLicense(
                envelopeJson = envelopeJson,
                licenseId = licenseId,
                emitidaEn = payload.emitidaEn,
                tipo = payload.tipo,
                venceEn = payload.venceEn,
                deviceId = payload.deviceId,
            ),
        )
        return ActivationResult.Accepted(state)
    }

    /**
     * Licencia corta cifrada → payload equivalente (sin datos personales). Falla (genérico) si la firma no es de GL, si
     * no se descifra con la clave de ESTE teléfono (Keystore) o si la huella no es la de este deviceId y esta clave.
     */
    private fun abrirCorta(codigo: LicenciaCorta.Codigo): LicensePayload {
        if (!firmaCortaValida(codigo)) fail()
        val cuerpo = LicenciaCorta.descifrar(codigo, deviceKey)
        val c = LicenciaCorta.leer(cuerpo) ?: fail()
        if (!MessageDigest.isEqual(c.huella, huellaPropia())) fail()
        return LicensePayload(
            nombre = "", apellidos = "", ci = "", deviceId = deviceId, tipo = c.tipo,
            id = c.id, emitidaEn = c.emitidaEn.toString(), venceEn = c.venceEn?.toString(), estado = c.estado,
            secundarias = c.secundarias,
        )
    }

    private fun huellaPropia(): ByteArray = LicenciaCorta.huella(deviceId, EcP256.comprimir(deviceKey.publicSpki()))

    private fun firmaCortaValida(codigo: LicenciaCorta.Codigo): Boolean = runCatching {
        val der = EcP256.rawToDer(codigo.firma)
        signKeys.any { EcP256.verify(it, codigo.mensajeFirmado, der) }
    }.getOrDefault(false)

    /**
     * 0.25.0: aplica la lista pública de revocadas (texto de `revocadas.json`). Si su firma es de GL y contiene la
     * licencia guardada en este teléfono, la invalida ([LicenseStore.markRevoked]) y devuelve true. Cualquier otro caso
     * (sin licencia, lista falsa o corrupta, licencia no incluida) no cambia nada.
     */
    suspend fun aplicarRevocaciones(texto: String): Boolean {
        val id = store.license()?.licenseId ?: return false
        val datos = ListaRevocaciones.verificar(texto, signKeys) ?: return false
        if (!ListaRevocaciones.contiene(datos, id)) return false
        store.markRevoked(clock.now())
        return true
    }

    /** 0.25.0: true = la licencia de este teléfono se revocó (se recuperó en otro). */
    suspend fun revocada(): Boolean = store.revokedAt() != null && store.migratedAt() != null &&
        evaluate().state == LicenseState.Revoked

    private fun openPayload(env: Envelope, licenseId: String): LicensePayload {
        val plain = opener.open(env, licenseId)
        return GlJson.decoder.decodeFromString(LicensePayload.serializer(), plain.toString(Charsets.UTF_8))
    }

    /** null = no verifica (alterada, otra clave de dispositivo, metadatos incoherentes…): se ignora. */
    private fun verifyStored(stored: StoredLicense, now: Instant): Pair<LicenseState, InstalledLicense>? = runCatching {
        // Clave maestra: perpetua y con 10 secundarias, pero SOLO en builds DEBUG (mitigación 1, Opción A). En release
        // la licencia instalada con la clave maestra se ignora, igual que si no existiera.
        if (stored.maestra) {
            if (!esDebug) return@runCatching null
            return@runCatching LicenseState.Perpetual(ID_MAESTRA, GlContract.SECUNDARIAS_MAX) to InstalledLicense(
                ID_MAESTRA, TipoLicencia.PERPETUA, Instant.parse(stored.emitidaEn), null, GlContract.SECUNDARIAS_MAX,
            )
        }
        val p = if (stored.envelopeJson.startsWith(LicenciaCorta.PREFIJO)) {
            abrirCorta(LicenciaCorta.extraer(stored.envelopeJson) ?: fail())
        } else {
            openPayload(GlJson.envelopeDecoder.decodeFromString(Envelope.serializer(), stored.envelopeJson), stored.licenseId)
        }
        val state = LicenseValidator.evaluate(p, stored.licenseId, deviceId, deviceKey.publicSpki(), now)
        val coherente = p.emitidaEn == stored.emitidaEn &&
            (stored.tipo == null || stored.tipo == p.tipo) &&
            (stored.venceEn == null || stored.venceEn == p.venceEn) &&
            (stored.deviceId == null || stored.deviceId == p.deviceId)
        if (!coherente) return@runCatching null
        state to InstalledLicense(
            p.id, p.tipo, Instant.parse(p.emitidaEn), p.venceEn?.let(Instant::parse), p.secundarias ?: GlContract.SECUNDARIAS_DEFECTO,
        )
    }.getOrNull()

    companion object {
        const val TRIAL_DAYS = 7

        /** id local de la licencia instalada con la clave maestra de desarrollo (no viene de GL). */
        const val ID_MAESTRA: String = ID_MAESTRA_LICENCIA

        /** id con el que se emiten los sub-tokens mientras la app está en periodo de prueba (no hay licencia). */
        const val ID_PRUEBA: String = "prueba"

        /** Margen para ajustes de hora/zona antes de considerar manipulación del reloj. */
        val CLOCK_TOLERANCE: Duration = Duration.ofHours(2)
    }
}
