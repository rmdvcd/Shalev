package cu.shalev.app.soporte

import cu.shalev.app.ayuda.AyudaContenido
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AyudaSoporteTest {
    @Test fun fichaDeSoporteExacta() {
        assertEquals(
            listOf(
                "Desarrollador" to "Ing. Ronnie Montero Duarte",
                "CI" to "91040922502",
                "Teléfono" to "51815604",
                "Especialidad" to "Desarrollo de sistemas y aplicaciones multiplataforma",
            ),
            SoporteInfo.filas,
        )
    }

    @Test fun manualBreveYCompleto() {
        val temas = AyudaContenido.temas
        // El resumen cubre las pantallas y mantiene cada tema breve.
        assertTrue(temas.size in 6..25)
        assertEquals(temas.size, temas.map { it.titulo }.toSet().size)
        // Cada sección existe y tiene temas; los temas de una sección son de esa sección.
        val secciones = AyudaContenido.seccionesPara(cu.shalev.domain.model.Modulo.TODOS)
        assertTrue(secciones.isNotEmpty())
        secciones.forEach { (c, ts) ->
            assertTrue(c.name, ts.isNotEmpty())
            ts.forEach { assertEquals(c, it.categoria) }
        }
        temas.forEach { t ->
            assertTrue(t.titulo, t.pasos.isNotEmpty() && t.pasos.size <= 5)
            t.pasos.forEach { p -> assertTrue("Paso demasiado largo: $p", p.length <= 110) }
        }
        // Cubre lo imprescindible para un usuario sin conocimientos previos.
        val todo = temas.joinToString(" ") { it.titulo + " " + it.pasos.joinToString(" ") }
        listOf("turno", "Vender", "Inventario", "Respaldo", "Licencia", "Soporte").forEach {
            assertTrue("Falta $it", todo.contains(it))
        }
    }
    @Test fun manualPorVentanaYContextoSegunModulos() {
        val todos = cu.shalev.domain.model.Modulo.TODOS
        val secciones = AyudaContenido.seccionesPara(todos)
        assertEquals(AyudaContenido.temas, secciones.flatMap { it.second })
        val inicio = cu.shalev.app.ayuda.PestanaAyuda.INICIO.temas(todos).map { it.titulo }
        assertTrue("Abrir y cerrar el turno" in inicio)
        val registros = cu.shalev.app.ayuda.PestanaAyuda.REGISTROS.temas(todos).map { it.titulo }
        assertTrue("Corregir operaciones" in registros)
        assertTrue(cu.shalev.app.ayuda.PestanaAyuda.INVENTARIO.temas(setOf(cu.shalev.domain.model.Modulo.SERVICIOS)).isEmpty())
        assertTrue(secciones.any { it.first == cu.shalev.app.ayuda.CategoriaAyuda.RESPALDO })
        assertTrue(secciones.any { it.first == cu.shalev.app.ayuda.CategoriaAyuda.VENTA })
    }

}
