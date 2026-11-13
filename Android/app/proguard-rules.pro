# ---------------------------------------------------------------------------
# Shalev — reglas R8 (release). Hilt, Compose, Navigation, DataStore y
# kotlinx.serialization traen sus propias consumer rules; aquí solo lo propio.
# ---------------------------------------------------------------------------

# Sin logs en release: R8 elimina las llamadas (y la construcción de sus mensajes).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Contrato GL (JSON firmado): los nombres de campo SON el contrato. Se conservan los
# serializadores generados y los miembros de las clases @Serializable de licencia.
-keep,includedescriptorclasses class cu.shalev.licencia.contract.**$$serializer { *; }
-keepclassmembers class cu.shalev.licencia.contract.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# 0.26.0 (P74): registro de la prueba (JSON {"firstInstall","trialDays","version"}): los nombres son el formato.
-keep,includedescriptorclasses class cu.shalev.licencia.prueba.**$$serializer { *; }
-keepclassmembers class cu.shalev.licencia.prueba.RegistroPrueba {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Rutas type-safe de Navigation (objetos @Serializable).
-keepclassmembers class cu.shalev.app.navigation.Route$* {
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Trazas legibles para soporte sin exponer nombres internos.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
