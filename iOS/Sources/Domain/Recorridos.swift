import Foundation

/// Recorridos guiados (coach marks) de iOS, con la misma mecánica y los mismos textos que Android
/// (`Recorridos.kt` + `CoachMarks.kt`) y que la versión de escritorio (`dshalev/recorridos.py`).
///
/// Reglas que se conservan de la referencia:
/// - Un recorrido por pantalla o ventana, con hasta 8 pasos agrupados (los controles relacionados comparten paso).
/// - Los pasos cuyo objetivo no está en pantalla al empezar **se saltan** (por ejemplo «Vender» con el turno cerrado).
/// - El estado se guarda por recorrido con la misma clave que Android: `<clave>_tour_shown`.
/// - Textos cortos: título de 28 caracteres como máximo y una frase de 120 que empieza por la acción.
///
/// Lo que **no** se copia de Android: aquí no hay `DataStore`; el estado vive en `UserDefaults`, como el resto de
/// preferencias de iOS, y el velo no intercepta toques (se puede seguir usando la pantalla con el recorrido abierto).
public enum RecorridoPantalla: String, CaseIterable, Identifiable, Sendable {
    // Pantallas
    case inicio, inventario, servicios, registros, herramientas, ajustes
    // Ventanas y formularios
    case venta, producto, servicio, turno, ajustePrecio, fichaCliente, exportar

    public var id: String { rawValue }

    /// Clave del estado guardado, idéntica a la de Android: `<clave>_tour_shown`.
    public var claveDeEstado: String { "\(rawValue)_tour_shown" }

    public var titulo: String {
        switch self {
        case .inicio: "Inicio"
        case .inventario: "Inventario"
        case .servicios: "Servicios"
        case .registros: "Registros"
        case .herramientas: "Herramientas"
        case .ajustes: "Ajustes"
        case .venta: "Nueva venta"
        case .producto: "Nuevo producto"
        case .servicio: "Nuevo servicio"
        case .turno: "Turno de caja"
        case .ajustePrecio: "Ajuste de precio"
        case .fichaCliente: "Ficha del cliente"
        case .exportar: "Exportar"
        }
    }

