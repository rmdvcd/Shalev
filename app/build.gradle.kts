import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.roborazzi)   // P23: solo añade tareas record/compare/verify; no toca el APK
}

/**
 * Firma de release (P19). Los secretos NUNCA van al repositorio. Se leen, por orden:
 *  1. `keystore.properties` en la raíz del proyecto (ignorado por git):
 *       storeFile=/ruta/absoluta/spvi-release.jks
 *       storePassword=…
 *       keyAlias=shalev
 *       keyPassword=…
 *  2. Variables de entorno: Shalev_KEYSTORE, Shalev_KEYSTORE_PASSWORD, Shalev_KEY_ALIAS, Shalev_KEY_PASSWORD.
 * Si falta alguno, `assembleRelease`/`bundleRelease` producen artefactos SIN FIRMAR (no instalables) y se avisa.
 * Ver RELEASE.md.
 */
val propsFirma = Properties().apply {
    // providers.fileContents: compatible con la configuration cache (el archivo queda registrado como entrada).
    providers.fileContents(rootProject.layout.projectDirectory.file("keystore.properties")).asText.orNull
        ?.let { load(it.reader()) }
}
fun datoFirma(clave: String, variable: String): String? =
    propsFirma.getProperty(clave)?.takeIf { it.isNotBlank() } ?: providers.environmentVariable(variable).orNull?.takeIf { it.isNotBlank() }
val firmaStoreFile = datoFirma("storeFile", "Shalev_KEYSTORE")
val firmaStorePassword = datoFirma("storePassword", "Shalev_KEYSTORE_PASSWORD")
val firmaKeyAlias = datoFirma("keyAlias", "Shalev_KEY_ALIAS")
val firmaKeyPassword = datoFirma("keyPassword", "Shalev_KEY_PASSWORD")
val firmaCompleta = listOf(firmaStoreFile, firmaStorePassword, firmaKeyAlias, firmaKeyPassword).all { it != null }

/** P23: SDK android-all-instrumented de Robolectric (SDK 35), resuelto por Gradle para tests y capturas sin red. */
val robolectricSdk: Configuration by configurations.creating { isTransitive = false }

