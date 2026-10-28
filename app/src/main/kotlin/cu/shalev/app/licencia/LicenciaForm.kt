package cu.shalev.app.licencia

import cu.shalev.licencia.contract.GlContract
import cu.shalev.core.result.AppError
import cu.shalev.core.time.Dates
import cu.shalev.core.validation.Phone
import cu.shalev.core.validation.Validators
import cu.shalev.core.money.Money
import cu.shalev.domain.model.Licencia
import cu.shalev.domain.model.LicenciaInstalada
import cu.shalev.domain.model.Perfil
import cu.shalev.licencia.ActivationResult
import cu.shalev.licencia.LicenseState
import cu.shalev.licencia.SolicitudInput
import cu.shalev.licencia.bannerText
import cu.shalev.licencia.contract.TipoLicencia
import cu.shalev.licencia.contract.Via
import java.time.Instant
import java.time.ZoneId

/*
 * Lógica PURA del panel de Licencia (sin Android ni Compose): se prueba en JVM (LicenciaFormTest).
 * Los textos hacia el usuario son genéricos a propósito: nunca revelan qué comprobación criptográfica falló.
 */

/** Datos del solicitante. [editando] = formulario abierto; si es false se muestra un resumen con "Editar". */
data class LicenciaForm(
    val nombre: String = "",
    val apellidos: String = "",
    val ci: String = "",
    val telefono: String = "",
    val tipo: TipoLicencia = TipoLicencia.MENSUAL,
    val via: Via = Via.WHATSAPP,
    val mensajeLicencia: String = "",
    val mostrarErrores: Boolean = false,
    val editando: Boolean = true,
    /** 0.21.0 (C4): apps secundarias (empleados) que se pagan con la licencia, 0..[GlContract.SECUNDARIAS_MAX]. */
    val secundarias: Int = 0,
    /**
     * 0.25.0 (§3): ID de la licencia que se RECUPERA (teléfono nuevo o app reinstalada). Vacío = solicitud normal. GL
     * revoca la anterior y emite gratis la misma (vencimiento y secundarias) para este teléfono.
     */
    val recupera: String = "",
    /** Solicitud de ampliación de apps secundarias sobre la licencia activa instalada (conservando vencimiento). */
    val ampliandoSecundarias: Boolean = false,
) {
    /** 0.21.0 (C4): precio total = base del tipo + importe por cada secundaria. */
    val precio: Long get() = tipo.precio(secundarias)

    /** Importe a cobrar cuando se amplían apps secundarias sobre una licencia con [previas] secundarias. */
    fun precioAmpliacion(previas: Int): Long = tipo.precioAmpliacion((secundarias - previas).coerceAtLeast(1))

    val nombreOk: Boolean get() = Validators.nombre(nombre)
    val apellidosOk: Boolean get() = Validators.nombre(apellidos)
    val ciOk: Boolean get() = Validators.ci(ci)
    val telefonoOk: Boolean get() = Phone.normalize(telefono) != null
    val valido: Boolean get() = nombreOk && apellidosOk && ciOk && telefonoOk
    /** ID canónico a recuperar (null = vacío o sin un ID reconocible). */
    val idRecupera: String? get() = recupera.takeIf { it.isNotBlank() }?.let { cu.shalev.licencia.contract.IdLicencia.extraer(it) }
    val recuperaOk: Boolean get() = recupera.isBlank() || idRecupera != null

    fun aInput(lic: Licencia? = null): SolicitudInput {
        val inst = lic?.instalada
        if (lic != null && inst != null && lic.accionSolicitud(ampliandoSecundarias) == AccionSolicitud.AMPLIAR) {
            val total = secundarias.coerceIn(inst.secundarias + 1, GlContract.SECUNDARIAS_MAX)
            return SolicitudInput(
                nombre = nombre.trim(),
                apellidos = apellidos.trim(),
                ci = ci.trim(),
                telefono = telefono.trim(),
                tipo = inst.tipo,
                via = via,
                secundarias = total,
                amplia = inst.id,
                secundariasPrevias = inst.secundarias,
            )
        }
        return SolicitudInput(nombre.trim(), apellidos.trim(), ci.trim(), telefono.trim(), tipo, via, secundarias, recupera = idRecupera)
    }

    /** 0.21.0 (C4): cambia el nº de secundarias dentro de [minimo]..[GlContract.SECUNDARIAS_MAX]. */
    fun conSecundarias(n: Int, minimo: Int = 0): LicenciaForm =
        copy(secundarias = n.coerceIn(minimo.coerceIn(0, GlContract.SECUNDARIAS_MAX), GlContract.SECUNDARIAS_MAX))

    /** Activa o desactiva el modo de ampliación de secundarias ajustando tipo y mínimo de secundarias. */
    fun conModoAmpliacion(activar: Boolean, instalada: LicenciaInstalada?): LicenciaForm {
        if (!activar || instalada == null || instalada.secundarias >= GlContract.SECUNDARIAS_MAX) {
            return copy(ampliandoSecundarias = false)
        }
        val minTotal = (instalada.secundarias + 1).coerceAtMost(GlContract.SECUNDARIAS_MAX)
        return copy(
            tipo = instalada.tipo,
            secundarias = secundarias.coerceIn(minTotal, GlContract.SECUNDARIAS_MAX),
            recupera = "",
            ampliandoSecundarias = true,
        )
    }

    /**
     * Copia los datos del Perfil (sincronía Perfil → Licencia). Perfil vacío o incompleto = formulario
     * editable; completo = resumen. El tipo, la vía y el mensaje pegado del usuario se conservan.
     */
    fun desdePerfil(p: Perfil): LicenciaForm {
        val f = copy(
            nombre = p.nombre,
            apellidos = p.apellidos,
            ci = p.ci,
            telefono = p.telefonos.firstOrNull()?.numero.orEmpty(),
        )
        return f.copy(editando = p.vacio || !f.valido)
    }

    /** Selecciona un teléfono (chip del Perfil o escrito a mano). */
    fun conTelefono(numero: String): LicenciaForm = copy(telefono = numero)

    fun telefonoSeleccionado(numero: String): Boolean =
        Phone.normalize(telefono)?.let { it == Phone.normalize(numero) } ?: false

    /** "Listo" solo cierra el formulario si los datos son válidos; si no, muestra los errores. */
    fun cerrarEdicion(): LicenciaForm = if (valido) copy(editando = false, mostrarErrores = false) else copy(mostrarErrores = true)
}

