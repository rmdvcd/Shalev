package cu.shalev.designsystem.component

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import java.text.Normalizer
import kotlinx.coroutines.CompletableDeferred

/**
 * Avisos de la app (toasts/snackbars) con forma de «isla»: una sola píldora que se apila con contador y se abre en una
 * tarjeta. Sigue Notificacion.md adaptado a Android (§5): no usa el Snackbar de Material porque solo muestra uno a la
 * vez y no permite pila, contador, paginador ni «Limpiar» en dos pasos; aquí la cola es propia.
 *
 * Reglas (Notificacion.md §2–§4):
 * - **Urgencia = permanencia:** éxito e información se van solos; advertencia, error y carga se quedan hasta que la
 *   persona los cierra (o, la carga, hasta que termina la operación).
 * - **Una sola píldora:** lo más nuevo arriba; la insignia cuenta los acumulados y la tarjeta muestra «1 de N».
 * - **Ráfagas agrupadas:** el mismo aviso repetido seguido no apila otro; suma «×N» y reinicia su tiempo.
 * - **Tope de [MAXIMO]:** los más antiguos se descartan y se cuentan en [perdidos].
 * - **Promesas:** [seguir] crea un único aviso que pasa de «cargando» a éxito o error (con «Reintentar»).
 *   Los mensajes sueltos de «operación en marcha» («Guardando…») también mutan: el siguiente los sustituye.
 * - **Una acción como mucho**, con verbo («Ver», «Deshacer», «Reintentar»).
 */
enum class TipoAviso {
    EXITO, INFO, ADVERTENCIA, ERROR, CARGA;

    /** Urgencia = permanencia: estos no se van solos. */
    val persistente: Boolean get() = this == ADVERTENCIA || this == ERROR || this == CARGA

    /** Se anuncian con prioridad (región viva «assertive»): interrumpen a TalkBack. */
    val urgente: Boolean get() = this == ADVERTENCIA || this == ERROR

    /** Nombre que TalkBack lee antes del mensaje (el tipo no se comunica solo con el color). */
    val nombre: String
        get() = when (this) {
            EXITO -> "Hecho"
            INFO -> "Información"
            ADVERTENCIA -> "Atención"
            ERROR -> "Error"
            CARGA -> "En curso"
        }

    companion object {
        /** Tipo deducido del texto, para los mensajes que la app emite como texto plano (ver [ClasificadorAviso]). */
        fun de(texto: String): TipoAviso = ClasificadorAviso.tipo(texto)
    }
}

/**
 * Si el aviso abre la isla solo (Notificacion.md §1, «Attention»):
 * - [AUTO]: se abre si trae algo que hacer (acción) o leer (descripción); si no, se queda en píldora.
 * - [ABRIR]: se abre siempre. - [APILAR]: se acumula en silencio en la píldora.
 */
enum class AtencionAviso { AUTO, ABRIR, APILAR }

/** Un aviso de la pila. Sus campos cambian en sitio (promesas, ráfagas): la isla se redibuja sin crear otro. */
@Stable
class Aviso internal constructor(
    val id: Long,
    tipo: TipoAviso,
    titulo: String,
    descripcion: String?,
    accion: String?,
    internal var alAccion: (() -> Unit)?,
    atencion: AtencionAviso,
    duracion: SnackbarDuration,
) {
    var atencion: AtencionAviso = atencion; internal set
    var duracion by mutableStateOf(duracion); internal set
    var tipo by mutableStateOf(tipo); internal set
    var titulo by mutableStateOf(titulo); internal set
    var descripcion by mutableStateOf(descripcion); internal set
    /** Texto del único botón de acción (verbo), o null. */
    var accion by mutableStateOf(accion); internal set
    /** Cuántas veces llegó seguido el mismo aviso (ráfaga: «×N»). */
    var repeticiones by mutableIntStateOf(1); internal set
    /** Cambia con cada repetición o cambio de estado: reinicia el tiempo que le queda. */
    var version by mutableIntStateOf(0); internal set
    /** «Reintentar» no cierra el aviso: lo devuelve a «cargando». */
    internal var cerrarAlAccionar: Boolean = true

    /**
     * Texto de una operación en marcha que llegó como mensaje suelto («Guardando un respaldo…»): el siguiente mensaje
     * lo sustituye en el mismo aviso en vez de apilar otro (un toast que muta, no dos).
     */
    var provisional: Boolean = false; internal set

    internal val resultado = CompletableDeferred<SnackbarResult>()

    /** No se va solo: por su tipo o porque se pidió duración indefinida. */
    val persistente: Boolean get() = tipo.persistente || duracion == SnackbarDuration.Indefinite

    /** Tiempo base en pantalla antes de ajustarlo con la accesibilidad del sistema (null = persistente). */
    val tiempoBaseMs: Long?
        get() = when {
            persistente -> null
            accion != null || duracion == SnackbarDuration.Long -> ShalevAvisosState.TIEMPO_CON_ACCION_MS
            else -> ShalevAvisosState.TIEMPO_MS
        }

    /** Lo que se lee de corrido: «Error: No se pudo guardar (×3)». */
    val descripcionAccesible: String
        get() = buildString {
            append(tipo.nombre).append(": ").append(titulo)
            if (repeticiones > 1) append(" (").append(repeticiones).append(" veces)")
            descripcion?.let {
                if (!titulo.trimEnd().endsWith('.')) append('.')
                append(' ').append(it)
            }
        }
}

