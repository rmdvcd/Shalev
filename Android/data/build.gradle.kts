import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "cu.shalev.data"
    compileSdk = 35
    buildFeatures { buildConfig = true }

    defaultConfig {
        minSdk = 26
        // Una URL pública, no una credencial. Vacía mantiene exclusivamente la sincronización local.
        val relay = providers.gradleProperty("shalevRelayUrl").orElse("").get()
        require(relay.isEmpty() || relay.matches(Regex("https://[A-Za-z0-9.-]+/?"))) {
            "shalevRelayUrl debe ser una URL HTTPS sin ruta, credenciales ni parámetros"
        }
        buildConfigField("String", "SHALEV_RELAY_URL", "\"$relay\"")
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        // Esquemas exportados disponibles para MigrationTestHelper en tests instrumentados.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        animationsDisabled = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

dependencies {
    api(project(":domain"))

    // Antes en :security (P17 S1): Keystore (AEAD, clave del dispositivo, frase de la BD).
    implementation(libs.androidx.annotation)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher.android)

    implementation(libs.fastexcel)
    // Escáner: única red de la app (bases públicas de productos) y fotos re-codificadas.
    implementation(libs.okhttp)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.room.testing) // P17 A3: EsquemaTest (MigrationTestHelper con el esquema v3)
}
