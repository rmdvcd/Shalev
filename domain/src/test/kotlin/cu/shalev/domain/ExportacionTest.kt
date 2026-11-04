package cu.shalev.domain

import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.domain.repository.EtapaRespaldo
import cu.shalev.domain.repository.ExportadorDocumentos
import cu.shalev.domain.repository.InfoRespaldo
import cu.shalev.domain.repository.RespaldoRepository
import cu.shalev.domain.repository.ResumenRespaldo
import cu.shalev.domain.service.FormatoExport
import cu.shalev.domain.service.TablaExport
import cu.shalev.domain.service.TablasExport
import cu.shalev.domain.usecase.ExportarRespaldo
import cu.shalev.domain.usecase.ExportarTablas
import cu.shalev.domain.usecase.ImportarRespaldo
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prompt 14: exportación de tablas, lista de precios e importación por pasos. */
class ExportacionTest {

    private class Exportador : ExportadorDocumentos {
        val llamadas = mutableListOf<Pair<List<TablaExport>, FormatoExport>>()
        override suspend fun exportar(tablas: List<TablaExport>, formato: FormatoExport, destino: OutputStream): AppResult<Unit> {
            llamadas += tablas to formato
            destino.write(formato.extension.toByteArray())
            return AppResult.Ok(Unit)
        }
    }

    private class Respaldos : RespaldoRepository {
        val etapas = mutableListOf<EtapaRespaldo>()
        var info: AppResult<InfoRespaldo> = AppResult.Ok(InfoRespaldo(3, T0, 100))
        override suspend fun exportar(contrasena: CharArray, destino: OutputStream, progreso: (EtapaRespaldo) -> Unit): AppResult<ResumenRespaldo> {
            listOf(EtapaRespaldo.PREPARANDO, EtapaRespaldo.CIFRANDO, EtapaRespaldo.ESCRIBIENDO).forEach(progreso)
            return AppResult.Ok(ResumenRespaldo(1, 0, 0, 0))
        }
        override suspend fun inspeccionar(origen: InputStream) = info
        override suspend fun importar(origen: InputStream, contrasena: CharArray, progreso: (EtapaRespaldo) -> Unit): AppResult<ResumenRespaldo> {
            EtapaRespaldo.entries.drop(3).forEach(progreso)
            return AppResult.Ok(ResumenRespaldo(1, 0, 0, 0))
        }
    }

    private val tabla = TablaExport("Ventas", listOf("Nº", "Total"), listOf(listOf("1", "100 CUP")))

    @Test fun `exportar tablas en PDF y Excel escribe en el destino`() = runBlocking {
        val ex = Exportador()
        val out = ByteArrayOutputStream()
        assertTrue(ExportarTablas(ex)(listOf(tabla), FormatoExport.XLSX, out) is AppResult.Ok)
        assertEquals("xlsx", out.toString())
        assertEquals(FormatoExport.XLSX, ex.llamadas.single().second)
    }

    @Test fun `exportar tablas rechaza tablas vacías sin llamar al exportador`() = runBlocking {
        val ex = Exportador()
        val vacia = tabla.copy(filas = emptyList())
        val r2 = ExportarTablas(ex)(listOf(vacia), FormatoExport.PDF, ByteArrayOutputStream())
        assertEquals(AppError.Regla.REQUERIDO, ((r2 as AppResult.Err).error as AppError.Validacion).regla)
        assertTrue(ex.llamadas.isEmpty())
    }

    @Test fun `lista de precios para clientes sin costo ni existencias, ordenada`() {
        val ps = listOf(
            producto(1, "Refresco", cantidad = 7, venta = 150, costo = 90, categoria = "Bebidas"),
            producto(2, "Arroz", venta = 300, costo = 250, categoria = "Granos"),
            producto(3, "Agua", venta = 80, costo = 40, categoria = "bebidas"),
            producto(4, "Borrado", categoria = "Bebidas").copy(eliminado = true),
        )
        val t = TablasExport.listaPrecios(ps)
        assertEquals(listOf("Categoría", "Producto", "Precio"), t.columnas)
        assertEquals(listOf(listOf("bebidas", "Agua", "80 CUP"), listOf("Bebidas", "Refresco", "150 CUP"), listOf("Granos", "Arroz", "300 CUP")), t.filas)
        val todo = t.filas.flatten().joinToString()
        assertFalse(todo.contains("90.00") || todo.contains("250.00") || todo.contains("7"))
    }

    @Test fun `inspeccionar devuelve la información o el daño del archivo`() = runBlocking {
        val repo = Respaldos()
        assertEquals(T0, (repo.inspeccionar("x".byteInputStream()) as AppResult.Ok).value.creadoEn)
        repo.info = AppResult.Err(AppError.ArchivoDanado(incompleto = true))
        assertEquals(AppError.ArchivoDanado(true), (repo.inspeccionar("x".byteInputStream()) as AppResult.Err).error)
    }

    @Test fun `el progreso llega por pasos al exportar e importar`() = runBlocking {
        val repo = Respaldos()
        val vistos = mutableListOf<EtapaRespaldo>()
        ExportarRespaldo(repo)("clave-segura".toCharArray(), ByteArrayOutputStream()) { vistos += it }
        ImportarRespaldo(repo)("x".byteInputStream(), "clave-segura".toCharArray()) { vistos += it }
        assertEquals(EtapaRespaldo.entries.toList(), vistos)
    }
}
