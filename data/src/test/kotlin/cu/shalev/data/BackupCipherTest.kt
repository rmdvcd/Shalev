package cu.shalev.data

import cu.shalev.data.respaldo.BackupCipher
import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCipherTest {
    // Iteraciones bajas SOLO en tests (velocidad); producción usa 310 000.
    private val cipher = BackupCipher(SecureRandom(), iteraciones = 10_000)
    private val datos = """{"formato":"spvi-respaldo","productos":[1,2,3]}""".repeat(200).toByteArray()

    @Test fun limiteDePlanoYArchivoEsElMismoSinDesbordarLaCabecera() {
        cu.shalev.data.respaldo.exigirTamanoRespaldo(BackupCipher.MAX_PLANO)
        assertThrows(BackupCipher.FormatoException::class.java) {
            cu.shalev.data.respaldo.exigirTamanoRespaldo(BackupCipher.MAX_PLANO + BackupCipher.CABECERA_V4)
        }
        assertThrows(BackupCipher.FormatoException::class.java) { cu.shalev.data.respaldo.exigirTamanoRespaldo(-1) }
    }

    @Test fun `ida y vuelta con la contraseña correcta`() {
        val c = cipher.cifrar(datos, "clave segura".toCharArray())
        assertArrayEquals(datos, cipher.descifrar(c, "clave segura".toCharArray()))
    }

    @Test fun `comprime y no deja texto plano visible`() {
        val c = cipher.cifrar(datos, "x".toCharArray())
        assertTrue("debe comprimir", c.size < datos.size / 4)
        assertTrue(!String(c, Charsets.ISO_8859_1).contains("spvi-respaldo"))
    }

    @Test fun `salt e iv aleatorios producen cifrados distintos`() {
        assertTrue(!cipher.cifrar(datos, "x".toCharArray()).contentEquals(cipher.cifrar(datos, "x".toCharArray())))
    }

    @Test fun `contraseña incorrecta`() {
        val c = cipher.cifrar(datos, "buena".toCharArray())
        assertThrows(BackupCipher.ContrasenaIncorrectaException::class.java) { cipher.descifrar(c, "mala".toCharArray()) }
    }

    @Test fun `alterar la cabecera (iteraciones) invalida el tag`() {
        val c = cipher.cifrar(datos, "k".toCharArray())
        c[20] = (c[20] + 1).toByte() // v4: último byte de iteraciones (17..20): sigue en rango, pero es AAD
        assertThrows(BackupCipher.ContrasenaIncorrectaException::class.java) { cipher.descifrar(c, "k".toCharArray()) }
    }

    @Test fun `alterar la fecha en la cabecera invalida el tag`() {
        val c = cipher.cifrar(datos, "k".toCharArray(), 1_790_000_000_000)
        c[15] = (c[15].toInt() xor 1).toByte()
        assertThrows(BackupCipher.ContrasenaIncorrectaException::class.java) { cipher.descifrar(c, "k".toCharArray()) }
    }

    @Test fun `alterar el contenido es archivo dañado, no contraseña incorrecta`() {
        val c = cipher.cifrar(datos, "k".toCharArray())
        c[c.size - 20] = (c[c.size - 20].toInt() xor 1).toByte()
        val e = assertThrows(BackupCipher.ArchivoDanadoException::class.java) { cipher.descifrar(c, "k".toCharArray()) }
        assertFalse(e.incompleto)
        // Sin contraseña: la inspección ya lo detecta.
        assertThrows(BackupCipher.ArchivoDanadoException::class.java) { cipher.inspeccionar(c) }
    }

    @Test fun `archivo cortado es incompleto y con bytes de más es dañado`() {
        val c = cipher.cifrar(datos, "k".toCharArray())
        assertTrue(assertThrows(BackupCipher.ArchivoDanadoException::class.java) { cipher.inspeccionar(c.copyOf(c.size - 1)) }.incompleto)
        assertTrue(assertThrows(BackupCipher.ArchivoDanadoException::class.java) { cipher.inspeccionar(c.copyOf(BackupCipher.CABECERA - 3)) }.incompleto)
        assertFalse(assertThrows(BackupCipher.ArchivoDanadoException::class.java) { cipher.inspeccionar(c + byteArrayOf(0)) }.incompleto)
    }

    @Test fun `cabecera v4 legible sin contraseña`() {
        val c = cipher.cifrar(datos, "k".toCharArray(), 1_790_000_000_000)
        assertEquals(BackupCipher.Cabecera(4, 1_790_000_000_000, conContrasena = true), cipher.inspeccionar(c))
        val s = cipher.cifrar(datos, CharArray(0), 1_790_000_000_000)
        assertEquals(BackupCipher.Cabecera(4, 1_790_000_000_000, conContrasena = false), cipher.inspeccionar(s))
    }

    /** 0.27.0 (T10): sin contraseña se abre sin pedirla (la contraseña recibida se ignora). */
    @Test fun `ida y vuelta sin contraseña`() {
        val c = cipher.cifrar(datos, CharArray(0))
        assertArrayEquals(datos, cipher.descifrar(c, CharArray(0)))
        assertArrayEquals(datos, cipher.descifrar(c, "cualquiera".toCharArray()))
        assertEquals(0.toByte(), c[8])                                   // indicador = sin contraseña
        assertTrue(!String(c, Charsets.ISO_8859_1).contains("spvi-respaldo"))  // sigue cifrado
    }

    @Test fun `cambiar el indicador invalida el archivo`() {
        val c = cipher.cifrar(datos, CharArray(0)).also { it[8] = 1 }    // «con contraseña» falso: el tag no cuadra
        assertThrows(BackupCipher.ContrasenaIncorrectaException::class.java) { cipher.descifrar(c, "k".toCharArray()) }
        val raro = cipher.cifrar(datos, CharArray(0)).also { it[8] = 7 }
        assertThrows(BackupCipher.FormatoException::class.java) { cipher.inspeccionar(raro) }
    }

    @Test fun `con contraseña y sin escribirla es contraseña incorrecta`() {
        val c = cipher.cifrar(datos, "secreta1".toCharArray())
        assertThrows(BackupCipher.ContrasenaIncorrectaException::class.java) { cipher.descifrar(c, CharArray(0)) }
    }

    @Test fun `un respaldo v3 se sigue leyendo`() {
        val v3 = cipher.cifrarFormatoV3(datos, "secreta1".toCharArray(), 1_790_000_000_000)
        assertEquals(3.toByte(), v3[7])
        assertEquals(BackupCipher.Cabecera(3, 1_790_000_000_000, conContrasena = true), cipher.inspeccionar(v3))
        assertArrayEquals(datos, cipher.descifrar(v3, "secreta1".toCharArray()))
        assertThrows(BackupCipher.ContrasenaIncorrectaException::class.java) { cipher.descifrar(v3, "mala".toCharArray()) }
        assertThrows(BackupCipher.ArchivoDanadoException::class.java) { cipher.inspeccionar(v3.copyOf(v3.size - 1)) }
    }

    @Test fun `los formatos v1 y v2 ya no se leen y se distinguen de un archivo ajeno`() {
        listOf(1, 2).forEach { v ->
            val viejo = cipher.cifrar(datos, "k".toCharArray()).also { it[7] = v.toByte() }
            val e = assertThrows(BackupCipher.VersionAnteriorException::class.java) { cipher.inspeccionar(viejo) }
            assertEquals(v, e.version)
            assertTrue(e.message!!.contains("versión anterior"))
        }
    }

    @Test fun `versión futura es formato no soportado`() {
        val c = cipher.cifrar(datos, "k".toCharArray()).also { it[7] = 9 }
        val e = assertThrows(BackupCipher.FormatoException::class.java) { cipher.inspeccionar(c) }
        assertTrue(e.message!!.contains("más nueva"))
    }

    @Test fun `archivo ajeno o truncado es FormatoException`() {
        assertThrows(BackupCipher.FormatoException::class.java) { cipher.descifrar("PK\u0003\u0004 hola".toByteArray(), "k".toCharArray()) }
        val c = cipher.cifrar(datos, "k".toCharArray())
        assertThrows(BackupCipher.FormatoException::class.java) { cipher.descifrar(ByteArray(3), "k".toCharArray()) }
        val otro = c.copyOf().also { it[0] = 'X'.code.toByte() }
        assertThrows(BackupCipher.FormatoException::class.java) { cipher.descifrar(otro, "k".toCharArray()) }
    }

    @Test fun `cabecera con tamaño esperado`() {
        assertEquals(56, BackupCipher.AAD)
        assertEquals(88, BackupCipher.CABECERA)
        assertEquals(57, BackupCipher.AAD_V4)
        assertEquals(89, BackupCipher.CABECERA_V4)
    }
}
