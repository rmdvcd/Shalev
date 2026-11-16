package cu.shalev.data.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** IPv4 de las interfaces disponibles y descubrimiento Bonjour/mDNS, sin permisos de red adicionales. */
@Singleton
class RedLocal @Inject constructor(@ApplicationContext private val context: Context) {

    private val nsd: NsdManager? get() = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private var registro: NsdManager.RegistrationListener? = null

    /**
     * IPv4 candidatas, priorizando primero Wi-Fi/Zona Wi-Fi y otras redes privadas; las interfaces celulares van al
     * final porque sus IP privadas/CGNAT normalmente no admiten conexiones entrantes. Se conservan como último recurso
     * cuando el operador sí permite tráfico entre sus abonados.
     */
    fun direcciones(): List<String> = runCatching {
        candidatas(
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { interfaz ->
                    interfaz.inetAddresses.toList().filterIsInstance<Inet4Address>()
                        .map { CandidataIPv4(interfaz.name, it) }
                },
        )
    }.getOrDefault(emptyList())

    fun anunciar(negocioId: String, puerto: Int) {
        dejarDeAnunciar()
        val m = nsd ?: return
        val info = NsdServiceInfo().apply {
            serviceName = nombreServicio(negocioId)
            serviceType = TIPO
            port = puerto
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(s: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(s: NsdServiceInfo, e: Int) = Unit
            override fun onServiceUnregistered(s: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(s: NsdServiceInfo, e: Int) = Unit
        }
        runCatching { m.registerService(info, NsdManager.PROTOCOL_DNS_SD, l) }.onSuccess { registro = l }
    }

    fun dejarDeAnunciar() {
        val l = registro ?: return
        registro = null
        runCatching { nsd?.unregisterService(l) }
    }

    /** Busca la principal de [negocioId] en la red local. null si no aparece en [ms]. */
    suspend fun buscar(negocioId: String, ms: Long = 6_000): Pair<String, Int>? {
        val m = nsd ?: return null
        val nombre = nombreServicio(negocioId)
        var escucha: NsdManager.DiscoveryListener? = null
        val encontrado = (try {
            withTimeoutOrNull(ms) {
                suspendCancellableCoroutine<NsdServiceInfo?> { cont ->
                    val l = object : NsdManager.DiscoveryListener {
                        override fun onDiscoveryStarted(t: String) = Unit
                        override fun onDiscoveryStopped(t: String) = Unit
                        override fun onStartDiscoveryFailed(t: String, e: Int) { if (cont.isActive) cont.resume(null) }
                        override fun onStopDiscoveryFailed(t: String, e: Int) = Unit
                        override fun onServiceLost(s: NsdServiceInfo) = Unit
                        override fun onServiceFound(s: NsdServiceInfo) {
                            if (s.serviceName == nombre && cont.isActive) cont.resume(s)
                        }
                    }
                    escucha = l
                    runCatching { m.discoverServices(TIPO, NsdManager.PROTOCOL_DNS_SD, l) }
                        .onFailure { escucha = null; if (cont.isActive) cont.resume(null) }
                }
            }
        } finally {
            escucha?.let { l -> runCatching { m.stopServiceDiscovery(l) } }
        }) ?: return null
        return withTimeoutOrNull(ms) { resolver(m, encontrado) }
    }

    @Suppress("DEPRECATION") // resolveService sigue funcionando en API 34+; no hay sustituto para minSdk 26.
    private suspend fun resolver(m: NsdManager, s: NsdServiceInfo): Pair<String, Int>? = suspendCancellableCoroutine { cont ->
        runCatching {
            m.resolveService(s, object : NsdManager.ResolveListener {
                override fun onResolveFailed(s: NsdServiceInfo, e: Int) { if (cont.isActive) cont.resume(null) }
                override fun onServiceResolved(s: NsdServiceInfo) {
                    val host = s.host?.hostAddress
                    if (cont.isActive) cont.resume(host?.let { it to s.port })
                }
            })
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }

    companion object {
        const val TIPO = "_shalev._tcp."
        fun nombreServicio(negocioId: String) = "Shalev-" + negocioId.take(12)

        /** Compatibilidad para tests/llamadores que solo tienen direcciones, sin la interfaz que las publicó. */
        fun utiles(direcciones: List<Inet4Address>): List<String> = candidatas(
            direcciones.map { CandidataIPv4("", it) },
        )

        /** Ordena por interfaz antes de limitar el QR; no deja que una IP celular 10.x adelante a la Wi-Fi 192.168.x. */
        internal fun candidatas(direcciones: List<CandidataIPv4>): List<String> = direcciones
            .filter { candidata ->
                !candidata.direccion.isAnyLocalAddress && !candidata.direccion.isLoopbackAddress &&
                    !candidata.direccion.isLinkLocalAddress && !candidata.direccion.isMulticastAddress &&
                    candidata.direccion.hostAddress?.let { DireccionIPv4Sync.esCandidata(it) } == true
            }
            .sortedBy(::prioridad)
            .distinctBy { it.direccion.hostAddress }
            .take(DireccionIPv4Sync.MAX_DIRECCIONES)
            .mapNotNull { it.direccion.hostAddress }

        private fun prioridad(candidata: CandidataIPv4): Int {
            val interfaz = candidata.interfaz.lowercase(Locale.ROOT)
            val wifiOEthernet = listOf("wlan", "wifi", "ap", "softap", "swlan", "p2p", "eth", "en").any(interfaz::startsWith)
            val celular = listOf("rmnet", "ccmni", "ccemni", "cc2mni", "pdp", "wwan", "cell").any(interfaz::startsWith)
            val privadaOCgnat = candidata.direccion.isSiteLocalAddress || esCgnat(candidata.direccion)
            return when {
                wifiOEthernet && privadaOCgnat -> 0
                wifiOEthernet -> 1
                !celular && privadaOCgnat -> 2
                !celular -> 3
                !privadaOCgnat -> 4 // Una IPv4 pública celular puede aceptar conexiones entrantes.
                else -> 5 // La IP celular privada/CGNAT solo funciona si el operador enruta entre teléfonos.
            }
        }

        private fun esCgnat(direccion: Inet4Address): Boolean {
            val bytes = direccion.address
            val primero = bytes[0].toInt() and 0xff
            val segundo = bytes[1].toInt() and 0xff
            return primero == 100 && segundo in 64..127 // 100.64.0.0/10
        }
    }
}

/** Dirección IPv4 y la interfaz donde Android la encontró (p. ej. wlan0, ap0 o rmnet_data0). */
internal data class CandidataIPv4(val interfaz: String, val direccion: Inet4Address)
