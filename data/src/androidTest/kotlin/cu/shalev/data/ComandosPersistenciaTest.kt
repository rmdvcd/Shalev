package cu.shalev.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import cu.shalev.core.money.Cup
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.db.entity.EmpleadoEntity
import cu.shalev.data.repository.InsumoRepositoryImpl
import cu.shalev.data.repository.PreciosRepositoryImpl
import cu.shalev.data.repository.ProductoRepositoryImpl
import cu.shalev.data.repository.ServicioRepositoryImpl
import cu.shalev.data.sync.AjustarStockProducto
import cu.shalev.data.sync.Comando
import cu.shalev.data.sync.EjecutorComandos
import cu.shalev.data.sync.ErroresRemotos
import cu.shalev.domain.model.Producto
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Persistencia, protección contra reutilización y concurrencia de claves de comandos de secundarias. */
@RunWith(AndroidJUnit4::class)
class ComandosPersistenciaTest {
    private val contexto = ApplicationProvider.getApplicationContext<Context>()
    private val ahora = Instant.parse("2026-11-10T12:00:00Z")
    private val clock = Clock { ahora }
    private val luis = empleado(id = 3, nombre = "Luis", permisos = "EDITAR_INVENTARIO")

    private fun empleado(id: Long, nombre: String, permisos: String) = EmpleadoEntity(
        id = id, nombre = nombre, permisos = permisos, creadoEn = 0, vinculadoEn = 0, ultimaSincronizacion = null,
        clave = null, codigoToken = null, codigoVence = null, activo = true,
    )

    private fun abrir(nombre: String) = Room.databaseBuilder(contexto, ShalevDatabase::class.java, nombre).build()

    private fun ejecutor(db: ShalevDatabase) = EjecutorComandos(
        db, ProductoRepositoryImpl(db, clock), InsumoRepositoryImpl(db, clock), ServicioRepositoryImpl(db, clock),
        PreciosRepositoryImpl(db),
    )

    private suspend fun producto(db: ShalevDatabase): Long {
        val resultado = ProductoRepositoryImpl(db, clock).crear(
            Producto(
                categoria = "Bebidas", nombre = "Refresco", precioCosto = Cup.ofPesos(60),
                precioVenta = Cup.ofPesos(100), cantidad = 10, creadoEn = ahora,
            ),
            null,
        )
        return (resultado as? AppResult.Ok)?.value ?: error("no se pudo crear el producto: $resultado")
    }

    private suspend fun stock(db: ShalevDatabase, id: Long) = ProductoRepositoryImpl(db, clock).obtener(id)!!.cantidad

    @Test
    fun reintentoTrasCerrarYReabrirLaBaseNoVuelveAAplicarElAjuste() = runBlocking {
        val nombre = "comando-${UUID.randomUUID()}.db"
        contexto.deleteDatabase(nombre)
        var db = abrir(nombre)
        try {
            val id = producto(db)
            val accion = AjustarStockProducto(id, 5, "compra")
            val primera = ejecutor(db).resultado(Comando(91, accion, "recibo-reinicio"), luis)
            assertEquals(15L, primera.valor)
            assertNull(primera.error)
            db.close()

            db = abrir(nombre)
            val reintento = ejecutor(db).resultado(Comando(4, accion, "recibo-reinicio"), luis)
            assertEquals(4L, reintento.id) // el id de transporte es nuevo; se reproduce la respuesta original
            assertEquals(15L, reintento.valor)
            assertNull(reintento.error)
            assertEquals(15L, stock(db, id))
        } finally {
            db.close()
            contexto.deleteDatabase(nombre)
        }
    }

    @Test
    fun mismaClaveConContenidoDistintoSeRechazaSinAlterarElReciboOriginal() = runBlocking {
        val nombre = "comando-${UUID.randomUUID()}.db"
        contexto.deleteDatabase(nombre)
        val db = abrir(nombre)
        try {
            val id = producto(db)
            val executor = ejecutor(db)
            val original = executor.resultado(Comando(1, AjustarStockProducto(id, 5, "compra"), "misma-clave"), luis)
            val conflicto = executor.resultado(Comando(2, AjustarStockProducto(id, 6, "corrección"), "misma-clave"), luis)
            val repetido = executor.resultado(Comando(3, AjustarStockProducto(id, 5, "compra"), "misma-clave"), luis)

            assertEquals(15L, original.valor)
            assertEquals(ErroresRemotos.CLAVE_REUTILIZADA, conflicto.error)
            assertEquals(15L, repetido.valor)
            assertNull(repetido.error)
            assertEquals(15L, stock(db, id))
        } finally {
            db.close()
            contexto.deleteDatabase(nombre)
        }
    }

    @Test
    fun dosReintentosConcurrentesConLaMismaClaveSoloAplicanUnaVez() = runBlocking {
        val nombre = "comando-${UUID.randomUUID()}.db"
        contexto.deleteDatabase(nombre)
        val db = abrir(nombre)
        try {
            val id = producto(db)
            val executor = ejecutor(db)
            val accion = AjustarStockProducto(id, 5, "compra")
            val resultados = listOf(10L, 11L).map { idPeticion ->
                async(Dispatchers.IO) { executor.resultado(Comando(idPeticion, accion, "concurrente"), luis) }
            }.awaitAll()

            assertEquals(setOf(10L, 11L), resultados.map { it.id }.toSet())
            assertTrue(resultados.all { it.valor == 15L && it.error == null })
            assertEquals(15L, stock(db, id))
        } finally {
            db.close()
            contexto.deleteDatabase(nombre)
        }
    }

    @Test
    fun clavesIgualesDeEmpleadosDistintosSonIndependientesYLosErroresSeRecuerdan() = runBlocking {
        val nombre = "comando-${UUID.randomUUID()}.db"
        contexto.deleteDatabase(nombre)
        val db = abrir(nombre)
        try {
            val id = producto(db)
            val executor = ejecutor(db)
            val accion = AjustarStockProducto(id, 2, "ajuste")
            val sinPermiso = empleado(id = 8, nombre = "Ana", permisos = "")
            val denegado = executor.resultado(Comando(1, accion, "por-empleado"), sinPermiso)
            val permisoAgregado = executor.resultado(
                Comando(2, accion, "por-empleado"), sinPermiso.copy(permisos = "EDITAR_INVENTARIO"),
            )
            val luisResultado = executor.resultado(Comando(3, accion, "por-empleado"), luis)

            assertEquals(ErroresRemotos.PERMISO, denegado.error)
            assertEquals(ErroresRemotos.PERMISO, permisoAgregado.error)
            assertEquals(12L, luisResultado.valor)
            assertEquals(12L, stock(db, id))
            assertNotNull(db.syncDao().reciboComando(sinPermiso.id, "por-empleado"))
        } finally {
            db.close()
            contexto.deleteDatabase(nombre)
        }
    }
}
