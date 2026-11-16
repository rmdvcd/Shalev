package cu.shalev.data.sync

/**
 * Validación local de las direcciones que pueden viajar en el QR. El QR es entrada no confiable: solo admite IPv4
 * numéricas, para no provocar consultas DNS ni conectar a nombres arbitrarios. Las direcciones privadas y CGNAT se
 * admiten porque pueden funcionar dentro de una red del operador; eso no implica que sean alcanzables desde Internet.
 */
internal object DireccionIPv4Sync {
    const val MAX_DIRECCIONES = 32

    fun esCandidata(host: String): Boolean {
        if (host.length !in 7..15) return false
        val octetos = host.split('.')
        if (octetos.size != 4) return false
        val valores = IntArray(4)
        for (i in octetos.indices) {
            val octeto = octetos[i]
            if (octeto.isEmpty() || octeto.length > 3 || octeto.any { it !in '0'..'9' }) return false
            if (octeto.length > 1 && octeto[0] == '0') return false
            valores[i] = octeto.toIntOrNull()?.takeIf { it in 0..255 } ?: return false
        }
        val primero = valores[0]
        val segundo = valores[1]
        // 0/8, loopback, link-local, multicast y reservado (incluye broadcast) no son destinos del vínculo.
        if (primero == 0 || primero == 127 || (primero == 169 && segundo == 254) || primero >= 224) return false
        return true
    }

    fun listaValida(hosts: List<String>, permitirVacia: Boolean = false): Boolean =
        hosts.size <= MAX_DIRECCIONES &&
            (permitirVacia || hosts.isNotEmpty()) &&
            hosts.distinct().size == hosts.size &&
            hosts.all(::esCandidata)
}
