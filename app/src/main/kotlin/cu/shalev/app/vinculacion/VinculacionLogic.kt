package cu.shalev.app.vinculacion

import cu.shalev.core.result.AppError
import cu.shalev.core.time.Dates
import cu.shalev.domain.model.Empleado
import cu.shalev.domain.model.EstadoConexion
import cu.shalev.domain.model.TipoApp
import cu.shalev.domain.model.Vinculacion
import java.time.Instant

/** P37: textos y reglas puras de «Apps vinculadas» (testeadas en VinculacionLogicTest). */
object VinculacionLogic {
    const val TITULO = "Apps vinculadas"
    const val PRINCIPAL_DETALLE =
        "Esta es la app principal: guarda todos los datos y la licencia. Tus empleados se vinculan con un código QR."
    const val SIN_SECUNDARIAS = "Aún no hay apps secundarias"
    /** 1.0.1: la vinculación y la conexión también funcionan por datos móviles, no solo por la misma wifi. */
    const val SIN_SECUNDARIAS_DETALLE =
        "Agrega la app de cada empleado. Necesitan estar en la misma red que este teléfono: wifi, zona wifi o datos móviles."
    const val USAR_COMO_SECUNDARIA = "Usar esta app como secundaria"
    /** 0.27.0 (T9). */
    const val PRINCIPAL_NO_SECUNDARIA = "Esta app es la principal del negocio y no puede pasar a secundaria."
    const val ABRIR_ZONA_WIFI = "Abrir ajustes de zona wifi"
    const val ABRIR_WIFI = "Abrir ajustes de wifi"
    const val CONFIRMAR_SECUNDARIA =
        "Escanearás el código que te muestre la app principal. Al vincularse, se borran los datos de este teléfono y se usan los de la principal."
    const val QR_DETALLE = "En el teléfono del empleado, elige «Secundaria» y escanea este código."
    const val DESVINCULAR = "Desvincular"
    const val CONFIRMAR_DESVINCULAR = "Se borran los datos de este teléfono y vuelve a ser una app principal independiente."
    const val SINCRONIZAR = "Sincronizar ahora"
    const val QUITAR = "Quitar esta app"
    const val CONFIRMAR_QUITAR = "Deja de sincronizar y, cuando se conecte, sus datos se borran. Las ventas que ya envió se quedan aquí."
    const val NUEVO_CODIGO = "Nuevo código"
    const val VINCULADA = "Vinculada correctamente"
    const val SINCRONIZADA = "Sincronizado"
    const val GUARDADO = "Cambios guardados"
    /** 0.32.0: la principal avisó de que cambió el catálogo. */
    const val CAMBIOS_PENDIENTES = "El dueño cambió el catálogo. Sincroniza para recibirlo."

    // 0.20.0 (H4): cobro por transferencia de cada empleado.
    const val COBRO = "Cobro por transferencia"
    const val COBRO_TARJETA = "Cobrar en la tarjeta"
    /** 0.21.0 (C2): el teléfono lo escribe el empleado al vincularse; el SMS de Transfermóvil le llega a él. */
    const val COBRO_TELEFONO = "Teléfono del empleado"
    const val COBRO_DETALLE = "Su app solo recibe esta tarjeta para el QR de cobro."
    const val SIN_TELEFONO = "Lo escribe el empleado al vincular su app"
    const val TELEFONO_SECUNDARIA = "Tu número de teléfono"
    const val TELEFONO_SECUNDARIA_AYUDA = "8 dígitos. Va en tu QR de cobro: el SMS de la transferencia te llegará a ti."
    const val ERROR_TELEFONO = "Escribe 8 dígitos que empiecen por 5 o 6."

    // 0.21.0 (C6): solicitud de cierre del empleado.
    const val SOLICITA_CIERRE = "Pide cerrar su turno"
    const val APROBAR_CIERRE = "Aprobar cierre"
    const val RECHAZAR_CIERRE = "Rechazar cierre"
    fun cierreRechazado(nombre: String) = "Cierre rechazado: el turno de $nombre sigue abierto"

    // 0.20.0 (H5): pedir el cierre del turno.
    const val PEDIR_CIERRE = "Pedir cierre del turno"
    const val CONFIRMAR_CIERRE =
        "Su app lo cerrará al conectarse, nunca durante una venta. Las ventas del turno se quedan registradas."
    const val SIN_TURNO = "Ese turno ya está cerrado."

    // 0.32.0: el fondo de cada turno lo escribe quien abre el turno (también en una secundaria).

    /** 0.32.0: avance de la sincronización de una app conectada. */
    fun progreso(p: cu.shalev.domain.model.ProgresoSync): String {
        val fase = when (p.fase) {
            cu.shalev.domain.model.FaseSync.RECIBIENDO -> "Recibiendo"
            cu.shalev.domain.model.FaseSync.ENVIANDO -> "Enviando"
            cu.shalev.domain.model.FaseSync.APLICANDO -> "Guardando"
        }
        return if (p.conTotal) "$fase · ${(p.fraccion * 100).toInt()} %" else fase
    }