android {
    namespace = "cu.shalev.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "cu.shalev.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 54
        versionName = "1.0.1"
        // Repositorio PÚBLICO de GitHub cuyas Releases sirven APK y revocaciones (por defecto rmdvcd/Shalev).
        // Se puede sobrescribir con -PshalevGithubRepo=owner/repo; vacío desactiva estas consultas.
        buildConfigField("String", "GITHUB_REPO", "\"${(project.findProperty("shalevGithubRepo") as String?).orEmpty()}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (firmaCompleta) {
            create("release") {
                storeFile = file(firmaStoreFile!!)
                storePassword = firmaStorePassword
                keyAlias = firmaKeyAlias
                keyPassword = firmaKeyPassword
                // Esquemas de firma: los que AGP elige por defecto para minSdk 26 (v1/JAR no hace falta desde API 24).
            }
        } else {
            logger.warn("SPVI: firma de release no configurada (keystore.properties o Shalev_*). El release saldrá SIN FIRMAR.")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (firmaCompleta) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    // Auditoría 2026-10-14 (O-05): un APK por ABI (+ universal) para no cargar las librerías nativas de todas
    // las arquitecturas (ML Kit/SQLCipher) en cada teléfono. El APK universal sigue existiendo para distribuir
    // un solo archivo, como hasta ahora.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        // P17 S6: fakes en memoria compartidos por los tests JVM y los instrumentados (antes duplicados).
        getByName("test").java.srcDir("src/sharedTest/kotlin")
        getByName("androidTest").java.srcDir("src/sharedTest/kotlin")
    }

    testOptions {
        animationsDisabled = true                 // tests de Compose estables (sin esperar animaciones)
        unitTests.isReturnDefaultValues = true    // android.* devuelve valores por defecto en los tests JVM
        unitTests.isIncludeAndroidResources = true // P23: Robolectric necesita los recursos (logo, iconos) para las capturas
    }

    lint {
        // P17 U3: los errores nuevos rompen `shalevCheck`. Lo heredado queda en el baseline; para regenerarlo:
        // ./gradlew :app:updateLintBaseline (revisar el diff antes de aceptarlo).
        baseline = file("lint-baseline.xml")
        abortOnError = true
        checkDependencies = true
        warningsAsErrors = false
        // El XML siempre se escribe: el CI lo publica en el resumen del fallo (tools/verificacion/resumen_fallos.sh).
        xmlReport = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/versions/9/previous-compilation-data.bin")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":designsystem"))
    implementation(project(":domain"))
    implementation(project(":data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.biometric)          // 0.27.0 (T11): añade USE_BIOMETRIC y USE_FINGERPRINT (permisos normales)
    implementation(libs.androidx.fragment)           // 0.27.0 (T11): MainActivity es FragmentActivity (BiometricPrompt)
    implementation(libs.androidx.lifecycle.process)  // 0.27.0 (T11): la app pasa a segundo plano y vuelve
    // Auditoría 2026-10-14 (D-04): fuera androidx-profileinstaller: sin baseline profile generado no aportaba
    // nada y sumaba un permiso/dependencia. Si se genera el perfil (Macrobenchmark), se repone junto con él.
    implementation(libs.kotlinx.serialization.core)

    // Vinculación: lector QR con CameraX + ML Kit (modelo empaquetado, sin Play Services). Inventario: fotos (Coil, solo archivos locales).
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode)
    implementation(libs.mlkit.text)          // OCR offline gratuito e ilimitado (modelo latino empaquetado, sin red)
    implementation(libs.coil.compose)
    implementation(libs.zxing.core)          // QR de cobro (solo generación, sin cámara ni red)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Tests de Compose sin Hilt: se prueban los *Content sin estado.
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // Prompt 15 — integración: Room en memoria con los repositorios reales de :data.
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.room.runtime)
    debugImplementation(libs.compose.ui.test.manifest)

    // P23 — capturas de pantalla reales (Robolectric + Roborazzi) de los *Content, en claro y oscuro.
    // Solo testImplementation: no entran en el APK. Ver CAPTURAS.md.
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    robolectricSdk(libs.robolectric.android.all)
}

// ---------------------------------------------------------------------------------------------------------------
// P23 — Capturas (cu.shalev.app.capturas) y SDK offline para los tests JVM de :app.
//   ./gradlew :app:recordRoborazziDebug   → app/capturas/{claro,oscuro}/*.png
// Las capturas se excluyen de `test`, `shalevTests` y `shalevCheck` (solo corren con tareas Roborazzi).
// Gradle resuelve android-all-instrumented con `robolectricSdk`; todos los tests de :app reciben el directorio
// local y `robolectric.offline=true`, así el proceso de tests no necesita red (incluye ProcesoActualizarTest).
// ---------------------------------------------------------------------------------------------------------------
val robolectricSdkDir = layout.buildDirectory.dir("robolectric-sdk")
val prepararRobolectricSdk = tasks.register<Copy>("prepararRobolectricSdk") {
    from(robolectricSdk)
    into(robolectricSdkDir)
}
val conCapturas = gradle.startParameter.taskNames.any { it.contains("Roborazzi", ignoreCase = true) }
tasks.withType<Test>().configureEach {
    // Robolectric descarga android-all-instrumented al arrancar cada test si no le damos una ruta local.
    // Los tests JVM tienen el proxy externo bloqueado desde build.gradle.kts; preparar el SDK para todos
    // evita fallos de red en tests como ProcesoActualizarTest, no solo al regenerar capturas.
    dependsOn(prepararRobolectricSdk)
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", robolectricSdkDir.get().asFile.absolutePath)

    if (conCapturas) {
        maxHeapSize = "2g"
    } else {
        exclude("**/capturas/**")
    }
}
