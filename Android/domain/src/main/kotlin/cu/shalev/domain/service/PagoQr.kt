package cu.shalev.domain.service

import cu.shalev.core.validation.Phone
import cu.shalev.domain.model.Perfil

/**
 * Contenido del QR de cobro por transferencia (C3, cerrado en el Prompt 13).
 *
 * Formato VERIFICADO decodificando el QR que genera Transfermóvil (Mis Cuentas → tarjeta → QR, 30/9/2026):
 *
 *     TRANSFERMOVIL_ETECSA,TRANSFERENCIA,<tarjeta 16 dígitos>,<móvil 8 dígitos sin +53>,
 *
 * (con la coma final). Al escanearlo, Transfermóvil abre «Transferir Efectivo» con la tarjeta y el «Móvil a confirmar»
 * rellenos y el **monto vacío**: el QR oficial no lleva importe. Shalev NO inventa un campo de importe (podría hacer
 * que Transfermóvil rechace el código); el importe se muestra grande bajo el QR para que el cliente lo teclee.
 */
object PagoQr {
    const val PREFIJO = "TRANSFERMOVIL_ETECSA,TRANSFERENCIA"

    sealed interface Resultado {
        data class Ok(val contenido: String, val tarjeta: String, val telefono: String?) : Resultado
        /** No hay tarjeta/cuenta elegida en Pago electrónico (o no es válida). */
        data object SinTarjeta : Resultado
    }

    fun transfermovil(perfil: Perfil): Resultado {
        val tarjeta = perfil.tarjetaPago?.numero?.filter(Char::isDigit)?.takeIf { it.length in 12..20 }
            ?: return Resultado.SinTarjeta
        // Móvil cubano de 8 dígitos sin +53 (números de otro país no caben en el campo: queda vacío).
        val movil = perfil.telefonoPago?.numero?.let(Phone::normalize)?.removePrefix("+53")
            ?.takeIf { it.length == 8 && it.all(Char::isDigit) }
        return Resultado.Ok("$PREFIJO,$tarjeta,${movil.orEmpty()},", tarjeta, movil)
    }
}
