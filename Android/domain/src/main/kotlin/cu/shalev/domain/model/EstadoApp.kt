package cu.shalev.domain.model

import java.time.Duration
import java.time.Instant

/**
 * 0.25.0 (Prompt 69): estado propio de la app que NO va en el respaldo: recordatorio de respaldo, comprobación de
 * versiones en GitHub y licencia recuperable (la que traía un respaldo restaurado en un teléfono sin licencia).
 */
data class EstadoApp(
    /** Último respaldo EXPORTADO (importar no cuenta). */
    val ultimoRespaldo: Instant? = null,
    /** Desde cuándo se cuentan los 30 días si nunca se exportó (primera apertura con 0.25.0 o fin del asistente). */
    val cuentaRespaldoDesde: Instant? = null,
    /** Última consulta CORRECTA a GitHub. */
    val ultimaComprobacion: Instant? = null,
    /** Versión más nueva encontrada en la última consulta (null = ninguna más nueva). */
    val disponible: InfoActualizacion? = null,
    /**
     * 0.26.0 (P73 §6): primera vez que se vio una versión más nueva pendiente (GitHub en la principal, la principal en
     * una secundaria). El plazo de [ActualizacionObligatoria.DIAS] cuenta desde aquí y NO se reinicia si sale otra más
     * nueva. null = nada pendiente.
     */
    val obligatoriaDesde: Instant? = null,
    /** Versión instalada cuando se fijó [obligatoriaDesde]: si cambia (se actualizó), el plazo se borra. */
    val obligatoriaPara: String? = null,
    /** «Más tarde» en la tarjeta de Inicio: oculta el aviso hasta esta hora (como mucho un aviso al día). */
    val aplazadaHasta: Instant? = null,
    val licenciaRecuperable: LicenciaRecuperable? = null,
)

/** Datos PÚBLICOS de la licencia que viajan en el respaldo v4 (la licencia en sí está atada a la clave del teléfono). */
data class LicenciaRecuperable(
    val id: String,
    val tipo: String,
    val secundarias: Int,
    /** null = perpetua. */
    val venceEn: Instant?,
    val ci: String,
)

/** Release de GitHub con una versión más nueva que la instalada. */
data class InfoActualizacion(
    val version: String,
    /** Página de la Release (se abre en el navegador). */
    val pagina: String,
    /** Recurso `Shalev-X.Y.Z.apk` (null = la Release no trae APK: solo se avisa). */
    val apkUrl: String? = null,
    /** SHA-256 en hex del APK (del `digest` de la API o del recurso `.sha256`). null = sin huella: no se instala. */
    val sha256: String? = null,
    val bytes: Long = 0,
    val notas: String = "",
)

/** Recordatorio mensual de respaldo (solo en la app principal). */
object RecordatorioRespaldo {
    const val DIAS = 30L

    /** Aviso para Inicio: [dias] desde el último respaldo (null = nunca se hizo). */
    data class Aviso(val dias: Long?)

    fun aviso(ahora: Instant, e: EstadoApp, esPrincipal: Boolean): Aviso? {
        if (!esPrincipal) return null
        val ultimo = e.ultimoRespaldo
        if (ultimo != null) {
            val d = Duration.between(ultimo, ahora).toDays()
            return if (d >= DIAS) Aviso(d) else null
        }
        val desde = e.cuentaRespaldoDesde ?: return null
        return if (Duration.between(desde, ahora).toDays() >= DIAS) Aviso(null) else null
    }
}

/** Comprobación semanal de versiones (sin trabajo en segundo plano: al abrir la app). */
object Actualizaciones {
    const val DIAS = 7L

    /**
     * ¿Toca consultar GitHub? 0.26.0: la consulta semanal es fija (sin interruptor en Ajustes); la lista de revocadas
     * se consulta a la vez si hay una licencia instalada.
     */
    fun debeComprobar(ahora: Instant, e: EstadoApp, repoConfigurado: Boolean): Boolean =
        repoConfigurado &&
            (e.ultimaComprobacion == null || Duration.between(e.ultimaComprobacion, ahora).toDays() >= DIAS ||
                ahora.isBefore(e.ultimaComprobacion))