/** Qué ofrece el panel según lo instalado. */
enum class AccionSolicitud(val titulo: String, val boton: String) {
    SOLICITAR("Solicitar licencia", "Solicitar"),
    ACTUALIZAR("Renovar o cambiar licencia", "Solicitar renovación"),
    AMPLIAR("Ampliar apps secundarias", "Solicitar ampliación"),
    NINGUNA("Licencia perpetua", ""),
}

/** Hay una licencia vigente (activa o perpetua, no maestra) con margen para añadir más apps secundarias (< 10). */
fun Licencia.ampliable(): Boolean =
    (estado is LicenseState.Active || estado is LicenseState.Perpetual) &&
        instalada != null &&
        instalada.id != cu.shalev.licencia.ID_MAESTRA_LICENCIA &&
        instalada.secundarias < GlContract.SECUNDARIAS_MAX

fun Licencia.accionSolicitud(modoAmpliar: Boolean = false): AccionSolicitud = when {
    estado is LicenseState.Perpetual -> if (ampliable()) AccionSolicitud.AMPLIAR else AccionSolicitud.NINGUNA
    ampliable() && modoAmpliar -> AccionSolicitud.AMPLIAR
    instalada != null -> AccionSolicitud.ACTUALIZAR
    else -> AccionSolicitud.SOLICITAR
}

/**
 * 0.21.0 (C4): secundarias sugeridas en la solicitud: las de la licencia instalada (al renovar se mantiene lo
 * contratado); sin licencia, los empleados que se indicaron en el tour inicial (C12). Suposición: 0 si no se indicó
 * (un negocio sin empleados paga solo la base).
 */
fun Licencia.secundariasSugeridas(empleadosPrevistos: Int): Int =
    (instalada?.secundarias ?: empleadosPrevistos).coerceIn(0, GlContract.SECUNDARIAS_MAX)

/** Al renovar se preselecciona el tipo vigente; si no hay licencia, Mensual (la más barata). */
fun Licencia.tipoSugerido(): TipoLicencia = instalada?.tipo ?: TipoLicencia.MENSUAL

/** Avisos de una versión incompleta: solo los resuelve el desarrollador con un build nuevo (claves fijadas). */
enum class AvisoLicencia(val titulo: String, val detalle: String) {
    SIN_SOLICITUD(
        "Esta versión no puede pedir licencias",
        "Contacta al desarrollador para obtener una versión actualizada de Shalev.",
    ),
    SIN_VALIDACION(
        "Esta versión no puede validar licencias",
        "Contacta al desarrollador para obtener una versión actualizada de Shalev.",
    ),
}

fun Licencia.avisos(): List<AvisoLicencia> = buildList {
    if (!validacionDisponible) add(AvisoLicencia.SIN_VALIDACION)
    if (faltaClaveEmisor) add(AvisoLicencia.SIN_SOLICITUD)
}

