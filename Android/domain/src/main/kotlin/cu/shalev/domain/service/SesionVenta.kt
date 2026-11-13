package cu.shalev.domain.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 0.20.0 (H5, P43): ¿hay una venta a medias en esta app? La pantalla de venta la abre al empezar y la cierra al
 * confirmar, cancelar o salir. El cierre de turno pedido por el dueño espera a que no quede ninguna
 * («no se puede forzar el cierre durante una venta»).
 *
 * Solo vive en memoria: si el sistema mata la app, no hay venta en curso que proteger (el carrito guardado se
 * recupera al volver y la venta sigue bloqueada si el turno ya se cerró, como cualquier venta sin turno).
 */
@Singleton
class SesionVenta @Inject constructor() {
    private val abiertas = mutableSetOf<Any>()
    private val _enCurso = MutableStateFlow(false)
    val enCurso: StateFlow<Boolean> = _enCurso.asStateFlow()

    /** Marca el inicio de una venta; devuelve la ficha para [terminar]. */
    fun iniciar(): Any {
        val ficha = Any()
        synchronized(abiertas) { abiertas += ficha; _enCurso.value = true }
        return ficha
    }

    /** Idempotente: terminar dos veces la misma ficha no tiene efecto. */
    fun terminar(ficha: Any) {
        synchronized(abiertas) { abiertas -= ficha; _enCurso.value = abiertas.isNotEmpty() }
    }
}
