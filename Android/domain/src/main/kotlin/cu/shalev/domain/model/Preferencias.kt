package cu.shalev.domain.model

import cu.shalev.core.quantity.Cantidad

/** Niveles por defecto de Shalev.txt: bajo 5, crítico 1 (productos e insumos). Se usan si el artículo no define los suyos. */
data class NivelesMinimos(
    val productoBajo: Long = 5,
    val productoCritico: Long = 1,
    val insumoBajo: Cantidad = Cantidad.enteras(5),
    val insumoCritico: Cantidad = Cantidad.enteras(1),
)

/**
 * Preferencias (DataStore cifrado). El turno activo NO vive aquí: su fuente de verdad es Room, en la misma
 * transacción que las ventas. El tema sigue SIEMPRE al sistema (Shalev.txt); el aviso de caducidad y el tiempo
 * de espera de red son constantes (Prompt 17: no tenían pantalla para cambiarlos).
 */
data class Preferencias(
    val niveles: NivelesMinimos = NivelesMinimos(),
    val onboardingCompletado: Boolean = false,
    /** 0.21.0 (C12): secciones activas del negocio (recorrido de la primera ejecución / Ajustes → Perfil). */
    val modulos: Set<Modulo> = Modulo.TODOS,
    /** 0.21.0 (C12): empleados que el dueño dijo tener en el recorrido (orienta la licencia; 0 = solo el dueño). */
    val empleadosPrevistos: Int = 0,
) {
    companion object {
        /** «Próximo a caducar» = vence en ≤ N días (o ya vencido). */
        const val DIAS_AVISO_CADUCIDAD = 7
        /** Tiempo máximo por base en las consultas de red (conectividad cara/inestable). */
        const val TIMEOUT_RED_SEGUNDOS = 5
    }
}