/**
 * Filas "etiqueta → valor" de la tarjeta de estado, SOLO con lo que el encabezado no dice ya (P28).
 * - Sin «Tipo» ni «Vence»: el encabezado ya dice «Licencia Mensual · Vence el …» (o «Sin vencimiento»).
 * - «Tiempo restante» solo con licencia activa; en la prueba el encabezado ya dice «Quedan N días».
 * - Sin «ID de licencia» ni «ID del dispositivo»: son datos internos. La solicitud cifrada ya los lleva, y el ID
 *   del teléfono que pide Migrar se ve en Ajustes → Migrar.
 */
fun Licencia.detalle(ahora: Instant, zone: ZoneId = ZoneId.systemDefault()): List<Pair<String, String>> = buildList {
    instalada?.let { add("Emitida" to Dates.day(it.emitidaEn, zone)) }
    if (estado is LicenseState.Active) add("Tiempo restante" to tiempoRestante(ahora, zone))
}

fun Licencia.tiempoRestante(ahora: Instant, zone: ZoneId = ZoneId.systemDefault()): String = when (val e = estado) {
    is LicenseState.Trial, is LicenseState.Active -> e.bannerText(ahora, zone)!!.substringAfter(": ")
    is LicenseState.Perpetual -> "Ilimitado"
    is LicenseState.Expired, LicenseState.TrialExpired -> "Vencida"
    LicenseState.Revoked -> "Revocada"
    LicenseState.ClockTampered -> "No disponible"
}

/**
 * 0.25.1 (C): se puede pedir la recuperación de la licencia de otro teléfono si aquí no hay licencia o la instalada
 * está vencida (quien migró y la dejó vencer). Con una licencia vigente no hace falta: se renueva.
 */
fun Licencia.permiteRecuperar(): Boolean = instalada == null || estado is LicenseState.Expired

/** 0.22.0 (L-e): hay una licencia con vencimiento instalada (activa o vencida) que se puede renovar igual. */
fun Licencia.renovable(): Boolean = instalada?.venceEn != null && estado !is LicenseState.Perpetual

/** 0.22.0 (L-e): «Renovar igual»: mismo tipo y secundarias que la licencia instalada (el usuario aún puede cambiarlos). */
fun LicenciaForm.igualQue(instalada: LicenciaInstalada): LicenciaForm =
    copy(tipo = instalada.tipo, ampliandoSecundarias = false).conSecundarias(instalada.secundarias)

/** 0.22.0: textos de la renovación y del QR (puros, probados en JVM). */
object TextosRenovacion {
    const val BOTON = "Renovar igual"
    const val BOTON_AMPLIAR = "Ampliar secundarias"
    const val SIN_PERDER_DIAS = "Si renuevas antes de que venza no pierdes días: la nueva licencia empieza cuando termine la actual."
    const val CONSERVA_VENCIMIENTO = "Conserva el vencimiento actual de tu licencia y cobra solo las secundarias añadidas."
    fun detalle(i: LicenciaInstalada): String =
        "${i.tipo.etiqueta} · ${TextosLicencia.secundarias(i.secundarias)} · ${Money.cup(i.tipo.precio(i.secundarias))}"
}

/** 0.23.1: la licencia llega solo como texto (sin QR): se pega el mensaje completo o solo el código. */
object TextosActivacion {
    const val INSTRUCCION = "Pega el mensaje de activación que recibiste."
}

fun mensajeActivacion(r: ActivationResult): String = when (r) {
    is ActivationResult.Accepted -> "Licencia activada"
    ActivationResult.NotFound -> "No se encontró una licencia en el texto pegado"
    ActivationResult.Rejected -> "La licencia no es válida para este dispositivo"
    ActivationResult.Outdated -> "Ya tienes instalada una licencia más reciente"
}

fun mensajeErrorSolicitud(e: AppError): String = when (e) {
    is AppError.Validacion -> "Revisa los datos del formulario"
    else -> "No se pudo generar la solicitud"
}

/**
 * P18 (A08): pasos del asistente de Licencia. Solo estado de pantalla: solicitar y activar siguen usando los mismos
 * casos de uso. Avanzar desde Datos exige datos válidos; si no lo son, se marcan los errores y no se avanza.
 */
enum class PasoLicencia(val titulo: String) {
    DATOS("Revisa tus datos"),
    SOLICITAR("Pide la licencia"),
    ACTIVAR("Activa la licencia"),
    ;

    val anterior: PasoLicencia? get() = entries.getOrNull(ordinal - 1)
    val siguiente: PasoLicencia? get() = entries.getOrNull(ordinal + 1)
}

/** Si ya hay un mensaje pegado (p. ej. tras girar la pantalla), se vuelve a Activar; si no, se empieza por los datos. */
fun pasoInicialLicencia(form: LicenciaForm): PasoLicencia =
    if (form.mensajeLicencia.isNotBlank()) PasoLicencia.ACTIVAR else PasoLicencia.DATOS

fun puedeAvanzar(paso: PasoLicencia, form: LicenciaForm): Boolean = paso.siguiente != null && (paso != PasoLicencia.DATOS || form.valido)
