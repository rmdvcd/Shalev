// Raíz: solo declara plugins (versiones en gradle/libs.versions.toml).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.roborazzi) apply false
}

// ---------------------------------------------------------------------------------------------------------------
// Prompt 15 — tests
// ---------------------------------------------------------------------------------------------------------------

/**
 * Tests JVM SIN RED: cualquier conexión a un host externo se envía a un proxy inexistente (127.0.0.1:9) y falla al
 * instante, así un test que dependiera de internet se detecta en vez de pasar "por suerte". Solo se permite el
 * bucle local (MockWebServer de EscanerRedTest), incluido el nombre canónico que MockWebServer usa para "localhost".
 */
val hostLocal: String = runCatching { java.net.InetAddress.getByName("localhost").canonicalHostName }.getOrDefault("localhost")
subprojects {
    tasks.withType<Test>().configureEach {
        val sinProxy = listOf("localhost", "127.*", "[::1]", hostLocal).distinct().joinToString("|")
        listOf("http", "https").forEach { p ->
            systemProperty("$p.proxyHost", "127.0.0.1")
            systemProperty("$p.proxyPort", "9")
            systemProperty("$p.nonProxyHosts", sinProxy)
        }
        systemProperty("user.timezone", "UTC") // mismo huso con el que se verificó la suite: fechas deterministas
        testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
    }
}

/** `./gradlew shalevTests` — todos los tests JVM (sin emulador ni red). */
tasks.register("shalevTests") {
    group = "verification"
    description = "Tests unitarios JVM de todos los módulos (sin dispositivo, sin red)."
    dependsOn(
        ":core:test", ":licencia:test", ":domain:test",
        ":data:testDebugUnitTest", ":designsystem:testDebugUnitTest", ":app:testDebugUnitTest",
    )
}

/** `./gradlew shalevInstrumentedTests` — tests instrumentados (emulador o teléfono conectado; tampoco usan red). */
tasks.register("shalevInstrumentedTests") {
    group = "verification"
    description = "Tests instrumentados: Room en memoria, Keystore, UI Compose y flujos de integración."
    dependsOn(":data:connectedDebugAndroidTest", ":app:connectedDebugAndroidTest")
}

// ---------------------------------------------------------------------------------------------------------------
// P17 — fase E: un solo comando local (y el mismo que usará la CI cuando haya repositorio remoto)
// ---------------------------------------------------------------------------------------------------------------

/**
 * Control del manifiesto FUSIONADO (el de la app más el de todas las librerías): Shalev solo pide los permisos de la lista `permitidos`.
 * Si una dependencia nueva añade otro permiso, falla aquí con el nombre del permiso. Así se puede quitar con
 * `tools:node="remove"`, como ya se hace con ACCESS_NETWORK_STATE de ML Kit.
 */
abstract class VerificarPermisos : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val manifiestos: ConfigurableFileCollection

    @get:Input
    abstract val permitidos: SetProperty<String>

    @TaskAction
    fun verificar() {
        val archivos = manifiestos.asFileTree.matching { include("**/AndroidManifest.xml") }.files
        if (archivos.isEmpty()) throw GradleException("No se encontró el manifiesto fusionado de :app (¿cambió la ruta en AGP?)")
        val patron = Regex("""<uses-permission(?:-sdk-23)?\b[^>]*android:name="([^"]+)"""")
        val pedidos = archivos.flatMap { f -> patron.findAll(f.readText()).map { it.groupValues[1] }.toList() }.toSortedSet()
        // androidx.core (targetSdk ≥ 33) declara un permiso PROPIO de nivel signature,
        // `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, para los receivers dinámicos no exportados.
        // No lo concede el usuario ni da acceso a nada fuera de la app: se admite solo con el prefijo de Shalev y ese sufijo exacto.
        val sobran = pedidos.filterNot { it.startsWith("cu.shalev.app.") && it.endsWith(".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION") }.toSet() - permitidos.get()
        if (sobran.isNotEmpty()) {
            throw GradleException("Permisos no permitidos en el manifiesto fusionado: ${sobran.joinToString()}. Solo se permiten: ${permitidos.get().joinToString()}")
        }
        logger.lifecycle("Permisos del manifiesto fusionado: ${pedidos.joinToString()}")
    }
}

val shalevPermisos = tasks.register<VerificarPermisos>("shalevPermisos") {
    group = "verification"
    description = "Falla si el APK pide algún permiso fuera de la lista autorizada (CAMERA, INTERNET, POST_NOTIFICATIONS y los del servicio de sincronización)."
    // P19: debug y release (release puede fusionar distinto: sin ui-test-manifest y con R8).
    dependsOn(":app:processDebugMainManifest", ":app:processReleaseMainManifest")
    manifiestos.from(
        project(":app").layout.buildDirectory.dir("intermediates/merged_manifest/debug"),
        project(":app").layout.buildDirectory.dir("intermediates/merged_manifest/release"),
    )
    // P29: POST_NOTIFICATIONS autorizado por el usuario, solo para el aviso «Turno abierto» (Android 13+).
    permitidos.set(setOf(
        "android.permission.CAMERA", "android.permission.INTERNET", "android.permission.POST_NOTIFICATIONS",
        // 0.19.2 (P40, autorizado): servicio en primer plano de la principal con secundarias.
        "android.permission.FOREGROUND_SERVICE", "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
        "android.permission.CHANGE_NETWORK_STATE",
        // 0.25.0 (P68b, autorizado): instalar actualizaciones y pedir la desinstalación tras transferir la licencia.
        "android.permission.REQUEST_INSTALL_PACKAGES", "android.permission.REQUEST_DELETE_PACKAGES",
        // 0.26.0 (P74, autorizado): registro de la prueba que sobrevive a reinstalar (READ hasta 12, WRITE hasta 9).
        "android.permission.READ_MEDIA_IMAGES", "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE",
        // 0.27.0 (P77, autorizado): acceso con huella/cara o PIN del teléfono (los añade androidx.biometric; normales).
        "android.permission.USE_BIOMETRIC", "android.permission.USE_FINGERPRINT",
    ))
}

/** `./gradlew shalevCheck` — tests JVM + lint (con baseline) + APK debug + control de permisos. */
tasks.register("shalevCheck") {
    group = "verification"
    description = "Todo lo que debe pasar antes de entregar: shalevTests, lintDebug, assembleDebug y shalevPermisos."
    dependsOn("shalevTests", ":app:lintDebug", ":app:assembleDebug", shalevPermisos)
}

/**
 * P19 — `./gradlew shalevRelease`: comprobaciones (shalevCheck) + AAB y APK de release minificados (R8) y firmados.
 * Salidas: app/build/outputs/bundle/release/app-release.aab y app/build/outputs/apk/release/app-release.apk.
 * Sin firma configurada salen como *-unsigned (ver RELEASE.md).
 */
tasks.register("shalevRelease") {
    group = "build"
    description = "shalevCheck + bundleRelease + assembleRelease."
    dependsOn("shalevCheck", ":app:bundleRelease", ":app:assembleRelease")
}