    public var pasos: [PasoRecorrido] {
        switch self {
        case .inicio:
            [
                PasoRecorrido("inicio_periodo", "Período", "Elige turno, hoy, 7 días, mes o año: todos los resúmenes cambian."),
                PasoRecorrido("inicio_ventas", "Ventas", "Toca para ver el importe y la ganancia del período, con su gráfico."),
                PasoRecorrido("inicio_metodos", "Métodos de pago", "Toca para ver cuánto cobraste en efectivo y cuánto por transferencia."),
                PasoRecorrido("inicio_top", "Rankings", "Cada lista muestra su Top 3 del período: productos, servicios, empleados y clientes."),
                PasoRecorrido("inicio_nueva_venta", "Nueva venta", "Toca aquí para cobrar un producto o un servicio."),
            ]
        case .inventario:
            [
                PasoRecorrido("inventario_lista", "Tus productos", "Toca un artículo para ver su ficha. Mantén pulsado para elegir varios."),
                PasoRecorrido("inventario_buscar", "Buscar", "Escribe parte del nombre para encontrar un artículo."),
                PasoRecorrido("inventario_ordenar", "Ordenar", "Toca para cambiar el orden: recientes, A–Z, mayor o menor precio."),
                PasoRecorrido("inventario_exportar", "Exportar", "Envía la lista en Excel, PDF o como promoción para clientes."),
                PasoRecorrido("inventario_agregar", "Agregar", "Toca Agregar para crear un producto o un insumo."),
            ]
        case .servicios:
            [
                PasoRecorrido("servicios_lista", "Tus servicios", "Toca un servicio para ver su ficha; mantén pulsado para elegir varios."),
                PasoRecorrido("servicios_buscar", "Buscar", "Escribe parte del nombre para encontrar un servicio."),
                PasoRecorrido("servicios_agregar", "Agregar", "Toca Agregar para crear un servicio nuevo."),
            ]
        case .registros:
            [
                PasoRecorrido("registros_lista", "Registros", "Toca una venta para ver el detalle; aquí también anulas o corriges."),
                PasoRecorrido("registros_filtro", "Filtrar", "Filtra por fecha, importe o forma de pago."),
                PasoRecorrido("registros_turnos", "Turnos", "Cambia a Turnos para ver arqueos y cierres de caja."),
                PasoRecorrido("registros_exportar", "Exportar", "Envía los registros filtrados en Excel o PDF."),
            ]
        case .herramientas:
            [
                PasoRecorrido("herramientas_precios", "Ajustes de precio", "Crea un ajuste por volumen o tipo de cliente y actívalo cuando quieras."),
                PasoRecorrido("herramientas_fichas", "Fichas de costo", "Calcula el costo de una receta o de un servicio antes de ponerle precio."),
                PasoRecorrido("herramientas_importar", "Importar", "Trae artículos desde una foto o un documento; revisa antes de guardar."),
            ]
        case .ajustes:
            [
                PasoRecorrido("ajustes_modulos", "Módulos", "Activa o desactiva Ventas, Inventario y Servicios. Lo oculto no se borra."),
                PasoRecorrido("ajustes_empleados", "Empleados", "Crea empleados y decide qué puede hacer cada uno."),
                PasoRecorrido("ajustes_licencia", "Licencia", "Consulta tu licencia, el ID de recuperación y el estado del periodo de prueba."),
                PasoRecorrido("ajustes_respaldo", "Respaldo", "Guarda una copia de tus datos y restáurala cuando lo necesites."),
                PasoRecorrido("ajustes_ayuda", "Ayuda", "Aquí tienes el manual, los atajos y el soporte."),
            ]
        case .venta:
            [
                PasoRecorrido("venta_carrito", "Carrito", "Ajusta cada cantidad con −1 y +1, o toca Quitar."),
                PasoRecorrido("venta_agregar", "Agregar", "Toca Agregar para volver a la lista y sumar más artículos."),
                PasoRecorrido("venta_total", "Total", "Revisa aquí el importe que vas a cobrar."),
                PasoRecorrido("venta_metodo", "Forma de pago", "Toca Efectivo o Transferencia antes de cobrar."),
                PasoRecorrido("venta_cobrar", "Cobrar", "Toca Continuar: en efectivo ves el comprobante; con transferencia, el QR."),
            ]
        case .producto:
            [
                PasoRecorrido("producto_nombre", "Nombre", "Escribe el nombre como lo conocen tus clientes; de 3 a 80 caracteres."),
                PasoRecorrido("producto_precio", "Precio y costo", "El precio debe superar el costo. La ganancia se calcula sola."),
                PasoRecorrido("producto_stock", "Existencia", "Escribe cuántos tienes y a partir de cuántos quieres que te avise."),
                PasoRecorrido("producto_guardar", "Guardar", "Toca Guardar; el movimiento de existencia queda registrado con su motivo."),
            ]
        case .servicio:
            [
                PasoRecorrido("servicio_nombre", "Nombre", "Escribe el nombre del servicio; de 3 a 80 caracteres."),
                PasoRecorrido("servicio_precio", "Precio", "Pon el precio de venta. Los servicios no descuentan existencia."),
                PasoRecorrido("servicio_guardar", "Guardar", "Toca Guardar para tenerlo disponible al cobrar."),
            ]
        case .turno:
            [
                PasoRecorrido("turno_fondo", "Fondo inicial", "Escribe el efectivo con el que empiezas el turno."),
                PasoRecorrido("turno_movimiento", "Movimientos", "Anota el efectivo que entra o sale sin ser una venta."),
                PasoRecorrido("turno_cerrar", "Cerrar", "Al terminar, cuenta el efectivo y cierra: se guarda el arqueo."),
            ]
        case .ajustePrecio:
            [
                PasoRecorrido("ajuste_condicion", "Condición", "Elige si el ajuste es por volumen o por tipo de cliente."),
                PasoRecorrido("ajuste_lineas", "Artículos", "Marca los artículos a los que se aplica el ajuste."),
                PasoRecorrido("ajuste_activar", "Activar", "Guarda el ajuste y actívalo; se aplica al cobrar."),
            ]
        case .fichaCliente:
            [
                PasoRecorrido("cliente_historial", "Historial", "Aquí ves todas las compras del cliente."),
                PasoRecorrido("cliente_editar", "Editar", "Corrige nombre, carné o teléfono; se actualizan sus compras anteriores."),
            ]
        case .exportar:
            [
                PasoRecorrido("exportar_alcance", "Qué se exporta", "Elige la lista o el período que quieres compartir."),
                PasoRecorrido("exportar_formato", "Formato", "Toca Excel, PDF o imagen para guardarlo o enviarlo."),
            ]
        }
    }
}

