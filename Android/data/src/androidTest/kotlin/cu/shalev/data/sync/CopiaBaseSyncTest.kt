package cu.shalev.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.core.result.AppResult
import cu.shalev.data.db.entity.EmpleadoEntity
import cu.shalev.domain.repository.EtapaRespaldo
import cu.shalev.domain.repository.InfoRespaldo
import cu.shalev.domain.repository.RespaldoRepository
import cu.shalev.domain.repository.ResumenRespaldo
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Prueba la compuerta de autorización y los bloques que genera el servidor para una copia completa. */
@RunWith(AndroidJUnit4::class)
class CopiaBaseSyncTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val cache = File(context.cacheDir, "copias_sync")
    private val solicitud = "38ea980e-f4cc-4504-9e99-10f870f28b23"
    private val contenido = ByteArray(CopiaBaseSync.TAMANO_BLOQUE * 2 + 123) { ((it * 73 + 19) and 0xff).toByte() }
    private val respaldo = RespaldoFalso(contenido)
    private val servicio = CopiaBaseSync(context, respaldo, Dispatchers.IO)

    @After fun limpiar() { cache.deleteRecursively() }

    @Test fun noSirveLaBaseSinSolicitudActivaYAutorizada() = runBlocking {
        val pendiente = empleado(estado = "PENDIENTE")
        val respuesta = servicio.bloque(pendiente, solicitud, 0, 1)
        assertEquals(CopiaBaseSync.ERROR_NO_AUTORIZADA, respuesta.error)
        assertEquals(0, respaldo.exportaciones)

        val otraSolicitud = servicio.bloque(empleado(), "4ea10bb2-d3cb-4e68-8dc9-1c7e2d107301", 0, 2)
        assertEquals(CopiaBaseSync.ERROR_NO_AUTORIZADA, otraSolicitud.error)
        assertEquals(0, respaldo.exportaciones)

        assertEquals(CopiaBaseSync.ERROR_NO_AUTORIZADA, servicio.bloque(empleado(activo = false), solicitud, 0, 3).error)
        assertEquals(CopiaBaseSync.ERROR_NO_AUTORIZADA, servicio.bloque(empleado(estado = "CONSUMIDA"), solicitud, 0, 4).error)
        assertEquals(0, respaldo.exportaciones)
    }

    @Test fun transmiteBloquesDeLaMismaCopiaYSoloUnFinDeArchivo() = runBlocking {
        val autorizada = empleado()
        val recibidos = ByteArrayOutputStream()
        var offset = 0L
        var id = 10L
        var cantidadBloques = 0
        while (offset < contenido.size) {
            val bloque = servicio.bloque(autorizada, solicitud, offset, id++)
            assertNull(bloque.error)
            assertEquals(solicitud, bloque.solicitudId)
            assertEquals(offset, bloque.desde)
            assertEquals(contenido.size.toLong(), bloque.total)
            assertEquals(sha256(contenido), bloque.sha256)
            val datos = Base64.getDecoder().decode(bloque.datos)
            assertTrue(datos.isNotEmpty())
            assertTrue(datos.size <= CopiaBaseSync.TAMANO_BLOQUE)
            recibidos.write(datos)
            offset += datos.size
            cantidadBloques++
        }

        // El protocolo usa un bloque vacío en el offset final para que el cliente confirme el fin de archivo.
        val fin = servicio.bloque(autorizada, solicitud, offset, id)
        assertNull(fin.error)
        assertEquals(offset, fin.desde)
        assertEquals("", fin.datos)
        assertEquals(contenido.size.toLong(), fin.total)
        assertEquals(sha256(contenido), fin.sha256)
        assertTrue(cantidadBloques >= 3)
        assertEquals(contenido.size, recibidos.size())
        assertTrue(contenido.contentEquals(recibidos.toByteArray()))
        assertEquals(1, respaldo.exportaciones)

        // Aunque la copia esté en caché, una autorización consumida o revocada ya no sirve nuevos bloques.
        assertEquals(CopiaBaseSync.ERROR_NO_AUTORIZADA,
            servicio.bloque(empleado(estado = "CONSUMIDA"), solicitud, 0, id + 1).error)
        assertFalse(recibidos.toByteArray().isEmpty())
        assertEquals(1, respaldo.exportaciones)
    }

    private fun empleado(estado: String = "AUTORIZADA", activo: Boolean = true) = EmpleadoEntity(
        id = 41,
        nombre = "Ana",
        permisos = "VENDER_PRODUCTOS",
        creadoEn = 0,
        vinculadoEn = 1,
        ultimaSincronizacion = null,
        clave = "clave-de-prueba",
        codigoToken = null,
        codigoVence = null,
        activo = activo,
        solicitudImportacionId = solicitud,
        estadoSolicitudImportacion = estado,
    )

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private class RespaldoFalso(private val contenido: ByteArray) : RespaldoRepository {
        var exportaciones = 0
            private set

        override suspend fun exportar(
            contrasena: CharArray,
            destino: OutputStream,
            progreso: (EtapaRespaldo) -> Unit,
        ): AppResult<ResumenRespaldo> = error("La prueba solo usa exportarParaSincronizacion")

        override suspend fun exportarParaSincronizacion(destino: OutputStream): AppResult<ResumenRespaldo> {
            exportaciones++
            destino.write(contenido)
            return AppResult.Ok(ResumenRespaldo(0, 0, 0, 0))
        }

        override suspend fun inspeccionar(origen: InputStream): AppResult<InfoRespaldo> = error("No se usa en esta prueba")

        override suspend fun importar(
            origen: InputStream,
            contrasena: CharArray,
            progreso: (EtapaRespaldo) -> Unit,
        ): AppResult<ResumenRespaldo> = error("No se usa en esta prueba")
    }
}
