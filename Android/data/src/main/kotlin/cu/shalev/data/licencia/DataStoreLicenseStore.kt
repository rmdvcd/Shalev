package cu.shalev.data.licencia

import cu.shalev.data.local.SecureDataStore
import cu.shalev.data.local.ShalevJson
import cu.shalev.licencia.LicenseStore
import cu.shalev.licencia.StoredLicense
import cu.shalev.licencia.contract.TipoLicencia
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * [LicenseStore] propio de Shalev sobre el DataStore cifrado (no reutiliza nada del almacenamiento de GL).
 * Guarda el envelope COMPLETO + id/emitidaEn/tipo/venceEn/deviceId (checklist §5 del contrato). Los
 * metadatos son caché de lectura: LicenseManager los contrasta con el envelope descifrado en cada arranque.
 */
@Singleton
class DataStoreLicenseStore @Inject constructor(private val ds: SecureDataStore) : LicenseStore {

    override suspend fun license(): StoredLicense? = ds.get(K_LICENSE)?.let(::decodificar)

    override suspend fun saveLicense(license: StoredLicense) = ds.put(K_LICENSE, codificar(license))

    override suspend fun trialStart(): Instant? = ds.get(K_TRIAL)?.toLongOrNull()?.let(Instant::ofEpochMilli)
    override suspend fun setTrialStart(instant: Instant) = ds.put(K_TRIAL, instant.toEpochMilli().toString())
    override suspend fun lastSeen(): Instant? = ds.get(K_LAST)?.toLongOrNull()?.let(Instant::ofEpochMilli)
    override suspend fun setLastSeen(instant: Instant) = ds.put(K_LAST, instant.toEpochMilli().toString())

    override suspend fun migratedAt(): Instant? = ds.get(K_MIGRADO)?.toLongOrNull()?.let(Instant::ofEpochMilli)

    /**
     * Orden deliberado: primero la marca y después el borrado. Si el proceso muere entre ambos, la licencia
     * que quede es anterior a la marca y LicenseManager ya la ignora.
     */
    override suspend fun markMigrated(at: Instant) {
        ds.put(K_MIGRADO, at.toEpochMilli().toString())
        ds.put(K_LICENSE, null)
    }

    override suspend fun revokedAt(): Instant? = ds.get(K_REVOCADO)?.toLongOrNull()?.let(Instant::ofEpochMilli)

    /** 0.25.0: primero la marca de revocación y después la de migración (que borra la licencia). */
    override suspend fun markRevoked(at: Instant) {
        ds.put(K_REVOCADO, at.toEpochMilli().toString())
        markMigrated(at)
    }

    override suspend fun clearRevoked() { if (ds.get(K_REVOCADO) != null) ds.put(K_REVOCADO, null) }

    /** Formato persistido: nombres cortos (env/id/at); los campos añadidos después son opcionales. */
    @Serializable
    internal data class Guardada(
        @SerialName("env") val envelope: String,
        @SerialName("id") val id: String,
        @SerialName("at") val emitidaEn: String,
        val tipo: String? = null,
        val venceEn: String? = null,
        val deviceId: String? = null,
    )

    internal companion object {
        const val K_LICENSE = "lic.envelope"
        const val K_TRIAL = "lic.trial_start"
        const val K_LAST = "lic.last_seen"
        const val K_MIGRADO = "lic.migrated_at"
        const val K_REVOCADO = "lic.revoked_at"

        fun codificar(l: StoredLicense): String = ShalevJson.encodeToString(
            Guardada.serializer(),
            Guardada(l.envelopeJson, l.licenseId, l.emitidaEn, l.tipo?.name, l.venceEn, l.deviceId),
        )

        /** Ilegible = sin licencia (estado seguro: prueba o bloqueo). */
        fun decodificar(raw: String): StoredLicense? = runCatching {
            val g = ShalevJson.decodeFromString(Guardada.serializer(), raw)
            StoredLicense(
                envelopeJson = g.envelope, licenseId = g.id, emitidaEn = g.emitidaEn,
                tipo = g.tipo?.let { t -> TipoLicencia.entries.firstOrNull { it.name == t } ?: return@runCatching null },
                venceEn = g.venceEn, deviceId = g.deviceId,
            )
        }.getOrNull()
    }
}
