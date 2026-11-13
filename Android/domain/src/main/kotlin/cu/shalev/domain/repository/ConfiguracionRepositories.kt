package cu.shalev.domain.repository

import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.Licencia
import cu.shalev.domain.model.NivelesMinimos
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.PreajustePrecios
import cu.shalev.domain.model.ConfiguracionInicial
import cu.shalev.domain.model.PasoConfiguracion
import cu.shalev.domain.model.Preferencias
import cu.shalev.licencia.ActivationResult
import cu.shalev.licencia.SolicitudInput
import java.io.InputStream
import java.time.Instant
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface LicenciaRepository {
    /** null mientras se evalúa por primera vez (splash). */
    val snapshot: StateFlow<Licencia?>
    suspend fun refrescar(): Licencia
    suspend fun construirSolicitud(input: SolicitudInput): AppResult<cu.shalev.licencia.SolicitudGenerada>
    suspend fun activar(mensaje: String): ActivationResult
    /** 0.25.0: aplica la lista pública de revocadas; true = la licencia de este teléfono quedó revocada. */
    suspend fun aplicarRevocaciones(texto: String): Boolean = false

    /**
     * 0.31.0 (mitigación 7, Opción A): firma el sub-token que autoriza a [dispositivo] como app secundaria en la
     * ranura [ranura]. `null` = no se puede emitir (sin firmante en el build, app bloqueada, cuota 0 o ranura fuera
     * de la cuota de la licencia).
     */
    suspend fun emitirSubToken(dispositivo: String, ranura: Int): cu.shalev.domain.model.SubTokenEmitido? = null
}

interface PerfilRepository {
    val perfil: Flow<Perfil>
    /** Reemplaza el perfil completo (datos + listas) en una transacción. Asigna ids a tarjetas/teléfonos nuevos. */
    suspend fun guardar(perfil: Perfil)
}

interface PreciosRepository {
    fun observarPreajustes(): Flow<List<PreajustePrecios>>
    suspend fun preajustesActivos(): List<PreajustePrecios>
    suspend fun guardarPreajuste(p: PreajustePrecios): AppResult<Long>
    suspend fun eliminarPreajuste(id: Long): AppResult<Unit>
}

interface PreferenciasRepository {
    val preferencias: Flow<Preferencias>
    val onboardingCompletado: Flow<Boolean>
    suspend fun completarOnboarding()
    suspend fun guardarNiveles(n: NivelesMinimos)
    /** 0.21.0 (C12): se normaliza con [cu.shalev.domain.model.Modulo.normalizar]. */
    suspend fun guardarModulos(m: Set<cu.shalev.domain.model.Modulo>)
    suspend fun guardarEmpleadosPrevistos(n: Int)
    /** Restauración desde un respaldo. */
    suspend fun reemplazar(p: Preferencias)
}

/** 0.27.0: ajustes de este teléfono (DataStore cifrado; fuera del respaldo). */
interface AjustesDispositivoRepository {
    val ajustes: kotlinx.coroutines.flow.Flow<cu.shalev.domain.model.AjustesDispositivo>
    suspend fun guardarAccesoConClave(activo: Boolean)
    suspend fun guardarEligioSecundaria(valor: Boolean)
}

/** Progreso del asistente de primera ejecución (DataStore cifrado, propio de este dispositivo). */
interface ConfiguracionInicialRepository {
    val estado: Flow<ConfiguracionInicial>
    suspend fun confirmar(paso: PasoConfiguracion)
    suspend fun marcarCamaraSolicitada()
    /** 0.31.0 (P9): el sistema ya mostró el diálogo de permisos de la primera apertura. */
    suspend fun marcarPermisosInicialesPedidos()
}

/** Qué trajo o llevó un respaldo. Siempre es completo: base de datos + configuración (P17: un solo tipo). */
data class ResumenRespaldo(val productos: Int, val insumos: Int, val ventas: Int, val movimientos: Int)

/** 0.25.0: estado propio de la app (recordatorio de respaldo, versiones, licencia recuperable). No va en el respaldo. */
interface EstadoAppRepository {
    val estado: Flow<cu.shalev.domain.model.EstadoApp>
    suspend fun actual(): cu.shalev.domain.model.EstadoApp
    suspend fun editar(cambio: (cu.shalev.domain.model.EstadoApp) -> cu.shalev.domain.model.EstadoApp)
    suspend fun borrar()
}

