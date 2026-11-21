package cu.shalev.licencia.redeem

import cu.shalev.core.time.Clock
import cu.shalev.licencia.ActivationResult
import cu.shalev.licencia.LicenseManager
import cu.shalev.licencia.LicenseState
import cu.shalev.licencia.TrustDirectory
import cu.shalev.licencia.contract.TipoLicencia
import cu.shalev.licencia.crypto.B64
import cu.shalev.licencia.crypto.DeviceKey
import cu.shalev.licencia.crypto.EcP256
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
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

    val canCheckStatus: Boolean
        get() = appId.isNotBlank() && endpointStatus.startsWith("https://")

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
            var (status, responseText) = sendPost(endpointRedeem, payload)

            // Reintento automático ante CLOCK_SKEW (máximo 1 reintento con hora del servidor)
            if (status == 400 && responseText.contains("CLOCK_SKEW")) {
                val serverTime = parseServerTime(responseText) ?: consultarHora()
                if (serverTime != null) {
                    licenseManager.resetearRelojServidor(serverTime)
                    val retryRequestedAt = DateTimeFormatter.ISO_INSTANT.format(serverTime)
                    val retryNonceBytes = ByteArray(16).also { random.nextBytes(it) }
                    val retryNonceB64 = B64.enc(retryNonceBytes)
                    val retryProofBytes = RedeemContract.bytesPruebaCanje(
                        appId = appId,
                        code = codeNorm,
                        deviceKeyB64 = deviceKeyB64,
                        requestId = requestId,
                        requestedAt = retryRequestedAt,
                        nonceB64 = retryNonceB64,
                    )
                    val retryProofB64 = B64.enc(deviceKey.sign(retryProofBytes))
                    val retryPayload = buildString {
                        append("{\"v\":1,")
                        append("\"appId\":\"$appId\",")
                        append("\"code\":\"$codeNorm\",")
                        append("\"deviceKey\":\"$deviceKeyB64\",")
                        append("\"requestId\":\"$requestId\",")
                        append("\"requestedAt\":\"$retryRequestedAt\",")
                        append("\"nonce\":\"$retryNonceB64\",")
                        append("\"proof\":\"$retryProofB64\"}")
                    }
                    val retryRes = sendPost(endpointRedeem, retryPayload)
                    status = retryRes.first
                    responseText = retryRes.second
                } else {
                    return RedeemResult.ClockSkew(clock.now())
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

            // Verificar firma con la clave de uso 'canje' (gl-redeem-v1) del directorio
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
            val kid = licenseObj["kid"]?.jsonPrimitive?.contentOrNull ?: "gl-redeem-v1"
            val redeemSpki = TrustDirectory.effectiveDirectory?.redeemKey(kid)
                ?: TrustDirectory.effectiveRedeemKey(kid)
                ?: TrustDirectory.effectiveDirectory?.canjeKeys()?.firstOrNull()

            if (redeemSpki == null) {
                return RedeemResult.Error("UNKNOWN_KID", "Clave pública del servicio no autorizada o no encontrada para kid: $kid")
            }

            val pubKey = runCatching { EcP256.publicKey(redeemSpki) }.getOrNull()
                ?: return RedeemResult.Error("INVALID_KEY", "Clave pública de canje inválida")

            val firmaValida = runCatching {
                EcP256.verify(pubKey, bytesAFirmar, sigBytes)
            }.getOrDefault(false)

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
                else -> LicenseState.Active(tipoEnum, Instant.parse(expiresAt!!), licenseId, secundarias)
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

    suspend fun consultarEstado(licenciaIdManual: String? = null): RedeemResult {
        if (!canCheckStatus) {
            return RedeemResult.Error(
                "ACTIVACION_DESHABILITADA",
                "La consulta de estado comercial no está configurada en este build.",
            )
        }

        val targetLicenseId = licenciaIdManual
            ?: licenseManager.installedLicenseId()
            ?: return RedeemResult.Error("SIN_LICENCIA", "No hay una licencia guardada que consultar.")

        val requestId = UUID.randomUUID().toString()
        val requestedAt = DateTimeFormatter.ISO_INSTANT.format(clock.now())
        val nonceBytes = ByteArray(16).also { random.nextBytes(it) }
        val nonceB64 = B64.enc(nonceBytes)
        val deviceKeyB64 = B64.enc(deviceKey.publicSpki())

        val proofBytes = RedeemContract.bytesPruebaEstado(
            appId = appId,
            licenseId = targetLicenseId,
            deviceKeyB64 = deviceKeyB64,
            requestId = requestId,
            requestedAt = requestedAt,
            nonceB64 = nonceB64,
        )
        val proofB64 = B64.enc(deviceKey.sign(proofBytes))

        val payload = buildString {
            append("{\"v\":1,")
            append("\"appId\":\"$appId\",")
            append("\"licenseId\":\"$targetLicenseId\",")
            append("\"deviceKey\":\"$deviceKeyB64\",")
            append("\"requestId\":\"$requestId\",")
            append("\"requestedAt\":\"$requestedAt\",")
            append("\"nonce\":\"$nonceB64\",")
            append("\"proof\":\"$proofB64\"}")
        }

        return try {
            var (status, responseText) = sendPost(endpointStatus, payload)

            // Reintento automático ante CLOCK_SKEW
            if (status == 400 && responseText.contains("CLOCK_SKEW")) {
                val serverTime = parseServerTime(responseText) ?: consultarHora()
                if (serverTime != null) {
                    licenseManager.resetearRelojServidor(serverTime)
                    val retryRequestedAt = DateTimeFormatter.ISO_INSTANT.format(serverTime)
                    val retryNonceBytes = ByteArray(16).also { random.nextBytes(it) }
                    val retryNonceB64 = B64.enc(retryNonceBytes)
                    val retryProofBytes = RedeemContract.bytesPruebaEstado(
                        appId = appId,
                        licenseId = targetLicenseId,
                        deviceKeyB64 = deviceKeyB64,
                        requestId = requestId,
                        requestedAt = retryRequestedAt,
                        nonceB64 = retryNonceB64,
                    )
                    val retryProofB64 = B64.enc(deviceKey.sign(retryProofBytes))
                    val retryPayload = buildString {
                        append("{\"v\":1,")
                        append("\"appId\":\"$appId\",")
                        append("\"licenseId\":\"$targetLicenseId\",")
                        append("\"deviceKey\":\"$deviceKeyB64\",")
                        append("\"requestId\":\"$requestId\",")
                        append("\"requestedAt\":\"$retryRequestedAt\",")
                        append("\"nonce\":\"$retryNonceB64\",")
                        append("\"proof\":\"$retryProofB64\"}")
                    }
                    val retryRes = sendPost(endpointStatus, retryPayload)
                    status = retryRes.first
                    responseText = retryRes.second
                } else {
                    return RedeemResult.ClockSkew(clock.now())
                }
            }

            if (status !in 200..299) {
                val err = parseError(responseText, status)
                return RedeemResult.Error(err.first, err.second)
            }

            val parsed = json.parseToJsonElement(responseText).jsonObject
            val statusObj = parsed["status"]?.jsonObject
                ?: return RedeemResult.Error("INVALID_RESPONSE", "Respuesta sin objeto de estado")

            val statusContent = statusObj["status"]?.jsonObject
                ?: return RedeemResult.Error("INVALID_RESPONSE", "Respuesta sin contenido de estado")
            val sigB64 = statusObj["sig"]?.jsonPrimitive?.contentOrNull
                ?: return RedeemResult.Error("INVALID_RESPONSE", "Respuesta sin firma")

            // Verificar thumbprint del dispositivo y appId
            val deviceThumbprint = statusContent["deviceThumbprint"]?.jsonPrimitive?.contentOrNull
            val expectedThumbprint = RedeemContract.deviceThumbprint(deviceKey.publicSpki())
            if (deviceThumbprint != expectedThumbprint) {
                return RedeemResult.Error("LICENSE_WRONG_DEVICE", "El estado es para otro dispositivo")
            }

            val stAppId = statusContent["appId"]?.jsonPrimitive?.contentOrNull
            if (stAppId != appId) {
                return RedeemResult.Error("LICENSE_WRONG_APP", "El estado es para otra aplicación")
            }

            val v = statusContent["v"]?.jsonPrimitive?.intOrNull ?: 1
            val licenseId = statusContent["licenseId"]?.jsonPrimitive?.contentOrNull ?: ""
            val type = statusContent["type"]?.jsonPrimitive?.contentOrNull ?: ""
            val licStatus = statusContent["status"]?.jsonPrimitive?.contentOrNull ?: ""
            val issuedAt = statusContent["issuedAt"]?.jsonPrimitive?.contentOrNull ?: ""
            val expiresAt = statusContent["expiresAt"]?.jsonPrimitive?.contentOrNull
            val redemptionId = statusContent["redemptionId"]?.jsonPrimitive?.contentOrNull ?: ""
            val transferredFrom = statusContent["transferredFrom"]?.jsonPrimitive?.contentOrNull
            val secundarias = statusContent["secundarias"]?.jsonPrimitive?.intOrNull ?: 0
            val devicePublicKey = statusContent["devicePublicKey"]?.jsonPrimitive?.contentOrNull
            val checkedAt = statusContent["checkedAt"]?.jsonPrimitive?.contentOrNull ?: requestedAt

            val bytesAFirmar = RedeemContract.bytesFirmaEstado(
                v = v,
                licenseId = licenseId,
                appId = stAppId,
                type = type,
                status = licStatus,
                issuedAt = issuedAt,
                expiresAt = expiresAt,
                deviceThumbprint = deviceThumbprint,
                redemptionId = redemptionId,
                transferredFrom = transferredFrom,
                secundarias = secundarias,
                devicePublicKey = devicePublicKey,
                checkedAt = checkedAt,
            )

            val sigBytes = B64.dec(sigB64)
            val kid = statusObj["kid"]?.jsonPrimitive?.contentOrNull ?: "gl-redeem-v1"
            val redeemSpki = TrustDirectory.effectiveDirectory?.redeemKey(kid)
                ?: TrustDirectory.effectiveRedeemKey(kid)
                ?: TrustDirectory.effectiveDirectory?.canjeKeys()?.firstOrNull()

            if (redeemSpki == null) {
                return RedeemResult.Error("UNKNOWN_KID", "Clave pública del servicio no autorizada para kid: $kid")
            }

            val pubKey = runCatching { EcP256.publicKey(redeemSpki) }.getOrNull()
                ?: return RedeemResult.Error("INVALID_KEY", "Clave de canje no es P-256 válida")

            val firmaValida = runCatching {
                EcP256.verify(pubKey, bytesAFirmar, sigBytes)
            }.getOrDefault(false)

            if (!firmaValida) {
                return RedeemResult.Error("INVALID_LICENSE_SIGNATURE", "Firma de estado inválida")
            }

            if (licStatus == "REVOCADA") {
                licenseManager.marcarRevocada()
                return RedeemResult.Success(LicenseState.Revoked)
            }

            val tipoEnum = when (type) {
                "PERPETUA" -> TipoLicencia.PERPETUA
                "ANUAL" -> TipoLicencia.ANUAL
                else -> TipoLicencia.MENSUAL
            }
            val state = when (tipoEnum) {
                TipoLicencia.PERPETUA -> LicenseState.Perpetual(licenseId, secundarias)
                else -> LicenseState.Active(tipoEnum, Instant.parse(expiresAt!!), licenseId, secundarias)
            }
            RedeemResult.Success(state)
        } catch (e: Exception) {
            RedeemResult.Error("NETWORK", e.message ?: "Error de conexión")
        }
    }

    private fun sendPost(urlStr: String, payload: String): Pair<Int, String> {
        val url = URI(urlStr).toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        return status to text
    }

    private fun parseServerTime(responseText: String): Instant? {
        return try {
            val parsed = json.parseToJsonElement(responseText).jsonObject
            val errObj = parsed["error"]?.jsonObject
            val stRaw = errObj?.get("serverTime")?.jsonPrimitive?.contentOrNull
                ?: parsed["serverTime"]?.jsonPrimitive?.contentOrNull
            if (stRaw != null) Instant.parse(stRaw) else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun consultarHora(): Instant? {
        if (endpointRedeem.isBlank()) return null
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
            val message = err?.get("message")?.jsonPrimitive?.contentOrNull ?: "Error en la operación"
            code to message
        } catch (_: Exception) {
            "HTTP_$httpStatus" to "Error de comunicación ($httpStatus)"
        }
    }
}
