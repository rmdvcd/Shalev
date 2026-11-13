package cu.shalev.data.sync

import cu.shalev.core.result.AppResult
import cu.shalev.core.result.runCatchingCancelable
import cu.shalev.core.time.Clock
import cu.shalev.domain.di.IoDispatcher
import cu.shalev.domain.repository.TurnoRepository
import cu.shalev.domain.service.SesionVenta
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 0.20.0 (H5): en una app secundaria, cierra el turno que el dueño pidió cerrar desde la principal.
 *
 * - La petición llega en la respuesta de una sincronización (`SincronizarOk.cerrarTurno`) y se guarda en
 *   [DatosSecundaria.cierrePendiente]: sobrevive a que la app se cierre.
 * - P43 «no se puede forzar el cierre durante una venta»: espera a que [SesionVenta] no tenga ninguna venta a medias
 *   (confirmada, cancelada o abandonada). Mientras tanto Inicio no deja empezar otra.
 * - Cierra con el mismo TurnoRepository que el botón (resumen congelado y aviso al cliente de sincronización) y
 *   sincroniza enseguida para que la principal reciba el turno cerrado y borre la petición.
 * - 0.21.6 (P-b): si el cierre falla (error de la base de datos o excepción) se reintenta con espera creciente
 *   (5 s, 10 s, 20 s… hasta 5 min) mientras siga pedido y no haya venta en curso. Antes no se reintentaba: el flujo
 *   con `distinctUntilChanged` no volvía a emitir y una nueva petición de la principal tampoco (ya estaba pendiente),
 *   así que el turno quedaba abierto hasta empezar otra venta o reabrir la app.
 */
@Singleton
class CierreRemoto @Inject constructor(
    private val config: ConfigSync,
    private val turnos: TurnoRepository,
    private val sesion: SesionVenta,
    private val cliente: ClienteSync,
    private val clock: Clock,
    @IoDispatcher io: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + io)
    private var trabajo: Job? = null

    @Synchronized
    fun iniciar() {
        if (trabajo?.isActive == true) return
        trabajo = scope.launch {
            // 0.25.0: también espera al conteo de caja (el diálogo de Inicio lo pide); al declararlo, vuelve a emitir.
            combine(
                config.secundaria.map { it?.cierrePendiente == true }, sesion.enCurso,
                turnos.observarActivo().map { conteoListo(it) },
            ) { pedido, vendiendo, conteo -> pedido && !vendiendo && conteo }
                .distinctUntilChanged()
                // collectLatest: si empieza una venta, la espera entre reintentos se cancela (P43).
                .collectLatest { toca -> if (toca) reintentar({ aplicarSeguro() != Resultado.FALLO }) { delay(it) } }
        }
    }

    /** Una excepción inesperada cuenta como fallo (se reintenta) en vez de terminar la vigilancia. */
    private suspend fun aplicarSeguro(): Resultado = runCatchingCancelable { aplicar() }.getOrDefault(Resultado.FALLO)

    /** Sin turno abierto, la petición ya está cumplida y solo se olvida. */
    internal suspend fun aplicar(): Resultado {
        // Se vuelve a mirar todo justo antes de cerrar: pudo empezar una venta en este instante.
        val pedido = config.secundariaActual()?.cierrePendiente == true
        val activo = turnos.activo()
        return when (decidir(pedido, sesion.enCurso.value, activo != null, conteoListo(activo))) {
            Accion.ESPERAR -> Resultado.ESPERA
            Accion.OLVIDAR -> { config.editarSecundaria { it.copy(cierrePendiente = false) }; Resultado.OLVIDADA }
            Accion.CERRAR -> cerrar()
        }
    }

    private suspend fun cerrar(): Resultado =
        when (turnos.cerrar(clock.now(), CERRADO_POR, turnos.activo()?.contado)) {
            is AppResult.Ok -> {
                config.editarSecundaria { it.copy(cierrePendiente = false, avisoCierre = true, cierreSolicitado = false) }
                cliente.sincronizar() // sin conexión no pasa nada: el turno cerrado se envía en la próxima
                Resultado.CERRADO
            }
            is AppResult.Err -> Resultado.FALLO // 0.21.6: se reintenta (ver [reintentar])
        }

    enum class Accion { ESPERAR, OLVIDAR, CERRAR }
    enum class Resultado { CERRADO, ESPERA, OLVIDADA, FALLO }

    companion object {
        /**
         * Regla pura (testeada en CierreRemotoTest):
         * - sin petición, o con una venta en curso (P43) → esperar;
         * - con petición y sin turno abierto → ya está cumplida: olvidarla;
         * - con petición, turno abierto y ninguna venta en curso → cerrar.
         */
        fun decidir(pedido: Boolean, ventaEnCurso: Boolean, turnoAbierto: Boolean, conteoListo: Boolean = true): Accion = when {
            !pedido || ventaEnCurso -> Accion.ESPERAR
            turnoAbierto && !conteoListo -> Accion.ESPERAR // 0.25.0: nunca se cierra sin conteo (arqueo vacío)
            !turnoAbierto -> Accion.OLVIDAR
            else -> Accion.CERRAR
        }

        const val REINTENTO_INICIAL_MS = 5_000L
        const val REINTENTO_MAX_MS = 300_000L

        /** 0.21.6: espera antes del reintento nº [intento] (0, 1, 2…): 5 s, 10 s, 20 s… hasta 5 min. */
        fun esperaReintento(intento: Int): Long =
            (REINTENTO_INICIAL_MS shl intento.coerceIn(0, 6)).coerceAtMost(REINTENTO_MAX_MS)

        /** Repite [intentar] (true = terminado) hasta que termine; entre intentos llama a [esperar]. Cancelable. */
        suspend fun reintentar(intentar: suspend () -> Boolean, esperar: suspend (Long) -> Unit) {
            var intento = 0
            while (!intentar()) esperar(esperaReintento(intento++))
        }

        /** El efectivo declarado no basta si hay inventario contado: debe llegar la aprobación de la principal. */
        fun conteoListo(t: cu.shalev.domain.model.Turno?): Boolean = t == null || ((t.fondo == null || t.contado != null) && (t.conteoCierre == null || t.conteoCierre?.aprobadoEn != null))

        /** Lo que sale en «Cierre» del detalle del turno (en lugar del nombre de quien lo cerró). */
        const val CERRADO_POR = "Pedido desde la app principal"
    }
}