/** Control de una operación en curso ([ShalevAvisosState.seguir]): el mismo aviso cambia de estado. */
class SeguimientoAviso internal constructor(private val estado: ShalevAvisosState, val aviso: Aviso) {
    /** Vuelve (o sigue) en «cargando», p. ej. al reintentar o para mostrar el avance en el texto. */
    fun cargando(titulo: String = aviso.titulo, descripcion: String? = aviso.descripcion) =
        estado.cambiar(aviso, TipoAviso.CARGA, titulo, descripcion, accion = null, alAccion = null)

    fun exito(titulo: String, descripcion: String? = null, accion: String? = null, alAccion: (() -> Unit)? = null) =
        estado.cambiar(aviso, TipoAviso.EXITO, titulo, descripcion, accion, alAccion)

    /** Error recuperable: con [reintentar] la tarjeta ofrece «Reintentar» y el aviso vuelve a «cargando». */
    fun error(titulo: String, descripcion: String? = null, reintentar: (() -> Unit)? = null) {
        estado.cambiar(
            aviso, TipoAviso.ERROR, titulo, descripcion,
            accion = reintentar?.let { ShalevAvisosState.REINTENTAR },
            alAccion = reintentar?.let { r -> { cargando(); r() } },
            cerrarAlAccionar = false,
        )
    }

    /** La operación se canceló: el aviso desaparece. */
    fun cancelar() = estado.cerrar(aviso)
}

/**
 * Estado de la isla de avisos. Una sola instancia para toda la app ([LocalShalevAvisos], la pone MainScaffold):
 * así los avisos sobreviven al cambio de pantalla y siempre salen en el mismo sitio. Las pantallas sueltas (tests,
 * asistente inicial) crean la suya con [rememberAvisos].
 *
 * [showSnackbar] mantiene la firma de `SnackbarHostState` para que las pantallas no cambien su forma de avisar. A
 * diferencia de aquel, **no espera** a que el aviso se cierre salvo que lleve acción: un error que se queda no
 * bloquea los avisos siguientes.
 */
@Stable
class ShalevAvisosState {
    private val _avisos = mutableStateListOf<Aviso>()

    /** Lo más nuevo primero. */
    val avisos: List<Aviso> get() = _avisos

    /** La isla está abierta en tarjeta (si no, es una píldora). Mientras está abierta, nada se va solo. */
    var abierta by mutableStateOf(false); private set

    /** Página que enseña la tarjeta (0 = el más nuevo). */
    var indice by mutableIntStateOf(0); private set

    /** Avisos descartados por superar [MAXIMO]. */
    var perdidos by mutableIntStateOf(0); private set

    /** «Limpiar» pulsado una vez: la siguiente pulsación borra todos. */
    var limpiarArmado by mutableStateOf(false); private set

    private var siguienteId = 1L

    /** Anfitriones que pueden pintar la isla; solo pinta uno (ver [ShalevSnackbarHost]). */
    private val anfitriones = mutableStateListOf<Pair<Any, Int>>()

    val total: Int get() = _avisos.size

    /** El aviso que se ve: el más nuevo en la píldora o la página elegida en la tarjeta. */
    val actual: Aviso? get() = _avisos.getOrNull(indice.coerceIn(0, (_avisos.size - 1).coerceAtLeast(0)))

    /** Número de la insignia: acumulados + los que se perdieron por el tope. */
    val contador: Int get() = _avisos.size + perdidos

