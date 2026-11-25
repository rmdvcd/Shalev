import XCTest
@testable import IShalevDomain

/// Recorridos guiados: mismas reglas que Android (`Recorridos.kt` + `CoachMarks.kt`) y que DShalev
/// (`dshalev/recorridos.py`). Se prueban la lógica y los textos, no la pintura (eso lo valida la compilación).
final class RecorridosTests: XCTestCase {

    // MARK: - Catálogo y textos

    func testCadaRecorridoTienePasosYClaveDeEstadoComoAndroid() {
        XCTAssertEqual(RecorridoPantalla.allCases.count, 13, "13 recorridos: 6 pantallas y 7 formularios/ventanas")
        for recorrido in RecorridoPantalla.allCases {
            XCTAssertFalse(recorrido.pasos.isEmpty, "\(recorrido.rawValue) sin pasos")
            // La clave es la de `OnboardingPreferences.clave`: `<clave>_tour_shown`.
            XCTAssertEqual(recorrido.claveDeEstado, "\(recorrido.rawValue)_tour_shown")
            // Un recorrido de más de 8 pasos no cabe en el globo (máximo de Android).
            XCTAssertLessThanOrEqual(recorrido.pasos.count, 8, recorrido.rawValue)
        }
    }

    func testLosTextosRespetanElMaximoDeAndroid() {
        for recorrido in RecorridoPantalla.allCases {
            for paso in recorrido.pasos {
                XCTAssertLessThanOrEqual(paso.titulo.count, 28, "\(recorrido.rawValue)/\(paso.objetivo): «\(paso.titulo)»")
                XCTAssertLessThanOrEqual(paso.frase.count, 120, "\(recorrido.rawValue)/\(paso.objetivo)")
                // Android: la frase empieza por la acción y termina en punto (así están escritas las 51 de
                // `Recorridos.kt`, p. ej. «Toca Agregar para crear un producto o un insumo.»).
                XCTAssertTrue(paso.frase.first?.isUppercase ?? false, "\(recorrido.rawValue)/\(paso.objetivo)")
                XCTAssertTrue(paso.frase.hasSuffix("."), "\(recorrido.rawValue)/\(paso.objetivo)")
            }
        }
    }

    func testUnTextoDemasiadoLargoNoSeConstruye() {
        // `precondition` solo se dispara en compilación de depuración; la prueba existe para documentar el límite y
        // para que un texto nuevo que lo supere falle en la suite antes de llegar al globo.
        XCTAssertEqual(PasoRecorrido.maximoTitulo, 28)
        XCTAssertEqual(PasoRecorrido.maximoFrase, 120)
        let valido = PasoRecorrido("x", "Título", "Frase correcta")
        XCTAssertEqual(valido.objetivo, "x")
    }

    func testNoHayDosPasosConElMismoObjetivoDentroDeUnRecorrido() {
        for recorrido in RecorridoPantalla.allCases {
            let objetivos = recorrido.pasos.map(\.objetivo)
            XCTAssertEqual(Set(objetivos).count, objetivos.count, "objetivo repetido en \(recorrido.rawValue)")
        }
    }

    // MARK: - Estado del recorrido

    func testSoloArrancaConLosPasosCuyoObjetivoEstaEnPantalla() {
        let visibles: Set<String> = ["venta_total", "venta_cobrar", "venta_metodo"]
        let estado = EstadoRecorrido.iniciar(.venta, objetivos: visibles)
        XCTAssertNotNil(estado)
        // Se conserva el orden de la definición, no el del conjunto de visibles.
        XCTAssertEqual(estado?.pasos.map(\.objetivo), ["venta_total", "venta_metodo", "venta_cobrar"])
        XCTAssertEqual(estado?.pasos.count, 3)
    }

    func testSinObjetivosNoSeAbreNada() {
        XCTAssertNil(EstadoRecorrido.iniciar(.venta, objetivos: []))
        XCTAssertNil(EstadoRecorrido.iniciar(.venta, objetivos: ["otro_boton"]))
    }

    func testAvanzaYRetrocedeSinSalirse() {
        var estado = EstadoRecorrido.iniciar(.servicios, objetivos: ["servicios_lista", "servicios_buscar", "servicios_agregar"])!
        XCTAssertEqual(estado.progreso, "1 de 3")
        XCTAssertTrue(estado.esPrimero)
        XCTAssertFalse(estado.esUltimo)

        estado.anterior()  // en el primero no hace nada
        XCTAssertEqual(estado.indice, 0)

        estado.siguiente()
        XCTAssertEqual(estado.pasoActual?.objetivo, "servicios_buscar")
        XCTAssertEqual(estado.progreso, "2 de 3")

        estado.siguiente()
        XCTAssertTrue(estado.esUltimo)
        XCTAssertEqual(estado.progreso, "3 de 3")

        estado.siguiente()  // en el último tampoco: cerrar es decisión de la vista
        XCTAssertEqual(estado.indice, 2)
    }

    func testUnRecorridoVacioNoEstaActivo() {
        let estado = EstadoRecorrido(pasos: [])
        XCTAssertFalse(estado.activo)
        XCTAssertNil(estado.pasoActual)
        XCTAssertEqual(estado.progreso, "")
    }

    // MARK: - Persistencia y política

    private func almacenDePrueba() -> AlmacenRecorridosUsuario {
        // Dominio propio para no tocar las preferencias reales del simulador ni del usuario.
        let nombre = "shalev.recorridos.pruebas.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: nombre)!
        defaults.removePersistentDomain(forName: nombre)
        return AlmacenRecorridosUsuario(defaults: defaults)
    }

    func testElEstadoSeGuardaConLaClaveDeAndroidYSePuedeReiniciar() {
        let almacen = almacenDePrueba()
        XCTAssertFalse(almacen.visto(.inventario))
        almacen.marcarVisto(.inventario)
        XCTAssertTrue(almacen.visto(.inventario))
        XCTAssertFalse(almacen.visto(.servicios), "marcar uno no marca los demás")

        almacen.reiniciar()
        XCTAssertFalse(almacen.visto(.inventario))
    }

    func testSoloSeMuestraLaPrimeraVezYSiHayPasosPosibles() {
        let almacen = almacenDePrueba()
        let objetivos: Set<String> = ["inventario_lista", "inventario_buscar"]

        XCTAssertTrue(PoliticaRecorridos.debeMostrar(.inventario, almacen: almacen, objetivos: objetivos))

        almacen.marcarVisto(.inventario)
        // Ya visto: no vuelve a salir solo, aunque haya objetivos.
        XCTAssertFalse(PoliticaRecorridos.debeMostrar(.inventario, almacen: almacen, objetivos: objetivos))

        // Sin objetivos (pantalla todavía sin pintar) tampoco: el globo no tendría dónde señalar.
        XCTAssertFalse(PoliticaRecorridos.debeMostrar(.servicios, almacen: almacen, objetivos: []))
    }

    func testLaVentaSinObjetivosDisponiblesNoSeOfrece() {
        // Con el turno cerrado la pantalla de venta no muestra carrito ni cobrar: el recorrido no arranca.
        let almacen = almacenDePrueba()
        XCTAssertFalse(PoliticaRecorridos.debeMostrar(.venta, almacen: almacen,
                                                      objetivos: ["inventario_lista"]))
    }
}
