package cu.shalev.domain.repository

import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.CodigoVinculacion
import cu.shalev.domain.model.Empleado
import cu.shalev.domain.model.EstadoPrincipal
import cu.shalev.domain.model.EstadoSecundaria
import cu.shalev.domain.model.LicenciaPrincipal
import cu.shalev.domain.model.PermisoEmpleado
import cu.shalev.domain.model.PermisosApp
import cu.shalev.domain.model.TipoApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * P37: tipo de esta app (Principal/Secundaria) y lo que puede hacer. Lo persiste :data (DataStore cifrado).
 * [tipo] arranca en PRINCIPAL: las instalaciones de siempre no cambian.
 */
interface TipoAppRepository {
    val tipo: StateFlow<TipoApp>
    val permisos: Flow<PermisosApp>
    /** Solo en una secundaria: la licencia de la principal recibida en la última sincronización (null en la principal). */
    val licenciaPrincipal: Flow<LicenciaPrincipal?>
}

/** P37: lado de la app PRINCIPAL. Las secundarias se llaman «empleados» porque cada una es la app de una persona. */
interface PrincipalRepository {
    fun observarEmpleados(): Flow<List<Empleado>>
    val estado: StateFlow<EstadoPrincipal>

    /** Crea la secundaria y su QR (un solo uso, 10 min). Errores: Validacion("nombre"), SinPermiso (licencia/límite). */
    suspend fun agregarEmpleado(nombre: String, permisos: Set<PermisoEmpleado>): AppResult<CodigoVinculacion>
    /** QR nuevo para la misma persona (teléfono cambiado o QR vencido). Anula la clave anterior. */
    suspend fun nuevoCodigo(empleadoId: Long): AppResult<CodigoVinculacion>
    suspend fun cambiarPermisos(empleadoId: Long, permisos: Set<PermisoEmpleado>): AppResult<Unit>
    /** 0.20.0 (H4): tarjeta y teléfono con los que cobra ese empleado (null = los predeterminados del dueño). */
    suspend fun cambiarCobro(empleadoId: Long, tarjetaId: Long?, telefonoId: Long?): AppResult<Unit>
    /**
     * 0.20.0 (H5): pide cerrar el turno abierto de ese empleado. Se le avisa al momento si está conectado o al
     * conectar; su app lo cierra en cuanto no haya una venta en curso (P43: nunca durante una venta).
     */
    suspend fun pedirCierre(empleadoId: Long): AppResult<Unit>
    /** 0.21.0 (C6): aprueba la solicitud de cierre del empleado (= [pedirCierre]: se cierra al conectar, nunca en una venta). */
    suspend fun aprobarCierre(empleadoId: Long): AppResult<Unit>
    /** 0.21.0 (C6): rechaza la solicitud; se le avisa en la próxima sincronización y su turno sigue abierto. */
    suspend fun rechazarCierre(empleadoId: Long): AppResult<Unit>
    /** Quita la secundaria: deja de sincronizar y, al conectarse, borra sus datos. Sus ventas ya recibidas se quedan. */
    suspend fun quitarEmpleado(empleadoId: Long): AppResult<Unit>
}

/** P37: lado de la app SECUNDARIA. */
interface SecundariaRepository {
    val estado: StateFlow<EstadoSecundaria>

    /**
     * Lee el QR de la principal, acuerda la clave y descarga el catálogo. Solo si todo sale bien se borran los datos
     * propios de este teléfono y la app pasa a SECUNDARIA (si algo falla, no se toca nada).
     */
    suspend fun vincular(contenidoQr: String, telefono: String): AppResult<Unit>
    /**
     * 0.21.0 (C6, P46 «a»): el empleado no cierra su turno: lo solicita. Se envía en la próxima sincronización (sin
     * conexión, espera) y el empleado sigue vendiendo hasta que el dueño lo apruebe.
     */
    suspend fun solicitarCierre(contado: cu.shalev.core.money.Cup): AppResult<Unit>
    /** Envía lo pendiente y trae el catálogo. Error SinPrincipal si no hay conexión. */
    suspend fun sincronizarAhora(): AppResult<Unit>
    /** Borra los datos de este teléfono y vuelve a PRINCIPAL. Lo no enviado se pierde (la UI avisa antes). */
    suspend fun desvincular(): AppResult<Unit>
    /** 0.20.0 (H5): el empleado leyó el aviso «Tu turno lo cerró el encargado» (0.21.0: o «no aprobó el cierre»). */
    suspend fun descartarAvisoCierre()
    /**
     * 0.25.0: descarga por la red local el APK que ofrece la principal ([EstadoSecundaria.apk]) y comprueba su SHA-256.
     * [progreso] (bytes, total). Devuelve el archivo listo para instalar.
     */
    suspend fun descargarApk(progreso: (Long, Long) -> Unit): AppResult<java.io.File> = AppResult.Err(cu.shalev.core.result.AppError.NoEncontrado)
}

/**
 * P37: regla «no se empieza un turno nuevo sin sincronizar». La principal siempre puede; una secundaria primero
 * envía lo pendiente y recibe el catálogo (Err SinPrincipal si no lo consigue).
 */
interface PuertaTurno {
    suspend fun antesDeAbrir(): AppResult<Unit>

    companion object {
        val SIEMPRE: PuertaTurno = object : PuertaTurno {
            override suspend fun antesDeAbrir(): AppResult<Unit> = AppResult.Ok(Unit)
        }
    }
}
