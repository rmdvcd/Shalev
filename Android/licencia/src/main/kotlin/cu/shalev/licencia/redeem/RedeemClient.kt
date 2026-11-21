package cu.shalev.licencia.redeem

import cu.shalev.core.time.Clock
import cu.shalev.licencia.ActivationResult
import cu.shalev.licencia.DeviceKey
import cu.shalev.licencia.LicenseManager
import cu.shalev.licencia.LicenseState
import cu.shalev.licencia.TrustDirectory
import cu.shalev.licencia.contract.GlContract
import cu.shalev.licencia.contract.TipoLicencia
import cu.shalev.licencia.crypto.B64
import cu.shalev.licencia.crypto.EcP256
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI
import java.security.SecureRandom
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

sealed interface RedeemResult {
    data class Success(val state: LicenseState) : RedeemResult
    data class Absorbed(val state: LicenseState, val mensaje: String) : RedeemResult
    data class ClockSkew(val serverTime: Instant) : RedeemResult
    data class Error(val code: String, val message: String) : RedeemResult
}

class RedeemClient(
    private val deviceKey: DeviceKey,
    private val licenseManager: LicenseManager,
    private val clock: Clock,
    private val appId: String = RedeemContract.DEFAULT_APP_ID,
    private val endpointRedeem: String = "",
    private val endpointStatus: String = "",
    private val random: SecureRandom = SecureRandom(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    val canRedeem: Boolean
        get() = appId.isNotBlank() && endpointRedeem.startsWith("https://")

    suspend fun canjear(codigo: String): RedeemResult {
        if (!canRedeem) {
            return RedeemResult.Error(
                "ACTIVACION_DESHABILITADA",
                "El canje comercial directo no está configurado en este build.",
            )
        }

        val codeNorm = try {
            RedeemContract.normalizarCodigo(codigo)
        } catch (e: Exception) {
            return RedeemResult.Error("INVALID_CODE", e.message ?: "Código inválido")
        }

        val requestId = UUID.randomUUID().toString()
        val requestedAt = DateTimeFormatter.ISO_INSTANT.format(clock.now())
        val nonceBytes = ByteArray(16).also { random.nextBytes(it) }
        val nonceB64 = B64.enc(nonceBytes)
        val deviceKeyB64 = B64.enc(deviceKey.publicSpki())

        val proofBytes = RedeemContract.bytesPruebaCanje(
            appId = appId,
            code = codeNorm,
            deviceKeyB64 = deviceKeyB64,
            requestId = requestId,
            requestedAt = requestedAt,
            nonceB64 = nonceB64,
        )
        val proofB64 = B64.enc(deviceKey.sign(proofBytes))

        val payload = buildString {
            append("{\"v\":1,")
            append("\"appId\":\"$appId\",")
            append("\"code\":\"$codeNorm\",")
            append("\"deviceKey\":\"$deviceKeyB64\",")
            append("\"requestId\":\"$requestId\",")
            append("\"requestedAt\":\"$requestedAt\",")
            append("\"nonce\":\"$nonceB64\",")
            append("\"proof\":\"$proofB64\"}")
        }

        return try {
            val url = URI(endpointRedeem).toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() } ?: ""

            if (status == 400 && responseText.contains("CLOCK_SKEW")) {
                val parsed = json.parseToJsonElement(responseText).jsonObject
                val errObj = parsed["error"]?.jsonObject
                val stRaw = errObj?.get("serverTime")?.jsonPrimitive?.contentOrNull
                    ?: parsed["serverTime"]?.jsonPrimitive?.contentOrNull
                if (stRaw != null) {
                    val serverTime = Instant.parse(stRaw)
                    licenseManager.resetearRelojServidor(serverTime)
                    return RedeemResult.ClockSkew(serverTime)
                }
            }

            if (status !in 200..299) {
                val err = parseError(responseText, status)
                return RedeemResult.Error(err.first, err.second)
            }

            val parsed = json.parseToJsonElement(responseText).jsonObject
            val licenseObj = parsed["license"]?.jsonObject
                ?: return RedeemResult.Error("INVALID_RESPONSE", "Respuesta sin objeto de licencia")

            val licenseContent = licenseObj["license"]?.jsonObject
                ?: return RedeemResult.Error("INVALID_RESPONSE", "Respuesta sin contenido de licencia")
            val sigB64 = licenseObj["sig"]?.jsonPrimitive?.contentOrNull
                ?: return RedeemResult.Error("INVALID_RESPONSE", "Respuesta sin firma")

            // Verificar thumbprint del dispositivo
            val deviceThumbprint = licenseContent["deviceThumbprint"]?.jsonPrimitive?.contentOrNull
            val expectedThumbprint = RedeemContract.deviceThumbprint(deviceKey.publicSpki())
            if (deviceThumbprint != expectedThumbprint) {
                return RedeemResult.Error("LICENSE_WRONG_DEVICE", "La licencia es para otro dispositivo")
            }

            // Verificar appId
            val licAppId = licenseContent["appId"]?.jsonPrimitive?.contentOrNull
            if (licAppId != appId) {
                return RedeemResult.Error("LICENSE_WRONG_APP", "La licencia es para otra aplicación")
            }

            // Verificar firma con las claves del directorio o del build
            val v = licenseContent["v"]?.jsonPrimitive?.intOrNull ?: 1
            val licenseId = licenseContent["licenseId"]?.jsonPrimitive?.contentOrNull ?: ""
            val type = licenseContent["type"]?.jsonPrimitive?.contentOrNull ?: ""
            val licStatus = licenseContent["status"]?.jsonPrimitive?.contentOrNull ?: ""
            val issuedAt = licenseContent["issuedAt"]?.jsonPrimitive?.contentOrNull ?: ""
            val expiresAt = licenseContent["expiresAt"]?.jsonPrimitive?.contentOrNull
            val redemptionId = licenseContent["redemptionId"]?.jsonPrimitive?.contentOrNull ?: ""
            val transferredFrom = licenseContent["transferredFrom"]?.jsonPrimitive?.contentOrNull
            val secundarias = licenseContent["secundarias"]?.jsonPrimitive?.intOrNull ?: 0
            val devicePublicKey = licenseContent["devicePublicKey"]?.jsonPrimitive?.contentOrNull

            val bytesAFirmar = RedeemContract.bytesFirmaLicencia(
                v = v,
                licenseId = licenseId,
                appId = licAppId,
                type = type,
                status = licStatus,
                issuedAt = issuedAt,
                expiresAt = expiresAt,
                deviceThumbprint = deviceThumbprint,
                redemptionId = redemptionId,
                transferredFrom = transferredFrom,
                secundarias = secundarias,
                devicePublicKey = devicePublicKey,
            )

            val sigBytes = B64.dec(sigB64)
            val issuedInstant = Instant.parse(issuedAt)
            val signKeys = TrustDirectory.effectiveDirectory?.signKeysFor(issuedInstant.epochSecond)
                ?: cu.shalev.licencia.LicenseTrust.signSpkis()

            val firmaValida = signKeys.any { spki ->
                runCatching {
                    EcP256.verify(EcP256.publicKey(spki), bytesAFirmar, sigBytes)
                }.getOrDefault(false)
            }

            if (!firmaValida) {
                return RedeemResult.Error("INVALID_LICENSE_SIGNATURE", "Firma de licencia inválida")
            }

            // Activar a través de LicenseManager
            val tipoEnum = when (type) {
                "PERPETUA" -> TipoLicencia.PERPETUA
                "ANUAL" -> TipoLicencia.ANUAL
                else -> TipoLicencia.MENSUAL
            }
            val state = when (tipoEnum) {
                TipoLicencia.PERPETUA -> LicenseState.Perpetual(licenseId, secundarias)
                else -> {
                    val days = java.time.Duration.between(clock.now(), Instant.parse(expiresAt!!)).toDays().toInt()
                    LicenseState.Active(licenseId, days, tipoEnum, secundarias)
                }
            }

            when (val actRes = licenseManager.activarDirecta(licenseId, issuedAt, expiresAt, tipoEnum, secundarias, responseText)) {
                is ActivationResult.Accepted -> RedeemResult.Success(state)
                is ActivationResult.Absorbed -> RedeemResult.Absorbed(actRes.state, actRes.mensaje)
                is ActivationResult.Outdated -> RedeemResult.Error("OUTDATED", "Licencia anterior a la actual")
                else -> RedeemResult.Error("REJECTED", "No se pudo activar la licencia")
            }
        } catch (e: Exception) {
            RedeemResult.Error("NETWORK", e.message ?: "Error de conexión")
        }
    }

    suspend fun consultarHora(): Instant? {
        val base = endpointRedeem.substringBeforeLast("/")
        val timeUrl = "$base/time"
        return try {
            val url = URI(timeUrl).toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            if (conn.responseCode != 200) return null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val parsed = json.parseToJsonElement(text).jsonObject
            val nowStr = parsed["now"]?.jsonPrimitive?.contentOrNull ?: return null
            val serverTime = Instant.parse(nowStr)
            licenseManager.resetearRelojServidor(serverTime)
            serverTime
        } catch (_: Exception) {
            null
        }
    }

    private fun parseError(response: String, httpStatus: Int): Pair<String, String> {
        return try {
            val obj = json.parseToJsonElement(response).jsonObject
            val err = obj["error"]?.jsonObject
            val code = err?.get("code")?.jsonPrimitive?.contentOrNull ?: "HTTP_$httpStatus"
            val message = err?.get("message")?.jsonPrimitive?.contentOrNull ?: "Error en el canje"
            code to message
        } catch (_: Exception) {
            "HTTP_$httpStatus" to "Error de comunicación ($httpStatus)"
        }
    }
}