    /** Versión de GitHub más nueva que [instalada] (0.26.0: ya no se puede descartar; solo aplazar el aviso). */
    fun aviso(e: EstadoApp, instalada: String): InfoActualizacion? =
        e.disponible?.takeIf { esMasNueva(it.version, instalada) }

    /** «v0.25.10» > «0.25.9». Sufijos tras «-» (p. ej. -beta) se ignoran; lo que no se entiende cuenta como 0. */
    fun esMasNueva(candidata: String, instalada: String): Boolean = comparar(candidata, instalada) > 0

    fun comparar(a: String, b: String): Int {
        val x = partes(a); val y = partes(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    private fun partes(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+').split('.')
            .map { p -> p.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
}

/**
 * 0.25.0: datos del build que necesitan :data y :domain (los pone :app desde su BuildConfig).
 * [repoGithub] = «propietario/repositorio» de las Releases públicas; vacío = sin configurar (no se consulta nada).
 */
data class InfoApp(val versionName: String, val versionCode: Int, val repoGithub: String) {
    val repoConfigurado: Boolean get() = REPO.matches(repoGithub)

    companion object {
        private val REPO = Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
    }
}

/** 0.25.0: APK más nuevo disponible en la app principal para esta secundaria (por la red local). */
data class ApkEnPrincipal(val version: String, val versionCode: Int, val bytes: Long)

/**
 * 0.26.0 (P73 §6): las actualizaciones son obligatorias, aplazables [DIAS] días desde que se detectó la primera versión
 * pendiente. Solo cuenta si de verdad hay una versión nueva que se pueda instalar desde la app.
 */
object ActualizacionObligatoria {
    const val DIAS = 30L
    /** «Más tarde» oculta la tarjeta como mucho hasta el día siguiente. */
    val APLAZAR: Duration = Duration.ofDays(1)

    sealed interface Estado {
        /** Nada pendiente (o sin GitHub configurado). */
        data object Ninguna : Estado
        /** Aviso en Inicio: «Obligatoria desde el [limite]». [oculto] = el usuario tocó «Más tarde» hoy. */
        data class Aviso(val limite: Instant, val oculto: Boolean) : Estado
        /** Plazo vencido: pantalla de bloqueo en toda la app (nunca en mitad de una venta). */
        data class Bloqueo(val limite: Instant) : Estado
    }

    fun limite(desde: Instant): Instant = desde.plus(Duration.ofDays(DIAS))

    /**
     * [hayNueva] = existe AHORA una versión más nueva instalable (principal: Release de GitHub con APK y huella;
     * secundaria: APK más nuevo en la principal, o uno ya visto con esta misma versión instalada).
     */
    fun estado(ahora: Instant, e: EstadoApp, instalada: String, hayNueva: Boolean): Estado {
        val desde = e.obligatoriaDesde
        if (!hayNueva || desde == null || e.obligatoriaPara != instalada) return Estado.Ninguna
        val limite = limite(desde)
        return if (!ahora.isBefore(limite)) Estado.Bloqueo(limite)
        else Estado.Aviso(limite, oculto = e.aplazadaHasta?.let { ahora.isBefore(it) } == true)
    }

    /**
     * Estado nuevo tras observar si hay versión pendiente. Fija la fecha la primera vez (o tras actualizar a otra
     * versión) y la borra cuando ya no queda nada pendiente. Una versión aún más nueva NO reinicia el plazo.
     */
    fun registrar(ahora: Instant, e: EstadoApp, instalada: String, hayNueva: Boolean): EstadoApp = when {
        !necesitaRegistro(e, instalada, hayNueva) -> e
        !hayNueva -> e.copy(obligatoriaDesde = null, obligatoriaPara = null, aplazadaHasta = null)
        else -> e.copy(obligatoriaDesde = ahora, obligatoriaPara = instalada, aplazadaHasta = null)
    }

    /** ¿Cambia algo [registrar]? (evita escrituras en cada emisión). */
    fun necesitaRegistro(e: EstadoApp, instalada: String, hayNueva: Boolean): Boolean =
        if (!hayNueva) e.obligatoriaDesde != null || e.obligatoriaPara != null || e.aplazadaHasta != null
        else e.obligatoriaDesde == null || e.obligatoriaPara != instalada
}