/**
 * 0.25.0: GitHub Releases (repositorio público configurado en el build). Sin datos del negocio ni identificadores.
 * [repoConfigurado] false = marcador vacío: no se consulta nada.
 */
interface ActualizacionesRepository {
    val repoConfigurado: Boolean
    /** Última Release publicada (null si no hay ninguna). */
    suspend fun ultimaVersion(): AppResult<cu.shalev.domain.model.InfoActualizacion?>
    /** Texto de `revocadas.json` (Release `revocaciones`). */
    suspend fun listaRevocaciones(): AppResult<String>
    /**
     * Descarga (reanudable) el APK a la caché privada y comprueba su SHA-256. [progreso] (bytes, total).
     * Error Validacion("sha256") si no coincide (el archivo se borra).
     */
    suspend fun descargar(info: cu.shalev.domain.model.InfoActualizacion, progreso: (Long, Long) -> Unit): AppResult<java.io.File>
}

interface MantenimientoRepository {
    suspend fun borrarTodo(): AppResult<Unit>
}

/**
 * Ajustes de pantalla de ESTE teléfono que deben sobrevivir al cierre de la app (el período elegido en Inicio,
 * la pestaña y los filtros de Registros, los filtros de Inventario y Servicios, la tasa de la calculadora de
 * cambio…): antes vivían solo en memoria o en el estado del proceso y se perdían al cerrar y volver a abrir.
 *
 * Los valores son cadenas opacas ya codificadas por la capa de pantallas (una clave por ajuste); el dominio no
 * interpreta su contenido. Como el [cu.shalev.domain.model.EstadoApp], no viajan en el respaldo: son del teléfono.
 */
interface EstadoPantallasRepository {
    /** Valor guardado de [clave] (null = nunca se guardó); emite los cambios posteriores. */
    fun observar(clave: String): Flow<String?>

    /** Guarda [valor] para [clave] (null = olvidar el ajuste y volver al valor por defecto). */
    suspend fun guardar(clave: String, valor: String?)
}

/** Etapas visibles de exportar/importar un respaldo (la derivación de la clave tarda 1–3 s en un teléfono modesto). */
enum class EtapaRespaldo { PREPARANDO, CIFRANDO, ESCRIBIENDO, LEYENDO, VERIFICANDO, DESCIFRANDO, IMPORTANDO }

/**
 * Lo que se sabe de un archivo `.shalev` SIN la contraseña: versión del formato y fecha de creación (van en la
 * cabecera autenticada; no son datos personales). [creadoEn] null si la cabecera no la trae.
 */
/** [conContrasena] = false (0.27.0, T10): el archivo se abre sin pedir contraseña. */
data class InfoRespaldo(val version: Int, val creadoEn: Instant?, val bytes: Long, val conContrasena: Boolean = true)

/**
 * Respaldo portable y completo: JSON versionado, comprimido y cifrado con contraseña del usuario
 * (PBKDF2-HMAC-SHA256 + AES-256-GCM). La BD SQLCipher está atada al Keystore y no es portable por sí misma.
 */
interface RespaldoRepository {
    /** [contrasena] vacía = sin contraseña (0.27.0, T10: opcional; auditoría S-01: la pantalla la trae encendida). */
    suspend fun exportar(
        contrasena: CharArray, destino: OutputStream, progreso: (EtapaRespaldo) -> Unit = {},
    ): AppResult<ResumenRespaldo>

    /** Comprueba el archivo sin contraseña: ¿es de Shalev?, ¿está completo e intacto? No modifica nada. */
    suspend fun inspeccionar(origen: InputStream): AppResult<InfoRespaldo>

    /** Reemplaza TODOS los datos (si el archivo no tiene contraseña, [contrasena] se ignora) (base de datos y preferencias) en una sola transacción de Room (todo o nada). */
    suspend fun importar(origen: InputStream, contrasena: CharArray, progreso: (EtapaRespaldo) -> Unit = {}): AppResult<ResumenRespaldo>
}
