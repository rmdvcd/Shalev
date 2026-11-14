package cu.shalev.app.onboarding

import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppError
import cu.shalev.domain.model.DatosIniciales
import cu.shalev.domain.model.NivelesMinimos
import cu.shalev.domain.model.PasoConfiguracion
import cu.shalev.domain.model.ResumenConfiguracion
import cu.shalev.domain.usecase.GuardarDatosIniciales

/*
 * Lógica PURA del asistente (sin Android ni Compose): se prueba en JVM (OnboardingLogicTest).
 * Textos pensados para alguien sin experiencia: frases cortas, sin tecnicismos.
 */

enum class PasoWizard(val titulo: String) {
    BIENVENIDA("Bienvenido"),
    /** 0.21.0 (C12): ¿este teléfono es el del dueño (principal) o el de un empleado (secundaria)? */
    TIPO_APP("Tipo de app"),
    /** 0.27.0 (T11): acceso con huella, cara o PIN del teléfono (opcional; se puede saltar). */
    ACCESO_CLAVE("Acceso con clave"),
    /** 0.21.0 (C12): para qué se usará Shalev (Ventas / Inventario / Servicios). */
    OBJETIVO("Tu negocio"),
    /** 0.21.0 (C12): cuántos empleados (apps secundarias) habrá. */
    EMPLEADOS("Empleados"),
    DATOS("Tus datos"),
    ALERTAS("Avisos de inventario"),
    PRUEBA("Periodo de prueba"),
}

fun PasoConfiguracion.wizard(): PasoWizard = when (this) {
    PasoConfiguracion.DATOS -> PasoWizard.DATOS
    PasoConfiguracion.ALERTAS -> PasoWizard.ALERTAS
    PasoConfiguracion.PRUEBA -> PasoWizard.PRUEBA
}

/** PRIMERA_VEZ = arranque inicial (puerta de ShalevRoot). RETOMAR = desde Ajustes. */
enum class ModoWizard { PRIMERA_VEZ, RETOMAR }

/**
 * Pasos a mostrar. Se calcula UNA vez al abrir (si se recalculara, la lista encogería al completar pasos).
 * - Primera vez: bienvenida + todos los pasos aplicables.
 * - Retomar: solo los pendientes (o todos, si ya está completa, para poder revisarlos).
 * - [soloPaso]: acceso directo desde Ajustes a un paso concreto (p. ej. "Avisos de inventario").
 */
fun planWizard(modo: ModoWizard, resumen: ResumenConfiguracion, soloPaso: PasoConfiguracion? = null): List<PasoWizard> = when {
    soloPaso != null -> listOf(soloPaso.wizard())
    // 0.21.0 (C12): el recorrido de la primera vez empieza por el tipo de app, el objetivo y los empleados.
    // Solo los pasos PENDIENTES: quien activó su licencia antes de este asistente (la app arranca bloqueada y la
    // pantalla de Licencia ya le pidió sus datos, guardados en el Perfil al solicitar) no vuelve a darlos aquí.
    // En una instalación limpia no hay nada hecho y pendientes = todos: el recorrido es el mismo de siempre.
    modo == ModoWizard.PRIMERA_VEZ ->
        listOf(PasoWizard.BIENVENIDA) + PASOS_TOUR + resumen.pendientes.map { it.wizard() }
    else -> resumen.pendientes.ifEmpty { resumen.pasos }.map { it.wizard() }
}

/** 0.21.0 (C12): pasos del recorrido inicial que no dependen de la configuración guardada. */
val PASOS_TOUR = listOf(PasoWizard.TIPO_APP, PasoWizard.ACCESO_CLAVE, PasoWizard.OBJETIVO, PasoWizard.EMPLEADOS)

/** 0.21.0 (C12): textos del recorrido inicial (puros, probados en JVM). */
object TextosTour {
    const val PRINCIPAL = "Principal (dueño)"
    const val PRINCIPAL_DETALLE = "Este teléfono controla el negocio: inventario, precios, empleados y licencia."
    const val SECUNDARIA = "Secundaria (empleado)"
    const val SECUNDARIA_DETALLE = "Este teléfono es de un empleado: vende con el inventario y los permisos que le dé el dueño."
    const val TIPO_EXPLICACION = "Solo hay un teléfono principal por negocio. Cada empleado usa una app secundaria vinculada a él."
    const val SECUNDARIA_SIGUIENTE = "Al continuar escribirás tu número de teléfono y escanearás el código que te muestre el dueño."
    const val OBJETIVO_EXPLICACION = "Marca lo que harás con Shalev. Lo que no marques no aparecerá."
    const val VENTAS_ACTIVA_INVENTARIO = "Vender productos necesita el inventario: se activa también."
    const val EMPLEADOS_EXPLICACION = "Cada empleado usa su propia app secundaria."
    const val EMPLEADOS_AYUDA = "Puedes elegir hasta el límite de tu prueba o licencia. Para más apps, solicita una ampliación de licencia."
    /** 0.27.0 (T11/T9). */
    const val ACCESO_EXPLICACION = "Si quieres, Shalev pedirá tu huella, tu cara o el PIN del teléfono al abrirla."
    const val TIPO_DEFINITIVO = "El tipo de app no se puede cambiar después. Para cambiarlo habría que borrar los datos de la app."
    fun empleados(n: Int): String = when (n) { 0 -> "Sin empleados"; 1 -> "1 empleado"; else -> "$n empleados" }
}

