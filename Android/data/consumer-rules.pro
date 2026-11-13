# SQLCipher: clases nativas accedidas por JNI.
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
# fastexcel/opczip: sin reflexión, pero evitar avisos por clases opcionales de java.awt.
-dontwarn java.awt.**
-dontwarn org.dhatim.fastexcel.**
# kotlinx.serialization: los serializers generados se resuelven por el plugin; conservar companions de DTOs.
-keepclassmembers @kotlinx.serialization.Serializable class cu.shalev.data.dto.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
