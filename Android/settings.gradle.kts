pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Shalev"

// Capas (dependencias solo hacia abajo):
//   app → designsystem, data, domain
//   data → domain, licencia  (incluye el antiguo :security: Keystore y clave del dispositivo)
//   domain → licencia → core        (JVM puro, testeable sin Android)
include(":app")
include(":designsystem")
include(":data")
include(":domain")
include(":licencia")
include(":core")
