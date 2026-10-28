package cu.shalev.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Iconografía de Shalev: SOLO iconos sólidos (Filled). El estado seleccionado/activo se expresa con
 * resaltado invertido (fondo primary + icono onPrimary), lo aplica cada componente, no el icono.
 * Toda la app referencia iconos por su significado (ShalevIcons.Venta), nunca el trazado directamente.
 */
object ShalevIcons {
    // Barra inferior
    val Inicio: ImageVector get() = MaterialSolido.Home
    val Inventario: ImageVector get() = MaterialSolido.Inventory2          // cajas en 3D
    /** P29/P30: Servicios = mano que recibe, sin corazón. */
    val Servicios: ImageVector get() = MaterialSolido.ManoRecibe
    val Registros: ImageVector get() = MaterialSolido.History              // reloj con flecha antihoraria
    val Ajustes: ImageVector get() = MaterialSolido.Settings

    // Dominio
    val Venta: ImageVector get() = MaterialSolido.ShoppingCart
    val Efectivo: ImageVector get() = MaterialSolido.Payments
    val Transferencia: ImageVector get() = MaterialSolido.SwapHoriz         // dos flechas opuestas
    val Perfil: ImageVector get() = MaterialSolido.Person
    val Precios: ImageVector get() = MaterialSolido.AttachMoney            // P24: «$»
    val PagoElectronico: ImageVector get() = MaterialSolido.CreditCard
    val Turno: ImageVector get() = MaterialSolido.Storefront
    val Telefono: ImageVector get() = MaterialSolido.Phone
    val Porcentaje: ImageVector get() = MaterialSolido.Percent
    val Escanear: ImageVector get() = MaterialSolido.QrCodeScanner
    val Licencia: ImageVector get() = MaterialSolido.Key
    val Soporte: ImageVector get() = MaterialSolido.SupportAgent
    val Ayuda: ImageVector get() = MaterialSolido.Help
    val Respaldo: ImageVector get() = MaterialSolido.Backup
    val Bloqueo: ImageVector get() = MaterialSolido.Lock

    // Acciones (sustituyen a botones con texto)
    val Confirmar: ImageVector get() = MaterialSolido.Check
    val Cancelar: ImageVector get() = MaterialSolido.Close
    val Editar: ImageVector get() = MaterialSolido.Edit
    val Agregar: ImageVector get() = MaterialSolido.Add
    val Quitar: ImageVector get() = MaterialSolido.Remove
    /** Botones ◄ y ► de todos los selectores numéricos (spinners) y pasos. */
    val Disminuir: ImageVector get() = MaterialSolido.ArrowLeft
    val Aumentar: ImageVector get() = MaterialSolido.ArrowRight
    val Ocr: ImageVector get() = MaterialSolido.DocumentScanner
    val Camara: ImageVector get() = MaterialSolido.PhotoCamera
    val Configurar: ImageVector get() = MaterialSolido.Checklist
    val Eliminar: ImageVector get() = MaterialSolido.Delete
    val Compartir: ImageVector get() = MaterialSolido.Share
    val Importar: ImageVector get() = MaterialSolido.FileDownload
    val Exportar: ImageVector get() = MaterialSolido.FileUpload
    val Sms: ImageVector get() = MaterialSolido.Sms
    val WhatsApp: ImageVector by lazy { buildWhatsApp() }
    val Buscar: ImageVector get() = MaterialSolido.Search
    val Filtrar: ImageVector get() = MaterialSolido.FilterList
    val Pegar: ImageVector get() = MaterialSolido.ContentPaste
    val Limpiar: ImageVector get() = MaterialSolido.Close
    val Atras: ImageVector get() = MaterialSolido.ArrowBack
    val Abrir: ImageVector get() = MaterialSolido.KeyboardArrowRight

    // P24: botones solo icono (antes con texto). Un significado = un icono en toda la app (Jakob).
    val Guardar: ImageVector get() = Confirmar // P25 (confirmado en P33): guardar = confirmar (✓)
    val Reintentar: ImageVector get() = MaterialSolido.Refresh
    val Listo: ImageVector get() = MaterialSolido.Done
    val Aplicar: ImageVector get() = MaterialSolido.Done
    val QuitarFiltros: ImageVector get() = MaterialSolido.FilterAltOff
    val Siguiente: ImageVector get() = MaterialSolido.ArrowForward
    val AhoraNo: ImageVector get() = MaterialSolido.Schedule
    /** Saltar un paso: triángulo con barra, no un reloj («Ahora no»). */
    val Saltar: ImageVector get() = MaterialSolido.SkipNext
    val Fecha: ImageVector get() = MaterialSolido.CalendarMonth
    val Desplegar: ImageVector get() = MaterialSolido.ArrowDropDown // P25: combobox
    val AgregarALista: ImageVector get() = MaterialSolido.PlaylistAdd
    val Restablecer: ImageVector get() = MaterialSolido.Replay
    val Lista: ImageVector get() = MaterialSolido.ListAlt
    val Enviar: ImageVector get() = MaterialSolido.Send
    val Comprobante: ImageVector get() = MaterialSolido.ReceiptLong
    val AgregarCarrito: ImageVector get() = MaterialSolido.AddShoppingCart
    val Pdf: ImageVector get() = MaterialSolido.PictureAsPdf
    val Excel: ImageVector get() = MaterialSolido.Excel
    val Imagen: ImageVector get() = MaterialSolido.PhotoLibrary
    val Promocion: ImageVector get() = MaterialSolido.CreditCard
    val Salir: ImageVector get() = MaterialSolido.Logout
    val ElegirArchivo: ImageVector get() = MaterialSolido.UploadFile
    val Movil: ImageVector get() = MaterialSolido.PhoneAndroid

    // 0.27.0 (T12/T13/T11/N2)
    val Desarrollador: ImageVector get() = MaterialSolido.Person
    val Identidad: ImageVector get() = MaterialSolido.Badge
    val Especialidad: ImageVector get() = MaterialSolido.Work
    val Galeria: ImageVector get() = MaterialSolido.PhotoLibrary
    val Huella: ImageVector get() = MaterialSolido.Fingerprint

    // P37: tipo de app (Principal/Secundaria), vinculación y sincronización
    val Vinculacion: ImageVector get() = MaterialSolido.Devices
    val Sincronizar: ImageVector get() = MaterialSolido.Sync
    val RedLocal: ImageVector get() = MaterialSolido.Wifi
    val AgregarEmpleado: ImageVector get() = MaterialSolido.PersonAdd
    val Desvincular: ImageVector get() = MaterialSolido.LinkOff