/**
 * 0.21.0 (C12): señal de proceso «al terminar el recorrido, abrir la vinculación como secundaria». Se consume una vez.
 * Suposición: si el proceso muere entre medias, el empleado entra a Inicio y puede vincularse desde Ajustes → Empleados.
 */
object TourPendiente {
    private val vincular = java.util.concurrent.atomic.AtomicBoolean(false)
    fun marcarVincularSecundaria() = vincular.set(true)
    fun consumirVincularSecundaria(): Boolean = vincular.getAndSet(false)
}

// ---------------- Datos ----------------

fun mensajeDato(campo: String): String = when (campo) {
    "nombre" -> "Escribe solo letras (2 a 80)"
    "apellidos" -> "Escribe solo letras (2 a 80)"
    "ci" -> "Revisa el carné: 5 a 20 letras o números"
    "telefono" -> "Número no válido. Ejemplo: 52345678"
    else -> "Revisa este dato"
}

/** Errores por campo (vacío = válido: todos los datos son opcionales). */
fun erroresDatos(d: DatosIniciales): Map<String, String> = buildMap {
    // Se valida cada campo por separado para marcar todos a la vez.
    listOf(
        "nombre" to d.copy(apellidos = "", ci = "", telefono = ""),
        "apellidos" to d.copy(nombre = "", ci = "", telefono = ""),
        "ci" to d.copy(nombre = "", apellidos = "", telefono = ""),
        "telefono" to d.copy(nombre = "", apellidos = "", ci = ""),
    ).forEach { (campo, solo) -> if (GuardarDatosIniciales.validar(solo) != null) put(campo, mensajeDato(campo)) }
}

// ---------------- Niveles de alerta ----------------

enum class CampoNivel { PRODUCTO_BAJO, PRODUCTO_CRITICO, INSUMO_BAJO, INSUMO_CRITICO }

/** Formulario de niveles como texto (lo que el usuario escribe). Productos: enteros. Insumos: hasta 3 decimales. */
data class NivelesForm(
    val productoBajo: String = "5",
    val productoCritico: String = "1",
    val insumoBajo: String = "5",
    val insumoCritico: String = "1",
) {
    fun valor(c: CampoNivel): String = when (c) {
        CampoNivel.PRODUCTO_BAJO -> productoBajo
        CampoNivel.PRODUCTO_CRITICO -> productoCritico
        CampoNivel.INSUMO_BAJO -> insumoBajo
        CampoNivel.INSUMO_CRITICO -> insumoCritico
    }

    fun con(c: CampoNivel, v: String): NivelesForm = when (c) {
        CampoNivel.PRODUCTO_BAJO -> copy(productoBajo = v)
        CampoNivel.PRODUCTO_CRITICO -> copy(productoCritico = v)
        CampoNivel.INSUMO_BAJO -> copy(insumoBajo = v)
        CampoNivel.INSUMO_CRITICO -> copy(insumoCritico = v)
    }

    /** Botones + y − (más fáciles que el teclado): de 1 en 1, nunca por debajo de 0. */
    fun sumar(c: CampoNivel, delta: Int): NivelesForm {
        val esInsumo = c == CampoNivel.INSUMO_BAJO || c == CampoNivel.INSUMO_CRITICO
        val actual = if (esInsumo) Cantidad.parse(valor(c)) ?: Cantidad.ZERO else Cantidad.enteras(valor(c).trim().toLongOrNull() ?: 0)
        val nuevo = Cantidad((actual.milesimas + delta * 1000L).coerceIn(0, MAX * 1000L))
        return con(c, if (esInsumo) Cantidad.editable(nuevo) else (nuevo.milesimas / 1000).toString())
    }

    private fun entero(s: String): Long? = s.trim().takeIf { it.matches(Regex("^[0-9]{1,6}$")) }?.toLong()?.takeIf { it <= MAX }
    private fun cantidad(s: String): Cantidad? = Cantidad.parse(s)?.takeIf { it <= Cantidad.enteras(MAX) }

    fun errores(): Map<CampoNivel, String> = buildMap {
        val pb = entero(productoBajo); val pc = entero(productoCritico)
        val ib = cantidad(insumoBajo); val ic = cantidad(insumoCritico)
        if (pb == null) put(CampoNivel.PRODUCTO_BAJO, "Escribe un número entero (0 o más)")
        if (pc == null) put(CampoNivel.PRODUCTO_CRITICO, "Escribe un número entero (0 o más)")
        if (ib == null) put(CampoNivel.INSUMO_BAJO, "Escribe un número (hasta 3 decimales)")
        if (ic == null) put(CampoNivel.INSUMO_CRITICO, "Escribe un número (hasta 3 decimales)")
        if (pb != null && pc != null && pc > pb) put(CampoNivel.PRODUCTO_CRITICO, CRITICO_MAYOR)
        if (ib != null && ic != null && ic > ib) put(CampoNivel.INSUMO_CRITICO, CRITICO_MAYOR)
    }

    fun aNiveles(): NivelesMinimos? {
        if (errores().isNotEmpty()) return null
        return NivelesMinimos(entero(productoBajo)!!, entero(productoCritico)!!, cantidad(insumoBajo)!!, cantidad(insumoCritico)!!)
    }

    companion object {
        const val MAX = 999_999L
        const val CRITICO_MAYOR = "El nivel crítico no puede ser mayor que el bajo"

        fun desde(n: NivelesMinimos) = NivelesForm(
            n.productoBajo.toString(), n.productoCritico.toString(),
            Cantidad.editable(n.insumoBajo), Cantidad.editable(n.insumoCritico),
        )

        /** Valores recomendados de Shalev.txt: bajo 5, crítico 1. */
        val RECOMENDADOS = desde(NivelesMinimos())
    }
}

