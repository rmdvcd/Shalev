package cu.shalev.domain.model

/**
 * Perfil del usuario. Nombre y Apellidos separados (GL los valida por separado, D2.4).
 * [pagoTarjetaId]/[pagoTelefonoId] = selección de "Pago electrónico" (cobros por transferencia y QR).
 */
data class Perfil(
    val nombre: String = "",
    val apellidos: String = "",
    val ci: String = "",
    val tarjetas: List<TarjetaBancaria> = emptyList(),
    val telefonos: List<Telefono> = emptyList(),
    val pagoTarjetaId: Long? = null,
    val pagoTelefonoId: Long? = null,
) {
    val vacio: Boolean get() = nombre.isBlank() && apellidos.isBlank() && ci.isBlank()
    val tarjetaPago: TarjetaBancaria? get() = tarjetas.firstOrNull { it.id == pagoTarjetaId }
    val telefonoPago: Telefono? get() = telefonos.firstOrNull { it.id == pagoTelefonoId }

    /**
     * 0.20.0 (H4): lo que recibe la app de un empleado: SOLO la tarjeta y el teléfono con los que cobra (los suyos o,
     * si no tiene o ya no existen, los predeterminados), ya marcados como los de cobro. El resto no sale del teléfono.
     */
    fun paraEmpleado(tarjetaId: Long?, telefonoId: Long?, telefonoEmpleado: String? = null): Perfil {
        val t = tarjetas.firstOrNull { it.id == tarjetaId } ?: tarjetaPago
        // 0.21.0 (C2, P46): el QR de cobro lleva el teléfono que escribió el EMPLEADO al vincularse (así el SMS de
        // Transfermóvil le llega a él) con la tarjeta del dueño. Sin teléfono del empleado, lo de antes.
        val propio = telefonoEmpleado?.let { cu.shalev.core.validation.Phone.normalize(it) }?.let { Telefono(id = TELEFONO_EMPLEADO_ID, numero = it) }
        val f = propio ?: telefonos.firstOrNull { it.id == telefonoId } ?: telefonoPago
        return copy(tarjetas = listOfNotNull(t), telefonos = listOfNotNull(f), pagoTarjetaId = t?.id, pagoTelefonoId = f?.id)
    }

    companion object {
        /** Id sintético del teléfono del empleado en el Perfil que recibe su app (no existe en la principal). */
        const val TELEFONO_EMPLEADO_ID = -1L
    }
}

/** Tarjeta o cuenta bancaria para recibir transferencias. [numero] solo dígitos. */
data class TarjetaBancaria(val id: Long = 0, val numero: String, val alias: String? = null) {
    /** "•••• 1234" para listas; el número completo solo en el QR y la exportación del Perfil. */
    val enmascarado: String get() = "•••• " + numero.takeLast(4)
}

/** Teléfono en E.164 (+53XXXXXXXX). */
data class Telefono(val id: Long = 0, val numero: String, val alias: String? = null)