    // Estados
    val Prueba: ImageVector get() = MaterialSolido.HourglassTop
    val Verificado: ImageVector get() = MaterialSolido.Verified
    val Vencido: ImageVector get() = MaterialSolido.EventBusy
    val Revocado: ImageVector get() = MaterialSolido.Block
    val Reloj: ImageVector get() = MaterialSolido.AccessTime
    val Alerta: ImageVector get() = MaterialSolido.WarningAmber

    /**
     * Glifo de WhatsApp (24×24). Los flags de arco van con separadores explícitos (`0 0 1`):
     * la forma compacta `01-5.031` no la interpretan bien todos los parsers. Validado renderizando el path.
     */
    private fun buildWhatsApp(): ImageVector = ImageVector.Builder(
        name = "WhatsApp", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(pathData = addPathNodes(WHATSAPP_PATH), fill = SolidColor(Color.Black)).build()

    private const val WHATSAPP_PATH =
        "M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164" +
            "-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297" +
            "-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52" +
            "-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074" +
            "-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487" +
            ".709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248" +
            "-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 0 1 -5.031 -1.378" +
            "l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 0 1 -1.51 -5.26c.001-5.45 4.436-9.884 9.888-9.884" +
            " 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 0 1 2.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884" +
            "m8.413-18.297A11.815 11.815 0 0 0 12.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945" +
            "L.057 24l6.305-1.654a11.882 11.882 0 0 0 5.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893" +
            "a11.821 11.821 0 0 0 -3.48 -8.413Z"
}

/**
 * Trazados de Material Icons «Filled» (Apache 2.0, © Google) copiados como datos: sustituyen a
 * `material-icons-extended` (miles de iconos) por los que usa Shalev. Cada vector se construye una sola vez.
 */
internal object MaterialSolido {
    val Home: ImageVector by lazy { icono("Home", "M 10 20 v -6 h 4 v 6 h 5 v -8 h 3 L 12 3 2 12 h 3 v 8 z") }
    val Inventory2: ImageVector by lazy { icono("Inventory2", "M 20 2 H 4 c -1 0 -2 .9 -2 2 v 3.01 c 0 .72 .43 1.34 1 1.69 V 20 c 0 1.1 1.1 2 2 2 h 14 c .9 0 2 -.9 2 -2 V 8.7 c .57 -.35 1 -.97 1 -1.69 V 4 c 0 -1.1 -1 -2 -2 -2 z m -5 12 H 9 v -2 h 6 v 2 z m 5 -7 H 4 V 4 h 16 v 3 z") }
    /** P30: solo la mano que recibe (trazado de «volunteer_activism» sin el corazón), subida 4.5 para centrarla. */
    val ManoRecibe: ImageVector by lazy { icono("ManoRecibe", "M 1 6.5 h 4 v 11 H 1 z M 20 12.5 h -7 l -2.09 -.73 .33 -.94 L 13 11.5 h 2.82 c .65 0 1.18 -.53 1.18 -1.18 0 -.49 -.31 -.93 -.77 -1.11 L 8.97 6.5 H 7 v 9.02 L 14 17.5 l 8.01 -3 c -.01 -1.1 -.9 -2 -2.01 -2 z") }
    val History: ImageVector by lazy { icono("History", "M 13 3 a 9 9 0 0 0 -9 9 H 1 l 3.89 3.89 .07 .14 L 9 12 H 6 c 0 -3.87 3.13 -7 7 -7 s 7 3.13 7 7 -3.13 7 -7 7 c -1.93 0 -3.68 -.79 -4.94 -2.06 l -1.42 1.42 A 8.954 8.954 0 0 0 13 21 a 9 9 0 0 0 0 -18 z m -1 5 v 5 l 4.28 2.54 .72 -1.21 -3.5 -2.08 V 8 H 12 z") }
    val Settings: ImageVector by lazy { icono("Settings", "M 19.14 12.94 c .04 -.3 .06 -.61 .06 -.94 0 -.32 -.02 -.64 -.07 -.94 l 2.03 -1.58 a .49 .49 0 0 0 .12 -.61 l -1.92 -3.32 a .488 .488 0 0 0 -.59 -.22 l -2.39 .96 c -.5 -.38 -1.03 -.7 -1.62 -.94 l -.36 -2.54 a .484 .484 0 0 0 -.48 -.41 h -3.84 c -.24 0 -.43 .17 -.47 .41 l -.36 2.54 c -.59 .24 -1.13 .57 -1.62 .94 l -2.39 -.96 c -.22 -.08 -.47 0 -.59 .22 L 2.74 8.87 c -.12 .21 -.08 .47 .12 .61 l 2.03 1.58 c -.05 .3 -.09 .63 -.09 .94 s .02 .64 .07 .94 l -2.03 1.58 a .49 .49 0 0 0 -.12 .61 l 1.92 3.32 c .12 .22 .37 .29 .59 .22 l 2.39 -.96 c .5 .38 1.03 .7 1.62 .94 l .36 2.54 c .05 .24 .24 .41 .48 .41 h 3.84 c .24 0 .44 -.17 .47 -.41 l .36 -2.54 c .59 -.24 1.13 -.56 1.62 -.94 l 2.39 .96 c .22 .08 .47 0 .59 -.22 l 1.92 -3.32 c .12 -.22 .07 -.47 -.12 -.61 l -2.01 -1.58 z M 12 15.6 c -1.98 0 -3.6 -1.62 -3.6 -3.6 s 1.62 -3.6 3.6 -3.6 3.6 1.62 3.6 3.6 -1.62 3.6 -3.6 3.6 z") }
    val ShoppingCart: ImageVector by lazy { icono("ShoppingCart", "M 7 18 c -1.1 0 -1.99 .9 -1.99 2 S 5.9 22 7 22 s 2 -.9 2 -2 -.9 -2 -2 -2 z M 1 2 v 2 h 2 l 3.6 7.59 -1.35 2.45 c -.16 .28 -.25 .61 -.25 .96 0 1.1 .9 2 2 2 h 12 v -2 H 7.42 c -.14 0 -.25 -.11 -.25 -.25 l .03 -.12 .9 -1.63 h 7.45 c .75 0 1.41 -.41 1.75 -1.03 l 3.58 -6.49 A 1.003 1.003 0 0 0 20 4 H 5.21 l -.94 -2 H 1 z m 16 16 c -1.1 0 -1.99 .9 -1.99 2 s .89 2 1.99 2 2 -.9 2 -2 -.9 -2 -2 -2 z") }
    val Payments: ImageVector by lazy { icono("Payments", "M 19 14 V 6 c 0 -1.1 -.9 -2 -2 -2 H 3 c -1.1 0 -2 .9 -2 2 v 8 c 0 1.1 .9 2 2 2 h 14 c 1.1 0 2 -.9 2 -2 z m -9 -1 c -1.66 0 -3 -1.34 -3 -3 s 1.34 -3 3 -3 3 1.34 3 3 -1.34 3 -3 3 z m 13 -6 v 11 c 0 1.1 -.9 2 -2 2 H 4 v -2 h 17 V 7 h 2 z") }
    val SwapHoriz: ImageVector by lazy { icono("SwapHoriz", "M 6.99 11 3 15 l 3.99 4 v -3 H 14 v -2 H 6.99 v -3 z M 21 9 l -3.99 -4 v 3 H 10 v 2 h 7.01 v 3 L 21 9 z") }
    val Person: ImageVector by lazy { icono("Person", "M 12 12 c 2.21 0 4 -1.79 4 -4 s -1.79 -4 -4 -4 -4 1.79 -4 4 1.79 4 4 4 z m 0 2 c -2.67 0 -8 1.34 -8 4 v 2 h 16 v -2 c 0 -2.66 -5.33 -4 -8 -4 z") }
    val CreditCard: ImageVector by lazy { icono("CreditCard", "M 20 4 H 4 c -1.11 0 -1.99 .89 -1.99 2 L 2 18 c 0 1.11 .89 2 2 2 h 16 c 1.11 0 2 -.89 2 -2 V 6 c 0 -1.11 -.89 -2 -2 -2 z m 0 14 H 4 v -6 h 16 v 6 z m 0 -10 H 4 V 6 h 16 v 2 z") }
    val Storefront: ImageVector by lazy { icono("Storefront", "m 21.9 8.89 -1.05 -4.37 c -.22 -.9 -1 -1.52 -1.91 -1.52 H 5.05 c -.9 0 -1.69 .63 -1.9 1.52 L 2.1 8.89 c -.24 1.02 -.02 2.06 .62 2.88 .08 .11 .19 .19 .28 .29 V 19 c 0 1.1 .9 2 2 2 h 14 c 1.1 0 2 -.9 2 -2 v -6.94 c .09 -.09 .2 -.18 .28 -.28 .64 -.82 .87 -1.87 .62 -2.89 z m -2.99 -3.9 1.05 4.37 c .1 .42 .01 .84 -.25 1.17 -.14 .18 -.44 .47 -.94 .47 -.61 0 -1.14 -.49 -1.21 -1.14 L 16.98 5 l 1.93 -.01 z M 13 5 h 1.96 l .54 4.52 c .05 .39 -.07 .78 -.33 1.07 -.22 .26 -.54 .41 -.95 .41 -.67 0 -1.22 -.59 -1.22 -1.31 V 5 z M 8.49 9.52 9.04 5 H 11 v 4.69 c 0 .72 -.55 1.31 -1.29 1.31 -.34 0 -.65 -.15 -.89 -.41 a 1.42 1.42 0 0 1 -.33 -1.07 z m -4.45 -.16 L 5.05 5 h 1.97 l -.58 4.86 c -.08 .65 -.6 1.14 -1.21 1.14 -.49 0 -.8 -.29 -.93 -.47 -.27 -.32 -.36 -.75 -.26 -1.17 z M 5 19 v -6.03 c .08 .01 .15 .03 .23 .03 .87 0 1.66 -.36 2.24 -.95 .6 .6 1.4 .95 2.31 .95 .87 0 1.65 -.36 2.23 -.93 .59 .57 1.39 .93 2.29 .93 .84 0 1.64 -.35 2.24 -.95 .58 .59 1.37 .95 2.24 .95 .08 0 .15 -.02 .23 -.03 V 19 H 5 z") }
    val Phone: ImageVector by lazy { icono("Phone", "M 6.62 10.79 c 1.44 2.83 3.76 5.14 6.59 6.59 l 2.2 -2.2 c .27 -.27 .67 -.36 1.02 -.24 1.12 .37 2.33 .57 3.57 .57 .55 0 1 .45 1 1 V 20 c 0 .55 -.45 1 -1 1 -9.39 0 -17 -7.61 -17 -17 0 -.55 .45 -1 1 -1 h 3.5 c .55 0 1 .45 1 1 0 1.25 .2 2.45 .57 3.57 .11 .35 .03 .74 -.25 1.02 l -2.2 2.2 z") }
    val Percent: ImageVector by lazy { icono("Percent", "M 7.5 11 C 9.43 11 11 9.43 11 7.5 S 9.43 4 7.5 4 4 5.57 4 7.5 5.57 11 7.5 11 z m 0 -5 C 8.33 6 9 6.67 9 7.5 S 8.33 9 7.5 9 6 8.33 6 7.5 6.67 6 7.5 6 z M 4.002 18.583 18.59 3.996 l 1.414 1.414 L 5.417 19.997 z M 16.5 13 c -1.93 0 -3.5 1.57 -3.5 3.5 s 1.57 3.5 3.5 3.5 3.5 -1.57 3.5 -3.5 -1.57 -3.5 -3.5 -3.5 z m 0 5 c -.83 0 -1.5 -.67 -1.5 -1.5 s .67 -1.5 1.5 -1.5 1.5 .67 1.5 1.5 -.67 1.5 -1.5 1.5 z") }
    val QrCodeScanner: ImageVector by lazy { icono("QrCodeScanner", "M 9.5 6.5 v 3 h -3 v -3 h 3 M 11 5 H 5 v 6 h 6 V 5 z m -1.5 9.5 v 3 h -3 v -3 h 3 M 11 13 H 5 v 6 h 6 v -6 z m 6.5 -6.5 v 3 h -3 v -3 h 3 M 19 5 h -6 v 6 h 6 V 5 z m -6 8 h 1.5 v 1.5 H 13 V 13 z m 1.5 1.5 H 16 V 16 h -1.5 v -1.5 z M 16 13 h 1.5 v 1.5 H 16 V 13 z m -3 3 h 1.5 v 1.5 H 13 V 16 z m 1.5 1.5 H 16 V 19 h -1.5 v -1.5 z M 16 16 h 1.5 v 1.5 H 16 V 16 z m 1.5 -1.5 H 19 V 16 h -1.5 v -1.5 z m 0 3 H 19 V 19 h -1.5 v -1.5 z M 22 7 h -2 V 4 h -3 V 2 h 5 v 5 z m 0 15 v -5 h -2 v 3 h -3 v 2 h 5 z M 2 22 h 5 v -2 H 4 v -3 H 2 v 5 z M 2 2 v 5 h 2 V 4 h 3 V 2 H 2 z") }
    val Key: ImageVector by lazy { icono("Key", "M 21 10 h -8.35 A 5.99 5.99 0 0 0 7 6 c -3.31 0 -6 2.69 -6 6 s 2.69 6 6 6 a 5.99 5.99 0 0 0 5.65 -4 H 13 l 2 2 2 -2 2 2 4 -4.04 L 21 10 z M 7 15 c -1.65 0 -3 -1.35 -3 -3 s 1.35 -3 3 -3 3 1.35 3 3 -1.35 3 -3 3 z") }
    val SupportAgent: ImageVector by lazy { icono("SupportAgent", "M 21 12.22 C 21 6.73 16.74 3 12 3 c -4.69 0 -9 3.65 -9 9.28 -.6 .34 -1 .98 -1 1.72 v 2 c 0 1.1 .9 2 2 2 h 1 v -6.1 c 0 -3.87 3.13 -7 7 -7 s 7 3.13 7 7 V 19 h -8 v 2 h 8 c 1.1 0 2 -.9 2 -2 v -1.22 c .59 -.31 1 -.92 1 -1.64 v -2.3 c 0 -.7 -.41 -1.31 -1 -1.62 z", "M 8 13 A 1 1 0 1 0 10 13 A 1 1 0 1 0 8 13 Z", "M 14 13 A 1 1 0 1 0 16 13 A 1 1 0 1 0 14 13 Z", "M 18 11.03 A 6.04 6.04 0 0 0 12.05 6 c -3.03 0 -6.29 2.51 -6.03 6.45 a 8.075 8.075 0 0 0 4.86 -5.89 c 1.31 2.63 4 4.44 7.12 4.47 z") }
    val Help: ImageVector by lazy { icono("Help", "M 12 2 C 6.48 2 2 6.48 2 12 s 4.48 10 10 10 10 -4.48 10 -10 S 17.52 2 12 2 z m 1 17 h -2 v -2 h 2 v 2 z m 2.07 -7.75 -.9 .92 C 13.45 12.9 13 13.5 13 15 h -2 v -.5 c 0 -1.1 .45 -2.1 1.17 -2.83 l 1.24 -1.26 c .37 -.36 .59 -.86 .59 -1.41 0 -1.1 -.9 -2 -2 -2 s -2 .9 -2 2 H 8 c 0 -2.21 1.79 -4 4 -4 s 4 1.79 4 4 c 0 .88 -.36 1.68 -.93 2.25 z", espejo = true) }
    val Backup: ImageVector by lazy { icono("Backup", "M 19.35 10.04 A 7.49 7.49 0 0 0 12 4 C 9.11 4 6.6 5.64 5.35 8.04 A 5.994 5.994 0 0 0 0 14 c 0 3.31 2.69 6 6 6 h 13 c 2.76 0 5 -2.24 5 -5 0 -2.64 -2.05 -4.78 -4.65 -4.96 z M 14 13 v 4 h -4 v -4 H 7 l 5 -5 5 5 h -3 z") }
    val Lock: ImageVector by lazy { icono("Lock", "M 18 8 h -1 V 6 c 0 -2.76 -2.24 -5 -5 -5 S 7 3.24 7 6 v 2 H 6 c -1.1 0 -2 .9 -2 2 v 10 c 0 1.1 .9 2 2 2 h 12 c 1.1 0 2 -.9 2 -2 V 10 c 0 -1.1 -.9 -2 -2 -2 z m -6 9 c -1.1 0 -2 -.9 -2 -2 s .9 -2 2 -2 2 .9 2 2 -.9 2 -2 2 z m 3.1 -9 H 8.9 V 6 c 0 -1.71 1.39 -3.1 3.1 -3.1 1.71 0 3.1 1.39 3.1 3.1 v 2 z") }
    val ArrowDropDown: ImageVector by lazy { icono("ArrowDropDown", "M 7 10 l 5 5 5 -5 z") }
    val Check: ImageVector by lazy { icono("Check", "M 9 16.17 4.83 12 l -1.42 1.41 L 9 19 21 7 l -1.41 -1.41 z") }
    val Close: ImageVector by lazy { icono("Close", "M 19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12 z") }
    val Edit: ImageVector by lazy { icono("Edit", "M 3 17.25 V 21 h 3.75 L 17.81 9.94 l -3.75 -3.75 L 3 17.25 z M 20.71 7.04 a .996 .996 0 0 0 0 -1.41 l -2.34 -2.34 a .996 .996 0 0 0 -1.41 0 l -1.83 1.83 3.75 3.75 1.83 -1.83 z") }
    val Add: ImageVector by lazy { icono("Add", "M 19 13 h -6 v 6 h -2 v -6 H 5 v -2 h 6 V 5 h 2 v 6 h 6 v 2 z") }
    val Remove: ImageVector by lazy { icono("Remove", "M 19 13 H 5 v -2 h 14 v 2 z") }
    val PhotoCamera: ImageVector by lazy { icono("PhotoCamera", "M 8.8 12 A 3.2 3.2 0 1 0 15.2 12 A 3.2 3.2 0 1 0 8.8 12 Z", "M 9 2 7.17 4 H 4 c -1.1 0 -2 .9 -2 2 v 12 c 0 1.1 .9 2 2 2 h 16 c 1.1 0 2 -.9 2 -2 V 6 c 0 -1.1 -.9 -2 -2 -2 h -3.17 L 15 2 H 9 z m 3 15 c -2.76 0 -5 -2.24 -5 -5 s 2.24 -5 5 -5 5 2.24 5 5 -2.24 5 -5 5 z") }
    val Checklist: ImageVector by lazy { icono("Checklist", "M 22 7 h -9 v 2 h 9 V 7 z m 0 8 h -9 v 2 h 9 v -2 z M 5.54 11 2 7.46 l 1.41 -1.41 2.12 2.12 4.24 -4.24 1.41 1.41 L 5.54 11 z m 0 8 L 2 15.46 l 1.41 -1.41 2.12 2.12 4.24 -4.24 1.41 1.41 L 5.54 19 z") }
    val Delete: ImageVector by lazy { icono("Delete", "M 6 19 c 0 1.1 .9 2 2 2 h 8 c 1.1 0 2 -.9 2 -2 V 7 H 6 v 12 z M 19 4 h -3.5 l -1 -1 h -5 l -1 1 H 5 v 2 h 14 V 4 z") }
    val Share: ImageVector by lazy { icono("Share", "M 18 16.08 c -.76 0 -1.44 .3 -1.96 .77 L 8.91 12.7 c .05 -.23 .09 -.46 .09 -.7 s -.04 -.47 -.09 -.7 l 7.05 -4.11 c .54 .5 1.25 .81 2.04 .81 1.66 0 3 -1.34 3 -3 s -1.34 -3 -3 -3 -3 1.34 -3 3 c 0 .24 .04 .47 .09 .7 L 8.04 9.81 C 7.5 9.31 6.79 9 6 9 c -1.66 0 -3 1.34 -3 3 s 1.34 3 3 3 c .79 0 1.5 -.31 2.04 -.81 l 7.12 4.16 c -.05 .21 -.08 .43 -.08 .65 0 1.61 1.31 2.92 2.92 2.92 1.61 0 2.92 -1.31 2.92 -2.92 s -1.31 -2.92 -2.92 -2.92 z") }
    val FileDownload: ImageVector by lazy { icono("FileDownload", "M 19 9 h -4 V 3 H 9 v 6 H 5 l 7 7 7 -7 z M 5 18 v 2 h 14 v -2 H 5 z") }
    val FileUpload: ImageVector by lazy { icono("FileUpload", "M 9 16 h 6 v -6 h 4 l -7 -7 -7 7 h 4 z m -4 2 h 14 v 2 H 5 z") }
    val Sms: ImageVector by lazy { icono("Sms", "M 20 2 H 4 c -1.1 0 -1.99 .9 -1.99 2 L 2 22 l 4 -4 h 14 c 1.1 0 2 -.9 2 -2 V 4 c 0 -1.1 -.9 -2 -2 -2 z M 9 11 H 7 V 9 h 2 v 2 z m 4 0 h -2 V 9 h 2 v 2 z m 4 0 h -2 V 9 h 2 v 2 z") }
    val Search: ImageVector by lazy { icono("Search", "M 15.5 14 h -.79 l -.28 -.27 A 6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16 c 1.61 0 3.09 -.59 4.23 -1.57 l .27 .28 v .79 l 5 4.99 L 20.49 19 l -4.99 -5 z m -6 0 C 7.01 14 5 11.99 5 9.5 S 7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14 z") }
    val FilterList: ImageVector by lazy { icono("FilterList", "M 10 18 h 4 v -2 h -4 v 2 z M 3 6 v 2 h 18 V 6 H 3 z m 3 7 h 12 v -2 H 6 v 2 z") }
    val ContentPaste: ImageVector by lazy { icono("ContentPaste", "M 19 2 h -4.18 C 14.4 .84 13.3 0 12 0 c -1.3 0 -2.4 .84 -2.82 2 H 5 c -1.1 0 -2 .9 -2 2 v 16 c 0 1.1 .9 2 2 2 h 14 c 1.1 0 2 -.9 2 -2 V 4 c 0 -1.1 -.9 -2 -2 -2 z m -7 0 c .55 0 1 .45 1 1 s -.45 1 -1 1 -1 -.45 -1 -1 .45 -1 1 -1 z m 7 18 H 5 V 4 h 2 v 3 h 10 V 4 h 2 v 16 z") }
    val ArrowBack: ImageVector by lazy { icono("ArrowBack", "M 20 11 H 7.83 l 5.59 -5.59 L 12 4 l -8 8 8 8 1.41 -1.41 L 7.83 13 H 20 v -2 z", espejo = true) }
    val KeyboardArrowRight: ImageVector by lazy { icono("KeyboardArrowRight", "M 8.59 16.59 13.17 12 8.59 7.41 10 6 l 6 6 -6 6 -1.41 -1.41 z", espejo = true) }
    val HourglassTop: ImageVector by lazy { icono("HourglassTop", "m 6 2 .01 6 L 10 12 l -3.99 4.01 L 6 22 h 12 v -6 l -4 -4 4 -3.99 V 2 H 6 z m 10 14.5 V 20 H 8 v -3.5 l 4 -4 4 4 z") }
    val Verified: ImageVector by lazy { icono("Verified", "m 23 12 -2.44 -2.79 .34 -3.69 -3.61 -.82 -1.89 -3.2 L 12 2.96 8.6 1.5 6.71 4.69 3.1 5.5 l .34 3.7 L 1 12 l 2.44 2.79 -.34 3.7 3.61 .82 L 8.6 22.5 l 3.4 -1.47 3.4 1.46 1.89 -3.19 3.61 -.82 -.34 -3.69 L 23 12 z m -12.91 4.72 -3.8 -3.81 1.48 -1.48 2.32 2.33 5.85 -5.87 1.48 1.48 -7.33 7.35 z") }
    val EventBusy: ImageVector by lazy { icono("EventBusy", "m 9.31 17 2.44 -2.44 L 14.19 17 l 1.06 -1.06 -2.44 -2.44 2.44 -2.44 L 14.19 10 l -2.44 2.44 L 9.31 10 l -1.06 1.06 2.44 2.44 -2.44 2.44 L 9.31 17 z M 19 3 h -1 V 1 h -2 v 2 H 8 V 1 H 6 v 2 H 5 c -1.11 0 -1.99 .9 -1.99 2 L 3 19 a 2 2 0 0 0 2 2 h 14 c 1.1 0 2 -.9 2 -2 V 5 c 0 -1.1 -.9 -2 -2 -2 z m 0 16 H 5 V 8 h 14 v 11 z") }
    val Block: ImageVector by lazy { icono("Block", "M 12 2 C 6.48 2 2 6.48 2 12 s 4.48 10 10 10 10 -4.48 10 -10 S 17.52 2 12 2 z M 4 12 c 0 -4.42 3.58 -8 8 -8 1.85 0 3.55 .63 4.9 1.69 L 5.69 16.9 A 7.902 7.902 0 0 1 4 12 z m 8 8 c -1.85 0 -3.55 -.63 -4.9 -1.69 L 18.31 7.1 A 7.902 7.902 0 0 1 20 12 c 0 4.42 -3.58 8 -8 8 z") }
    val AccessTime: ImageVector by lazy { icono("AccessTime", "M 11.99 2 C 6.47 2 2 6.48 2 12 s 4.47 10 9.99 10 C 17.52 22 22 17.52 22 12 S 17.52 2 11.99 2 z M 12 20 c -4.42 0 -8 -3.58 -8 -8 s 3.58 -8 8 -8 8 3.58 8 8 -3.58 8 -8 8 z", "M 12.5 7 H 11 v 6 l 5.25 3.15 .75 -1.23 -4.5 -2.67 z") }
    val WarningAmber: ImageVector by lazy { icono("WarningAmber", "M 12 5.99 19.53 19 H 4.47 L 12 5.99 M 12 2 1 21 h 22 L 12 2 z", "M 13 16 h -2 v 2 h 2 z m 0 -6 h -2 v 5 h 2 z") }

