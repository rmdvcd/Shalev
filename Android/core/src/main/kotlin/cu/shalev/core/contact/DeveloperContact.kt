package cu.shalev.core.contact

/** Datos únicos del desarrollador: una sola fuente para Licencia y Soporte (decisión D4). */
object DeveloperContact {
    const val NOMBRE = "Ing. Ronnie Montero Duarte"
    const val CI = "91040922502"
    const val TELEFONO_LOCAL = "51815604"
    const val TELEFONO_E164 = "+53$TELEFONO_LOCAL"
    const val ESPECIALIDAD = "Desarrollo de sistemas y aplicaciones multiplataforma"

    /** wa.me exige el prefijo de país sin '+'. */
    const val WHATSAPP_URL = "https://wa.me/53$TELEFONO_LOCAL"
    const val SMS_URI = "smsto:$TELEFONO_E164"
}