fun mensajeErrorGuardado(e: AppError): String = when (e) {
    is AppError.Validacion -> "Revisa los datos marcados"
    else -> "No se pudo guardar. Inténtalo de nuevo."
}

// ---------------- Estado de la pantalla ----------------

data class OnboardingUiState(
    val cargando: Boolean = true,
    val modo: ModoWizard = ModoWizard.PRIMERA_VEZ,
    val pasos: List<PasoWizard> = emptyList(),
    val indice: Int = 0,
    val datos: DatosIniciales = DatosIniciales(),
    val mostrarErroresDatos: Boolean = false,
    val niveles: NivelesForm = NivelesForm(),
    val mostrarErroresNiveles: Boolean = false,
    val licencia: cu.shalev.domain.model.Licencia? = null,
    val ocupado: Boolean = false,
    /** 0.21.0 (C12): respuestas del recorrido inicial. */
    val esSecundaria: Boolean = false,
    val modulos: Set<cu.shalev.domain.model.Modulo> = cu.shalev.domain.model.Modulo.TODOS,
    val empleados: Int = 0,
    /** 0.27.0 (T11): acceso con clave en este teléfono. */
    val accesoClave: Boolean = false,
) {
    val paso: PasoWizard? get() = pasos.getOrNull(indice)
    val esUltimo: Boolean get() = indice == pasos.lastIndex
    val puedeVolver: Boolean get() = indice > 0

    /** "Paso 2 de 4" (la bienvenida no cuenta como paso). */
    val numeroPaso: Int get() = pasos.take(indice + 1).count { it != PasoWizard.BIENVENIDA }
    val totalPasos: Int get() = pasos.count { it != PasoWizard.BIENVENIDA }

    /** La bienvenida y la prueba son informativas: no se "omiten". */
    val pasoOmitible: Boolean get() = paso == PasoWizard.DATOS || paso == PasoWizard.ALERTAS ||
        paso == PasoWizard.OBJETIVO || paso == PasoWizard.EMPLEADOS || paso == PasoWizard.ACCESO_CLAVE

    /** 0.21.0 (C12): en Objetivo hace falta al menos una opción marcada. */
    val puedeSeguir: Boolean get() = paso != PasoWizard.OBJETIVO || modulos.isNotEmpty()

    val textoBotonPrincipal: String get() = when {
        paso == PasoWizard.BIENVENIDA -> "Comenzar"
        paso == PasoWizard.TIPO_APP && esSecundaria -> "Vincular con el dueño"
        esUltimo && modo == ModoWizard.PRIMERA_VEZ -> "Empezar a usar Shalev"
        esUltimo -> "Terminar"
        else -> "Siguiente"
    }
}