    /** 24×24, relleno sólido (el color real lo pone `Icon` con tint). [espejo] = se invierte en idiomas RTL. */
    // P24: acciones que antes tenían texto
    val AttachMoney: ImageVector by lazy { icono("AttachMoney", "M 11.8 10.9 c -2.27 -.59 -3 -1.2 -3 -2.15 0 -1.09 1.01 -1.85 2.7 -1.85 1.78 0 2.44 .85 2.5 2.1 h 2.21 c -.07 -1.72 -1.12 -3.3 -3.21 -3.81 V 3 h -3 v 2.16 c -1.94 .42 -3.5 1.68 -3.5 3.61 0 2.31 1.91 3.46 4.7 4.13 2.5 .6 3 1.48 3 2.41 0 .69 -.49 1.79 -2.7 1.79 -2.06 0 -2.87 -.92 -2.98 -2.1 h -2.2 c .12 2.19 1.76 3.42 3.68 3.83 V 21 h 3 v -2.15 c 1.95 -.37 3.5 -1.5 3.5 -3.55 0 -2.84 -2.43 -3.81 -4.7 -4.4 z") }
    val Refresh: ImageVector by lazy { icono("Refresh", "M 17.65 6.35 A 7.958 7.958 0 0 0 12 4 c -4.42 0 -7.99 3.58 -7.99 8 s 3.57 8 7.99 8 c 3.73 0 6.84 -2.55 7.73 -6 h -2.08 A 5.99 5.99 0 0 1 12 18 c -3.31 0 -6 -2.69 -6 -6 s 2.69 -6 6 -6 c 1.66 0 3.14 .69 4.22 1.78 L 13 11 h 7 V 4 l -2.35 2.35 z") }
    val Done: ImageVector by lazy { icono("Done", "M 9 16.2 4.8 12 l -1.4 1.4 L 9 19 21 7 l -1.4 -1.4 L 9 16.2 z") }
    val FilterAltOff: ImageVector by lazy { icono("FilterAltOff", "M 19.79 5.61 A .998 .998 0 0 0 19 4 H 6.83 l 7.97 7.97 4.99 -6.36 z M 2.81 2.81 1.39 4.22 10 13 v 6 c 0 .55 .45 1 1 1 h 2 c .55 0 1 -.45 1 -1 v -2.17 l 5.78 5.78 1.41 -1.41 L 2.81 2.81 z") }
    val ArrowForward: ImageVector by lazy { icono("ArrowForward", "m 12 4 -1.41 1.41 L 16.17 11 H 4 v 2 h 12.17 l -5.58 5.59 L 12 20 l 8 -8 z", espejo = true) }
    val Schedule: ImageVector by lazy { icono("Schedule", "M 11.99 2 C 6.47 2 2 6.48 2 12 s 4.47 10 9.99 10 C 17.52 22 22 17.52 22 12 S 17.52 2 11.99 2 z M 12 20 c -4.42 0 -8 -3.58 -8 -8 s 3.58 -8 8 -8 8 3.58 8 8 -3.58 8 -8 8 z", "M 12.5 7 H 11 v 6 l 5.25 3.15 .75 -1.23 -4.5 -2.67 z") }
    val SkipNext: ImageVector by lazy { icono("SkipNext", "M 6 18 l 8.5 -6 L 6 6 v 12 z M 16 6 v 12 h 2 V 6 h -2 z") }
    val CalendarMonth: ImageVector by lazy { icono("CalendarMonth", "M 19 4 h -1 V 2 h -2 v 2 H 8 V 2 H 6 v 2 H 5 c -1.11 0 -1.99 .9 -1.99 2 L 3 20 a 2 2 0 0 0 2 2 h 14 c 1.1 0 2 -.9 2 -2 V 6 c 0 -1.1 -.9 -2 -2 -2 z m 0 16 H 5 V 10 h 14 v 10 z M 9 14 H 7 v -2 h 2 v 2 z m 4 0 h -2 v -2 h 2 v 2 z m 4 0 h -2 v -2 h 2 v 2 z m -8 4 H 7 v -2 h 2 v 2 z m 4 0 h -2 v -2 h 2 v 2 z m 4 0 h -2 v -2 h 2 v 2 z") }
    val PlaylistAdd: ImageVector by lazy { icono("PlaylistAdd", "M 14 10 H 3 v 2 h 11 v -2 z m 0 -4 H 3 v 2 h 11 V 6 z m 4 8 v -4 h -2 v 4 h -4 v 2 h 4 v 4 h 2 v -4 h 4 v -2 h -4 z M 3 16 h 7 v -2 H 3 v 2 z") }
    val RestartAlt: ImageVector by lazy { icono("RestartAlt", "M 12 5 V 2 L 8 6 l 4 4 V 7 c 3.31 0 6 2.69 6 6 0 2.97 -2.17 5.43 -5 5.91 v 2.02 c 3.95 -.49 7 -3.85 7 -7.93 0 -4.42 -3.58 -8 -8 -8 z m -6 8 c 0 -1.65 .67 -3.15 1.76 -4.24 L 6.34 7.34 A 8.014 8.014 0 0 0 4 13 c 0 4.08 3.05 7.44 7 7.93 v -2.02 c -2.83 -.48 -5 -2.94 -5 -5.91 z") }
    /** Flecha circular antihoraria, sin reloj ni doble giro. */
    val Replay: ImageVector by lazy { icono("Replay", "M 12 5 V 1 L 7 6 l 5 5 V 7 c 3.31 0 6 2.69 6 6 s -2.69 6 -6 6 -6 -2.69 -6 -6 c 0 -1.01 .25 -1.97 .69 -2.8 L 4.23 8.74 A 7.93 7.93 0 0 0 3 13 c 0 4.97 4.03 9 9 9 s 9 -4.03 9 -9 -4.03 -9 -9 -9 z") }
    val ListAlt: ImageVector by lazy { icono("ListAlt", "M 19 5 v 14 H 5 V 5 h 14 m 1.1 -2 H 3.9 c -.5 0 -.9 .4 -.9 .9 v 16.2 c 0 .4 .4 .9 .9 .9 h 16.2 c .4 0 .9 -.5 .9 -.9 V 3.9 c 0 -.5 -.5 -.9 -.9 -.9 z M 11 7 h 6 v 2 h -6 V 7 z m 0 4 h 6 v 2 h -6 v -2 z m 0 4 h 6 v 2 h -6 z M 7 7 h 2 v 2 H 7 z m 0 4 h 2 v 2 H 7 z m 0 4 h 2 v 2 H 7 z") }
    val Send: ImageVector by lazy { icono("Send", "M 2.01 21 23 12 2.01 3 2 10 l 15 2 -15 2 z", espejo = true) }
    val ReceiptLong: ImageVector by lazy { icono("ReceiptLong", "M 19.5 3.5 18 2 l -1.5 1.5 L 15 2 l -1.5 1.5 L 12 2 l -1.5 1.5 L 9 2 7.5 3.5 6 2 v 14 H 3 v 3 c 0 1.66 1.34 3 3 3 h 12 c 1.66 0 3 -1.34 3 -3 V 2 l -1.5 1.5 z M 19 19 c 0 .55 -.45 1 -1 1 s -1 -.45 -1 -1 v -3 H 8 V 5 h 11 v 14 z", "M 9 7 h 6 v 2 H 9 z m 7 0 h 2 v 2 h -2 z m -7 3 h 6 v 2 H 9 z m 7 0 h 2 v 2 h -2 z") }
    val AddShoppingCart: ImageVector by lazy { icono("AddShoppingCart", "M 11 9 h 2 V 6 h 3 V 4 h -3 V 1 h -2 v 3 H 8 v 2 h 3 v 3 z m -4 9 c -1.1 0 -1.99 .9 -1.99 2 S 5.9 22 7 22 s 2 -.9 2 -2 -.9 -2 -2 -2 z m 10 0 c -1.1 0 -1.99 .9 -1.99 2 s .89 2 1.99 2 2 -.9 2 -2 -.9 -2 -2 -2 z m -9.83 -3.25 .03 -.12 .9 -1.63 h 7.45 c .75 0 1.41 -.41 1.75 -1.03 l 3.86 -7.01 L 19.42 4 h -.01 l -1.1 2 -2.76 5 H 8.53 l -.13 -.27 L 6.16 6 l -.95 -2 -.94 -2 H 1 v 2 h 2 l 3.6 7.59 -1.35 2.45 c -.16 .28 -.25 .61 -.25 .96 0 1.1 .9 2 2 2 h 12 v -2 H 7.42 c -.13 0 -.25 -.11 -.25 -.25 z") }
    val PictureAsPdf: ImageVector by lazy { icono("PictureAsPdf", "M 20 2 H 8 c -1.1 0 -2 .9 -2 2 v 12 c 0 1.1 .9 2 2 2 h 12 c 1.1 0 2 -.9 2 -2 V 4 c 0 -1.1 -.9 -2 -2 -2 z m -8.5 7.5 c 0 .83 -.67 1.5 -1.5 1.5 H 9 v 2 H 7.5 V 7 H 10 c .83 0 1.5 .67 1.5 1.5 v 1 z m 5 2 c 0 .83 -.67 1.5 -1.5 1.5 h -2.5 V 7 H 15 c .83 0 1.5 .67 1.5 1.5 v 3 z m 4 -3 H 19 v 1 h 1.5 V 11 H 19 v 2 h -1.5 V 7 h 3 v 1.5 z M 9 9.5 h 1 v -1 H 9 v 1 z M 4 6 H 2 v 14 c 0 1.1 .9 2 2 2 h 14 v -2 H 4 V 6 z m 10 5.5 h 1 v -3 h -1 v 3 z") }
    val Logout: ImageVector by lazy { icono("Logout", "m 17 7 -1.41 1.41 L 18.17 11 H 8 v 2 h 10.17 l -2.58 2.58 L 17 17 l 5 -5 z M 4 5 h 8 V 3 H 4 c -1.1 0 -2 .9 -2 2 v 14 c 0 1.1 .9 2 2 2 h 8 v -2 H 4 V 5 z", espejo = true) }
    val UploadFile: ImageVector by lazy { icono("UploadFile", "M 14 2 H 6 c -1.1 0 -1.99 .9 -1.99 2 L 4 20 c 0 1.1 .89 2 1.99 2 H 18 c 1.1 0 2 -.9 2 -2 V 8 l -6 -6 z m 4 18 H 6 V 4 h 7 v 5 h 5 v 11 z M 8 15.01 l 1.41 1.41 L 11 14.84 V 19 h 2 v -4.16 l 1.59 1.59 L 16 15.01 12.01 11 z") }
    val PhoneAndroid: ImageVector by lazy { icono("PhoneAndroid", "M 16 1 H 8 C 6.34 1 5 2.34 5 4 v 16 c 0 1.66 1.34 3 3 3 h 8 c 1.66 0 3 -1.34 3 -3 V 4 c 0 -1.66 -1.34 -3 -3 -3 z m -2 20 h -4 v -1 h 4 v 1 z m 3.25 -3 H 6.75 V 4 h 10.5 v 14 z") }

