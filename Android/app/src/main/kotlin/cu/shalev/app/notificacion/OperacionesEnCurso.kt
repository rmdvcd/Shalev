package cu.shalev.app.notificacion

import cu.shalev.designsystem.component.TipoAviso
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Operaciones largas que se ven en la barra de notificaciones con su barra de progreso horizontal. */
enum class Operacion(val idNotificacion: Int) {
    /** Descarga de la versión nueva y respaldo previo a la instalación. */
    ACTUALIZACION(40),

    /** Exportar o importar un respaldo (nunca a la vez: la pantalla de Respaldo hace una cosa cada vez). */
    RESPALDO(41),
}

/** Única acción con verbo que puede llevar la notificación (Notificacion.md §2.5: como mucho una, nunca «OK»). */
enum class AccionOperacion(val texto: String) {
    CANCELAR("Cancelar"),
    ABRIR("Abrir"),
}

/**
 * Estado de una operación. Mientras está en curso ([TipoAviso.CARGA]) lleva su avance; al terminar, la misma
 * notificación cambia a éxito o error (una promesa que muta, Notificacion.md §2.8), nunca se apila otra.
 *
 * [fraccion] = 0..1 para la barra; null = barra indeterminada (no se sabe cuánto falta).
 */
data class EstadoOperacion(
    val tipo: TipoAviso,
    val titulo: String,
    val texto: String,
    val fraccion: Float? = null,
    val accion: AccionOperacion? = null,
) {
    val enCurso: Boolean get() = tipo == TipoAviso.CARGA

    /** Porcentaje entero de la barra (null si es indeterminada). */
    val porcentaje: Int? get() = fraccion?.let { (it.coerceIn(0f, 1f) * 100).toInt() }
}

/**
 * Lo que está pasando ahora con la actualización y los respaldos. Lo escriben [cu.shalev.app.actualizacion.GestorActualizacion]
 * y [cu.shalev.app.respaldo.RespaldoViewModel]; lo pinta [NotificadorOperaciones]. Sin Android, para probarlo en la JVM.
 *
 * El avance solo se publica cuando cambia el porcentaje entero (o el texto): Android descarta en silencio las
 * actualizaciones demasiado seguidas de una notificación, y así la barra no se atasca.
 */
@Singleton
class OperacionesEnCurso @Inject constructor() {
    private val _estados = MutableStateFlow<Map<Operacion, EstadoOperacion>>(emptyMap())
    val estados: StateFlow<Map<Operacion, EstadoOperacion>> = _estados.asStateFlow()

    fun estado(op: Operacion): EstadoOperacion? = _estados.value[op]

    /** En curso: [fraccion] 0..1 o null (indeterminada). */
    fun avanzar(op: Operacion, titulo: String, texto: String, fraccion: Float? = null, accion: AccionOperacion? = null) {
        val nuevo = EstadoOperacion(TipoAviso.CARGA, titulo, texto, fraccion?.coerceIn(0f, 1f), accion)
        _estados.update { m ->
            val antes = m[op]
            if (antes != null && antes.enCurso && antes.titulo == titulo && antes.texto == texto &&
                antes.accion == accion && antes.porcentaje == nuevo.porcentaje
            ) m else m + (op to nuevo)
        }
    }

    /** Terminó: la notificación de la operación pasa a [tipo] (éxito, información, advertencia o error). */
    fun terminar(op: Operacion, tipo: TipoAviso, titulo: String, texto: String, accion: AccionOperacion? = AccionOperacion.ABRIR) {
        require(tipo != TipoAviso.CARGA) { "Una operación terminada no puede seguir cargando" }
        _estados.update { it + (op to EstadoOperacion(tipo, titulo, texto, null, accion)) }
    }

    /** Ya no hay nada que enseñar (cancelada, o el resultado ya se vio dentro de la app). */
    fun quitar(op: Operacion) = _estados.update { it - op }
}