/// Un paso del recorrido. `objetivo` es la clave que la vista marca con `recorridoObjetivo(_:)`.
public struct PasoRecorrido: Identifiable, Equatable, Sendable {
    public static let maximoTitulo = 28
    public static let maximoFrase = 120

    public let objetivo: String
    public let titulo: String
    public let frase: String

    public var id: String { objetivo }

    /// Se valida al construir: un texto más largo no entra en el globo de Android y aquí tampoco debe hacerlo.
    /// En producción los textos son constantes y la comprobación no cuesta nada; en las pruebas delata un descuido.
    public init(_ objetivo: String, _ titulo: String, _ frase: String) {
        precondition(!objetivo.isEmpty, "Un paso necesita objetivo")
        precondition(titulo.count <= Self.maximoTitulo, "Título demasiado largo (\(titulo.count)): \(titulo)")
        precondition(frase.count <= Self.maximoFrase, "Frase demasiado larga (\(frase.count)): \(frase)")
        self.objetivo = objetivo
        self.titulo = titulo
        self.frase = frase
    }
}

/// Estado de un recorrido en curso: qué pasos tienen su objetivo en pantalla y en cuál va.
///
/// Es lógica pura (sin SwiftUI) para poder probarla con `swift test`: `CoachMarkHost` solo la pinta.
public struct EstadoRecorrido: Equatable, Sendable {
    public let pasos: [PasoRecorrido]
    public private(set) var indice: Int = 0

    /// `true` si hay un recorrido abierto.
    public var activo: Bool { !pasos.isEmpty }
    public var pasoActual: PasoRecorrido? { activo ? pasos[indice] : nil }
    public var progreso: String { activo ? "\(indice + 1) de \(pasos.count)" : "" }
    public var esUltimo: Bool { activo && indice == pasos.count - 1 }
    public var esPrimero: Bool { indice == 0 }

    public init(pasos: [PasoRecorrido]) {
        self.pasos = pasos
    }

    /// Arranca solo con los pasos cuyo objetivo existe en pantalla (los demás se saltan, como en Android).
    /// Devuelve `false` si no queda ninguno: en ese caso no se abre ningún velo.
    public static func iniciar(_ recorrido: RecorridoPantalla, objetivos: Set<String>) -> EstadoRecorrido? {
        let disponibles = recorrido.pasos.filter { objetivos.contains($0.objetivo) }
        guard !disponibles.isEmpty else { return nil }
        return EstadoRecorrido(pasos: disponibles)
    }

    /// Avanza un paso. En el último no hace nada: cerrar es decisión de quien lo muestra (`finalizar`).
    public mutating func siguiente() {
        guard activo, !esUltimo else { return }
        indice += 1
    }

    public mutating func anterior() {
        guard activo, !esPrimero else { return }
        indice -= 1
    }
}

/// Qué recorridos ya se vieron y cuáles toca enseñar, con la misma clave de almacenamiento que Android.
public protocol AlmacenRecorridos: AnyObject {
    func visto(_ recorrido: RecorridoPantalla) -> Bool
    func marcarVisto(_ recorrido: RecorridoPantalla)
    func reiniciar()
}

/// `UserDefaults` en la app real; en las pruebas se usa una instancia con su propio dominio.
public final class AlmacenRecorridosUsuario: AlmacenRecorridos {
    private let defaults: UserDefaults

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    public func visto(_ recorrido: RecorridoPantalla) -> Bool {
        defaults.bool(forKey: recorrido.claveDeEstado)
    }

    public func marcarVisto(_ recorrido: RecorridoPantalla) {
        defaults.set(true, forKey: recorrido.claveDeEstado)
    }

    public func reiniciar() {
        for recorrido in RecorridoPantalla.allCases {
            defaults.removeObject(forKey: recorrido.claveDeEstado)
        }
    }
}

/// Decide si hay que enseñar un recorrido al abrir una pantalla: solo la primera vez y solo si tiene pasos posibles.
public enum PoliticaRecorridos {
    public static func debeMostrar(_ recorrido: RecorridoPantalla, almacen: AlmacenRecorridos,
                                   objetivos: Set<String>) -> Bool {
        // Un recorrido ya visto no vuelve a salir solo; para repetirlo está Ayuda → Recorridos.
        guard !almacen.visto(recorrido) else { return false }
        return !recorrido.pasos.filter { objetivos.contains($0.objetivo) }.isEmpty
    }
}