    fun mostrar(
        titulo: String,
        tipo: TipoAviso = TipoAviso.de(titulo),
        descripcion: String? = null,
        accion: String? = null,
        alAccion: (() -> Unit)? = null,
        atencion: AtencionAviso = AtencionAviso.AUTO,
        duracion: SnackbarDuration = SnackbarDuration.Short,
    ): Aviso {
        val ultimo = _avisos.firstOrNull()
        if (ultimo != null && ultimo.provisional && ultimo.tipo != TipoAviso.CARGA) {
            // La operación en marcha terminó (o avanzó): el mismo aviso cambia de texto y de tipo.
            ultimo.provisional = esProvisional(titulo, tipo)
            ultimo.atencion = atencion
            ultimo.duracion = duracion
            cambiar(ultimo, tipo, titulo, descripcion, accion, alAccion)
            indice = 0
            limpiarArmado = false
            if (debeAbrirse(ultimo)) abierta = true
            return ultimo
        }
        if (ultimo != null && accion == null && ultimo.accion == null && tipo != TipoAviso.CARGA &&
            ultimo.tipo == tipo && ultimo.titulo == titulo && ultimo.descripcion == descripcion
        ) {
            // Ráfaga: el mismo aviso otra vez no apila otro; cuenta «×N» y vuelve a empezar su tiempo.
            ultimo.repeticiones += 1
            ultimo.version += 1
            indice = 0
            limpiarArmado = false
            return ultimo
        }
        val aviso = Aviso(siguienteId++, tipo, titulo, descripcion, accion, alAccion, atencion, duracion)
        aviso.provisional = esProvisional(titulo, tipo)
        _avisos.add(0, aviso)
        while (_avisos.size > MAXIMO) {
            _avisos.removeAt(_avisos.lastIndex).resultado.complete(SnackbarResult.Dismissed)
            perdidos += 1
        }
        indice = 0
        limpiarArmado = false
        if (debeAbrirse(aviso)) abierta = true
        return aviso
    }

