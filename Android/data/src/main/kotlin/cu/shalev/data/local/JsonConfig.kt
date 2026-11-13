package cu.shalev.data.local

import kotlinx.serialization.json.Json

/** Json tolerante a campos nuevos/desconocidos (compatibilidad hacia delante de respaldos y preferencias). */
internal val ShalevJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}
