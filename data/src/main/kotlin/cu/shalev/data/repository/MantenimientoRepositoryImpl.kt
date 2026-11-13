package cu.shalev.data.repository

import android.content.Context
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.db.tx
import cu.shalev.domain.di.IoDispatcher
import cu.shalev.domain.model.Preferencias
import cu.shalev.domain.repository.MantenimientoRepository
import cu.shalev.domain.repository.PreferenciasRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Borrado total de este teléfono. Room en UNA transacción (mismas consultas que la restauración de respaldo), después las
 * preferencias a sus valores por defecto y por último los archivos propios: fotos (`filesDir/fotos`) y
 * temporales de compartir (`cacheDir/compartir`). Quien llama revoca antes la licencia si corresponde.
 * Si algo falla se devuelve Almacenamiento sin detalles.
 */
@Singleton
class MantenimientoRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: ShalevDatabase,
    private val preferencias: PreferenciasRepository,
    private val ds: cu.shalev.data.local.SecureDataStore,
    @IoDispatcher private val io: CoroutineDispatcher,
) : MantenimientoRepository {

    override suspend fun borrarTodo(): AppResult<Unit> {
        currentCoroutineContext().ensureActive()
        return withContext(NonCancellable) {
            val bd = db.tx {
                val m = db.mantenimientoDao()
                db.gestionDao().apply { borrarConsumos(); borrarLotes(); borrarRecepciones(); borrarRevisiones(); borrarFichas(); borrarConfiguracion()
                    guardarConfiguracion(cu.shalev.data.db.entity.ConfiguracionNegocioEntity(preferenciasJson = cu.shalev.data.local.ShalevJson.encodeToString(cu.shalev.data.dto.PreferenciasDto.serializer(), cu.shalev.data.dto.PreferenciasDto())))
                }
                m.borrarRecibosComandos()
                db.openHelper.writableDatabase.execSQL("DELETE FROM empleado")
                m.borrarOperacion()
                m.borrarConfiguracion()
            }
            if (bd is AppResult.Err) return@withContext bd
            withContext(io) {
                try {
                    ds.put("pref.v1", null)
                    context.getSharedPreferences("fichas_costo", Context.MODE_PRIVATE).edit().clear().commit().also { check(it) }
                    ds.put("sync.secundaria.v1", null); ds.put("sync.tipo", null); ds.put("sync.negocio", null)
                    // 0.25.0: también el APK de actualización guardado o a medio recibir.
                    listOf(File(context.filesDir, "fotos"), File(context.cacheDir, "compartir"), File(context.filesDir, "actualizacion"), File(context.cacheDir, "actualizacion"), File(context.filesDir, "respaldo_actualizacion")).forEach { it.deleteRecursively() }
                    AppResult.Ok(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppResult.Err(AppError.Almacenamiento)
                }
            }
        }
    }
}
