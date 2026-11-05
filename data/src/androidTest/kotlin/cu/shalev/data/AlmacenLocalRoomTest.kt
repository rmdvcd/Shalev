package cu.shalev.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.repository.AlmacenLocalRepositoryImpl
import cu.shalev.domain.repository.VentanaUbicacion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlmacenLocalRoomTest {
    @Test fun seleccionesIndependientesHistorialSinDuplicadosYVaciado() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ShalevDatabase::class.java).build()
        try {
            val repo = AlmacenLocalRepositoryImpl(db)
            assertTrue(repo.observar().first().isEmpty())
            repo.seleccionar(VentanaUbicacion.INVENTARIO, " Almacén   central ")
            repo.seleccionar(VentanaUbicacion.SERVICIOS, "Tienda")
            repo.seleccionar(VentanaUbicacion.INVENTARIO, "TIENDA")
            val lista = repo.observar().first()
            assertEquals(2, lista.size)
            assertEquals("Tienda", lista.single { it.inventario }.nombre)
            assertEquals("Tienda", lista.single { it.servicios }.nombre)
            repo.seleccionar(VentanaUbicacion.INVENTARIO, "Almacén central")
            assertEquals("Tienda", repo.observar().first().single { it.servicios }.nombre)
            db.mantenimientoDao().borrarConfiguracion()
            assertTrue(repo.observar().first().isEmpty())
        } finally { db.close() }
    }
}