    // P37 (Material Icons, Apache 2.0)
    val Devices: ImageVector by lazy { icono("Devices", "M4 6h18V4H4c-1.1 0-2 .9-2 2v11H0v3h14v-3H4V6zm19 2h-6c-.55 0-1 .45-1 1v10c0 .55.45 1 1 1h6c.55 0 1-.45 1-1V9c0-.55-.45-1-1-1zm-1 9h-4v-7h4v7z") }
    val Sync: ImageVector by lazy { icono("Sync", "M12 4V1L8 5l4 4V6c3.31 0 6 2.69 6 6 0 1.01-.25 1.97-.7 2.8l1.46 1.46A7.93 7.93 0 0 0 20 12c0-4.42-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6 0-1.01.25-1.97.7-2.8L5.24 7.74A7.93 7.93 0 0 0 4 12c0 4.42 3.58 8 8 8v3l4-4-4-4v3z") }
    val Wifi: ImageVector by lazy { icono("Wifi", "m1 9 2 2c4.97-4.97 13.03-4.97 18 0l2-2C16.93 2.93 7.08 2.93 1 9zm8 8 3 3 3-3a4.237 4.237 0 0 0-6 0zm-4-4 2 2a7.074 7.074 0 0 1 10 0l2-2C15.14 9.14 8.87 9.14 5 13z") }
    val PersonAdd: ImageVector by lazy { icono("PersonAdd", "M15 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm-9-2V7H4v3H1v2h3v3h2v-3h3v-2H6zm9 4c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z") }
    val LinkOff: ImageVector by lazy { icono("LinkOff", "M17 7h-4v1.9h4c1.71 0 3.1 1.39 3.1 3.1 0 1.43-.98 2.63-2.31 2.98l1.46 1.46C20.88 15.61 22 13.95 22 12c0-2.76-2.24-5-5-5zm-1 4h-2.19l2 2H16zM2 4.27l3.11 3.11A4.991 4.991 0 0 0 2 12c0 2.76 2.24 5 5 5h4v-1.9H7c-1.71 0-3.1-1.39-3.1-3.1 0-1.59 1.21-2.9 2.76-3.07L8.73 11H8v2h2.73L13 15.27V17h1.73l4.01 4L20 19.74 3.27 3 2 4.27z") }