    /** Compatible con `SnackbarHostState.showSnackbar`. Solo espera respuesta si hay acción. */
    suspend fun showSnackbar(
        message: String,
        actionLabel: String? = null,
        withDismissAction: Boolean = false,
        duration: SnackbarDuration = if (actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Long,
        tipo: TipoAviso = TipoAviso.de(message),
    ): SnackbarResult {
        val aviso = mostrar(message, tipo = tipo, accion = actionLabel, duracion = duration)
        return if (actionLabel == null) SnackbarResult.Dismissed else aviso.resultado.await()
    }

    /** Operación con principio y fin: un único aviso que empieza en «cargando» (Notificacion.md §2.8). */
    fun seguir(cargando: String, descripcion: String? = null): SeguimientoAviso =
        SeguimientoAviso(this, mostrar(cargando, TipoAviso.CARGA, descripcion, atencion = AtencionAviso.APILAR))

    /** Cierra un aviso (×, deslizar o se acabó su tiempo). */
    fun cerrar(aviso: Aviso) = quitar(aviso, SnackbarResult.Dismissed)

    /** Pulsó la acción del aviso. «Reintentar» lo deja en pantalla; el resto lo cierra. */
    fun accionar(aviso: Aviso) {
        val accion = aviso.alAccion
        if (aviso.cerrarAlAccionar) quitar(aviso, SnackbarResult.ActionPerformed)
        accion?.invoke()
    }

    fun abrir() { if (_avisos.isNotEmpty()) abierta = true }

    fun contraer() { abierta = false; limpiarArmado = false; indice = 0 }

    fun anterior() { if (indice > 0) indice -= 1; limpiarArmado = false }

    fun siguiente() { if (indice < _avisos.lastIndex) indice += 1; limpiarArmado = false }

    /** «Limpiar» en dos pasos: la primera pulsación arma (true = ya borró todo). */
    fun pulsarLimpiar(): Boolean {
        if (!limpiarArmado) { limpiarArmado = true; return false }
        limpiarTodo()
        return true
    }

    fun limpiarTodo() {
        val todos = _avisos.toList()
        _avisos.clear()
        todos.forEach { it.resultado.complete(SnackbarResult.Dismissed) }
        reiniciar()
    }

    internal fun cambiar(
        aviso: Aviso,
        tipo: TipoAviso,
        titulo: String,
        descripcion: String?,
        accion: String?,
        alAccion: (() -> Unit)?,
        cerrarAlAccionar: Boolean = true,
    ) {
        if (aviso !in _avisos) return
        aviso.tipo = tipo
        aviso.titulo = titulo
        aviso.descripcion = descripcion
        aviso.accion = accion
        aviso.alAccion = alAccion
        aviso.cerrarAlAccionar = cerrarAlAccionar
        aviso.version += 1
        // Un error con algo que hacer abre la isla, igual que un aviso nuevo con acción.
        if (accion != null && tipo.urgente) { indice = _avisos.indexOf(aviso); abierta = true }
    }

    private fun quitar(aviso: Aviso, resultado: SnackbarResult) {
        val i = _avisos.indexOf(aviso)
        if (i < 0) return
        _avisos.removeAt(i)
        aviso.resultado.complete(resultado)
        if (i < indice) indice -= 1
        indice = indice.coerceIn(0, (_avisos.size - 1).coerceAtLeast(0))
        limpiarArmado = false
        if (_avisos.isEmpty()) reiniciar()
    }

    private fun reiniciar() { abierta = false; limpiarArmado = false; perdidos = 0; indice = 0 }

    private fun esProvisional(titulo: String, tipo: TipoAviso): Boolean =
        tipo == TipoAviso.INFO && titulo.trimEnd().let { it.endsWith("…") || it.endsWith("...") }

    private fun debeAbrirse(a: Aviso): Boolean = when (a.atencion) {
        AtencionAviso.ABRIR -> true
        AtencionAviso.APILAR -> false
        AtencionAviso.AUTO -> a.accion != null || a.descripcion != null
    }

    private var avisado: Pair<Long, Int>? = null

    /** True la primera vez que se pinta este aviso en este estado (vibra una vez aunque cambie de pantalla). */
    internal fun marcarAvisado(a: Aviso): Boolean {
        val clave = a.id to a.version
        if (avisado == clave) return false
        avisado = clave
        return true
    }

    // ------------------------------------------------------------------ anfitriones

    internal fun registrar(token: Any, prioridad: Int) { anfitriones.add(token to prioridad) }

    internal fun retirar(token: Any) { anfitriones.removeAll { it.first === token } }

    /** Pinta el de mayor prioridad y, a igualdad, el último en llegar (la pantalla que acaba de entrar). */
    internal val anfitrionActivo: Any?
        get() {
            var mejor: Pair<Any, Int>? = null
            for (a in anfitriones) if (mejor == null || a.second >= mejor.second) mejor = a
            return mejor?.first
        }

    companion object {
        /** Éxito/información sin acción: ~5 s (Notificacion.md §3: 3–7 s, por defecto 5). */
        const val TIEMPO_MS = 5_000L
        /** Con acción hay que leer y decidir: el doble. */
        const val TIEMPO_CON_ACCION_MS = 10_000L
        /** Tope de la pila; al pasarlo, la insignia sigue contando los descartados. */
        const val MAXIMO = 100
        const val REINTENTAR = "Reintentar"
    }
}

/** La isla de toda la app. Null fuera de MainScaffold: cada pantalla usa entonces la suya. */
val LocalShalevAvisos = staticCompositionLocalOf<ShalevAvisosState?> { null }

/** La isla compartida si la hay; si no, una propia de esta pantalla. */
@Composable
fun rememberAvisos(): ShalevAvisosState {
    val compartida = LocalShalevAvisos.current
    return compartida ?: remember { ShalevAvisosState() }
}

/**
 * Tipo de un aviso que llega como texto plano. Orden: error → advertencia → éxito → información. Los textos de la app
 * siguen pautas fijas («No se pudo…», «Revisa…», «… guardado.»), así que bastan unas raíces; sin acentos ni
 * mayúsculas. Lo que termina en «…» (operación en marcha) es información: un aviso de carga sin seguimiento se
 * quedaría para siempre, por eso «cargando» solo sale de [ShalevAvisosState.seguir].
 */
object ClasificadorAviso {
    private val ERROR = listOf(
        "no se pudo", "no se pudieron", "fallo", "error", "no se instalo", "no se actualizo", "no se restauro",
        "no se registro", "no coincide", "no se encontro", "no se encuentra", "no permite",
    )
    private val ADVERTENCIA = listOf(
        "revisa", "no hay", "no es ", "no valid", "invalid", "no puede", "no se puede", "no alcanza", "no existe",
        "supera", "insuficiente", "sin insumos", "no se ve", "conectate", "activa ", "abrelo", "ya hay", "ya estaba",
        "ya se uso", "ya esta en", "solo se ", "deja al menos", "escribe", "no aprobo", "ajusta", "cambio la ", "cambio desde",
        "con cuidado", "vuelve a elegir", "sin conexion", "sin permiso",
    )
    private val EXITO = listOf(
        "guardad", "eliminad", "agregad", "registrad", "anulad", "modificad", "copiad", "abierto", "actualizad",
        "se actualizo", "restaurad", "enviad", "aplicad", "listo", "terminad", "creado", "importad", "exportad",
        "vinculad", "activad",
    )

    fun tipo(texto: String): TipoAviso {
        val t = normalizar(texto)
        return when {
            ERROR.any { it in t } -> TipoAviso.ERROR
            ADVERTENCIA.any { it in t } -> TipoAviso.ADVERTENCIA
            EXITO.any { it in t } -> TipoAviso.EXITO
            else -> TipoAviso.INFO
        }
    }

    internal fun normalizar(texto: String): String =
        Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}
