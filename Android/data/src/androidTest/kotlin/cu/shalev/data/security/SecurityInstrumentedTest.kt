package cu.shalev.data.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.licencia.crypto.EcP256
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** Keystore real del dispositivo/emulador (no se puede simular en JVM). */
@RunWith(AndroidJUnit4::class)
class SecurityInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val aead = KeystoreAead(context)

    // ---------- KeystoreAead ----------

    @Test fun aeadIdaYVuelta() {
        val plain = "licencia".toByteArray()
        assertArrayEquals(plain, aead.decrypt(aead.encrypt(plain, "k".toByteArray()), "k".toByteArray()))
    }

    @Test fun aeadIvAleatorio() {
        val p = ByteArray(32)
        assertFalse(aead.encrypt(p).contentEquals(aead.encrypt(p)))
    }

    @Test fun aeadRechazaOtraAadOManipulacion() {
        val blob = aead.encrypt("x".toByteArray(), "lic.envelope".toByteArray())
        assertThrows(Exception::class.java) { aead.decrypt(blob, "gl.ecdh.v1".toByteArray()) }
        val tampered = blob.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertThrows(Exception::class.java) { aead.decrypt(tampered, "lic.envelope".toByteArray()) }
    }

    // ---------- AndroidDeviceKey ----------

    @Test fun claveDelDispositivoEsP256YPersistente() {
        val a = AndroidDeviceKey(context, aead).publicSpki()
        EcP256.publicKey(a) // lanza si no es P-256 SPKI válida
        val b = AndroidDeviceKey(context, aead).publicSpki() // "reinicio": nueva instancia
        assertArrayEquals(a, b)
    }

    @Test fun ecdhDelDispositivoCoincideConElDelPar() {
        val device = AndroidDeviceKey(context, aead)
        val peer = EcP256.generate()
        val nuestro = device.agree(peer.public)
        val suyo = EcP256.ecdh(peer.private, EcP256.publicKey(device.publicSpki()))
        assertEquals(32, nuestro.size)
        assertArrayEquals(suyo, nuestro)
    }
}