    // 0.27.0: Soporte (ficha del desarrollador), foto desde galería, bloqueo biométrico y clientes fijos.
    val Badge: ImageVector by lazy { icono("Badge", "M20 7h-5V4c0-1.1-.9-2-2-2h-2c-1.1 0-2 .9-2 2v3H4c-1.1 0-2 .9-2 2v11c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V9c0-1.1-.9-2-2-2zM9 12c.83 0 1.5.67 1.5 1.5S9.83 15 9 15s-1.5-.67-1.5-1.5S8.17 12 9 12zm3 6H6v-.75c0-1 2-1.5 3-1.5s3 .5 3 1.5V18zm1-9h-2V4h2v5zm5 7.5h-4V15h4v1.5zm0-3h-4V12h4v1.5z") }
    val Work: ImageVector by lazy { icono("Work", "M20 6h-4V4c0-1.11-.89-2-2-2h-4c-1.11 0-2 .89-2 2v2H4c-1.11 0-1.99.89-1.99 2L2 19c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V8c0-1.11-.89-2-2-2zm-6 0h-4V4h4v2z") }
    val PhotoLibrary: ImageVector by lazy { icono("PhotoLibrary", "M22 16V4c0-1.1-.9-2-2-2H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2zm-11-4 2.03 2.71L16 11l4 5H8l3-4zM2 6v14c0 1.1.9 2 2 2h14v-2H4V6H2z") }
    val Fingerprint: ImageVector by lazy { icono("Fingerprint", "M17.81 4.47c-.08 0-.16-.02-.23-.06C15.66 3.42 14 3 12.01 3c-1.98 0-3.86.47-5.57 1.41-.24.13-.54.04-.68-.2a.506.506 0 0 1 .2-.68C7.82 2.52 9.86 2 12.01 2c2.13 0 3.99.47 6.03 1.52.25.13.34.43.21.67a.49.49 0 0 1-.44.28zM3.5 9.72a.499.499 0 0 1-.41-.79c.99-1.4 2.25-2.5 3.75-3.27C9.98 4.04 14 4.03 17.15 5.65c1.5.77 2.76 1.86 3.75 3.25a.5.5 0 0 1-.12.7c-.23.16-.54.11-.7-.12a9.388 9.388 0 0 0-3.39-2.94c-2.87-1.47-6.54-1.47-9.4.01-1.36.7-2.5 1.7-3.4 2.96-.08.14-.23.21-.39.21zm6.25 12.07a.47.47 0 0 1-.35-.15c-.87-.87-1.34-1.43-2.01-2.64-.69-1.23-1.05-2.73-1.05-4.34 0-2.97 2.54-5.39 5.66-5.39s5.66 2.42 5.66 5.39c0 .28-.22.5-.5.5s-.5-.22-.5-.5c0-2.42-2.09-4.39-4.66-4.39-2.57 0-4.66 1.97-4.66 4.39 0 1.44.32 2.77.93 3.85.64 1.15 1.08 1.64 1.85 2.42.19.2.19.51 0 .71-.11.1-.24.15-.37.15zm7.17-1.85c-1.19 0-2.24-.3-3.1-.89-1.49-1.01-2.38-2.65-2.38-4.39 0-.28.22-.5.5-.5s.5.22.5.5c0 1.41.72 2.74 1.94 3.56.71.48 1.54.71 2.54.71.24 0 .64-.03 1.04-.1.27-.05.53.13.58.41.05.27-.13.53-.41.58-.57.11-1.07.12-1.21.12zM14.91 22c-.04 0-.09-.01-.13-.02-1.59-.44-2.63-1.03-3.72-2.1a7.297 7.297 0 0 1-2.17-5.22c0-1.62 1.38-2.94 3.08-2.94 1.7 0 3.08 1.32 3.08 2.94 0 1.07.93 1.94 2.08 1.94s2.08-.87 2.08-1.94c0-3.77-3.25-6.83-7.25-6.83-2.84 0-5.44 1.58-6.61 4.03-.39.81-.59 1.76-.59 2.8 0 .78.07 2.01.67 3.61.1.26-.03.55-.29.64-.26.1-.55-.04-.64-.29a11.14 11.14 0 0 1-.73-3.96c0-1.2.23-2.29.68-3.24 1.33-2.79 4.28-4.6 7.51-4.6 4.55 0 8.25 3.51 8.25 7.83 0 1.62-1.38 2.94-3.08 2.94s-3.08-1.32-3.08-2.94c0-1.07-.93-1.94-2.08-1.94s-2.08.87-2.08 1.94c0 1.71.66 3.31 1.87 4.51.95.94 1.86 1.46 3.27 1.85.27.07.42.35.35.61-.05.23-.26.38-.47.38z") }