    fun subtituloAjustes(tipo: TipoApp, secundarias: Int, negocio: String): String = when (tipo) {
        TipoApp.SECUNDARIA -> if (negocio.isBlank()) "Secundaria" else "Secundaria de $negocio"
        TipoApp.PRINCIPAL -> when (secundarias) {
            0 -> "Principal · Vincula las apps de tus empleados"
            1 -> "Principal · 1 secundaria"
            else -> "Principal · $secundarias secundarias"
        }
    }

    fun predeterminada(etiqueta: String?): String = if (etiqueta == null) "La predeterminada" else "La predeterminada ($etiqueta)"

    /** Alias y número enmascarado de la tarjeta, en filas separadas para facilitar su lectura. */
    fun etiquetaTarjeta(alias: String?, enmascarado: String): String =
        listOfNotNull(alias?.takeIf { it.isNotBlank() }, enmascarado).joinToString("\n")

    /** 0.20.0 (H5): «Turno abierto desde las 09:30» o, con el cierre pedido, «Cierre pedido · …». null = sin turno. */
    fun turnoEmpleado(e: Empleado, conectada: Boolean): String? {
        val desde = e.turnoAbiertoDesde ?: return null
        return when {
            e.solicitaCierre -> "$SOLICITA_CIERRE · desde las ${Dates.time(desde)}"
            e.cierrePedido && conectada -> "Cierre pedido · se cerrará al terminar la venta en curso"
            e.cierrePedido -> "Cierre pedido · se cerrará al conectar"
            else -> "Turno abierto desde las ${Dates.time(desde)}"
        }
    }

    fun cierrePedido(nombre: String, conectada: Boolean): String =
        if (conectada) "Cierre pedido: el turno de $nombre se cierra ahora o al terminar su venta"
        else "Cierre pedido: el turno de $nombre se cerrará cuando su app se conecte"

    /** Subtítulo de la fila: el turno (si hay) y la conexión. */
    fun subtituloEmpleado(e: Empleado, conectada: Boolean, ahora: Instant): String =
        listOfNotNull(turnoEmpleado(e, conectada), estadoEmpleado(e, conectada, ahora)).joinToString("\n")

    /** 0.21.0 (C4): el máximo lo da la licencia. */
    fun tituloLista(n: Int, limite: Int) = "Secundarias ($n de $limite)"

    fun estadoEmpleado(e: Empleado, conectada: Boolean, ahora: Instant): String {
        val vence = e.codigoVence
        val ultima = e.ultimaSincronizacion
        return when {
            conectada -> "Conectada ahora"
            !e.vinculado && vence != null && vence.isAfter(ahora) -> "Esperando el código · vence a las ${Dates.time(vence)}"
            !e.vinculado -> "Sin vincular · genera un código nuevo"
            ultima != null -> "Última vez: ${Dates.dayTime(ultima)}"
            else -> "Vinculada · aún no ha sincronizado"
        }
    }

    fun conexion(c: EstadoConexion): String = when (c) {
        EstadoConexion.Desconectada -> "Desconectada"
        EstadoConexion.Conectando -> "Buscando la app principal…"
        is EstadoConexion.Conectada -> "Conectada con la app principal"
        is EstadoConexion.SinConexion -> c.motivo
    }

    fun pendientes(n: Int): String = when (n) {
        0 -> "Todo enviado"
        1 -> "1 pendiente de enviar"
        else -> "$n pendientes de enviar"
    }

    fun errorNombre(e: AppError): String? = when (e) {
        is AppError.Validacion -> if (e.regla == AppError.Regla.REQUERIDO) "Escribe el nombre del empleado." else "Máximo ${Vinculacion.MAX_NOMBRE} caracteres."
        is AppError.Duplicado -> "Ya hay una app con ese nombre."
        else -> null
    }

    fun error(e: AppError): String = when (e) {
        is AppError.Validacion -> if (e.campo == "telefono") ERROR_TELEFONO else "No se pudo completar. Inténtalo de nuevo."
        AppError.SinPermiso -> "Ya tienes todas las apps secundarias que cubre tu licencia. Para más, solicita una licencia con más secundarias."
        AppError.LicenciaBloqueada -> "Activa la licencia para vincular apps secundarias."
        AppError.Red.SinConexion -> "Conéctate a una wifi, activa la zona wifi de este teléfono o enciende los datos móviles."
        is AppError.SinPrincipal -> cu.shalev.app.common.TextosSync.sinPrincipal(e.pendientes)
        is AppError.VinculacionRechazada -> e.motivo
        else -> "No se pudo completar. Inténtalo de nuevo."
    }
}
