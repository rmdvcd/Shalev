package cu.shalev.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.repository.LocalRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlmacenLocalRoomTest {
    @Test fun localUnicoNormalizadoSinDuplicadosYVaciado() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ShalevDatabase::class.java).build()
        try {
            val repo = LocalRepositoryImpl(db)
            assertTrue(repo.observar().first().isEmpty())
            repo.seleccionar(" Almacén   central ")
            assertEquals("Almacén central", repo.observar().first().single().nombre)
            repo.seleccionar("ALMACÉN CENTRAL")
            assertEquals(1, repo.observar().first().size)
            repo.seleccionar("Tienda")
            assertEquals("Tienda", repo.observar().first().single().nombre)
            db.mantenimientoDao().borrarConfiguracion()
            assertTrue(repo.observar().first().isEmpty())
        } finally { db.close() }
    }
}