    // Silueta monocromática de hoja Excel con su X, sin color de marca.
    val Excel: ImageVector by lazy { icono("Excel", "M14 3h8v18h-8v-2h6v-3h-4v-2h4v-3h-4V9h4V5h-6z", "M2 5l12-2v18L2 19V5zm3 3l2.2 4L5 16h2l1.2-2.5L9.5 16h2l-2.3-4 2.3-4h-2l-1.3 2.5L7 8H5z") }
    /** Triángulos sólidos ◄ y ► para spinners y selectores de paso. */
    val ArrowLeft: ImageVector by lazy { icono("ArrowLeft", "M 16 5 L 6 12 L 16 19 Z", espejo = true) }
    val ArrowRight: ImageVector by lazy { icono("ArrowRight", "M 8 5 L 18 12 L 8 19 Z", espejo = true) }
    val DocumentScanner: ImageVector by lazy { icono("DocumentScanner", "M 7 3 H 4 v 3 H 2 V 1 h 5 v 2 z m 15 3 V 1 h -5 v 2 h 3 v 3 h 2 z M 7 21 H 4 v -3 H 2 v 5 h 5 v -2 z m 13 -3 v 3 h -3 v 2 h 5 v -5 h -2 z M 19 18 c 0 1.1 -.9 2 -2 2 H 7 c -1.1 0 -2 -.9 -2 -2 V 6 c 0 -1.1 .9 -2 2 -2 h 10 c 1.1 0 2 .9 2 2 v 12 z M 15 8 H 9 v 2 h 6 V 8 z m 0 3 H 9 v 2 h 6 v -2 z m 0 3 H 9 v 2 h 6 v -2 z") }

    private fun icono(nombre: String, vararg trazados: String, espejo: Boolean = false): ImageVector =
        ImageVector.Builder(
            name = nombre, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f, autoMirror = espejo,
        ).apply { trazados.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) } }.build()
}
