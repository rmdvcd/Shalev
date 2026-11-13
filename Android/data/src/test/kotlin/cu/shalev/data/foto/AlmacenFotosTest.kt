package cu.shalev.data.foto

import cu.shalev.core.time.Clock
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 0.32.0: el álbum de fotos de la sincronización. Las fotos llegan de la principal por su **nombre** (el UUID .jpg),
 * nunca por su ruta (esa es de otro teléfono): el nombre se valida y el archivo se busca solo dentro de la carpeta.
 */
class AlmacenFotosTest {
    private val dir = File(System.getProperty("java.io.tmpdir"), "shalev-fotos-${System.nanoTime()}")
    private val almacen = AlmacenFotos(dir, Clock { Instant.parse("2026-10-21T12:00:00Z") })

    @Test fun guardaYEncuentraLaFotoPorSuNombre() {
        val f = almacen.guardarBytes("abc-123.jpg", "contenido".toByteArray())
        assertNotNull(f)
        assertEquals("abc-123.jpg", f!!.name)
        assertNotNull(almacen.archivoPorNombre("abc-123.jpg"))
        assertEquals("file://" + f.canonicalFile.absolutePath, almacen.uriDe(f))
    }

    @Test fun unNombreManipuladoNoSaleDeLaCarpeta() {
        assertNull(almacen.guardarBytes("../fuera.jpg", "x".toByteArray()))
        assertNull(almacen.guardarBytes("sub/carpeta.jpg", "x".toByteArray()))
        assertNull(almacen.guardarBytes("sinExtension", "x".toByteArray()))
        assertNull(almacen.archivoPorNombre("../fuera.jpg"))
        assertNull(almacen.archivoPorNombre("otro.jpg"))   // no está
    }

    @Test fun elArchivoDeUnaFotoAjenaSeIgnora() {
        // La ruta de la principal (otra carpeta) no da acceso a su archivo: solo vale el nombre.
        val fuera = File(dir.parentFile, "ajena.jpg")
        fuera.writeText("x")
        assertNull(almacen.archivoDe("file://" + fuera.absolutePath))
        assertNull(almacen.archivoPorNombre("ajena.jpg"))
        fuera.delete()
    }
}
