package cu.shalev.data.di

import cu.shalev.domain.di.IoDispatcher
import cu.shalev.domain.repository.ServicioRepository
import android.content.Context
import cu.shalev.core.time.Clock
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.licencia.DataStoreLicenseStore
import cu.shalev.data.licencia.LicenciaRepositoryImpl
import cu.shalev.data.repository.PerfilRepositoryImpl
import cu.shalev.data.repository.ConfiguracionInicialRepositoryImpl
import cu.shalev.data.repository.PreferenciasRepositoryImpl
import cu.shalev.data.repository.RegistroRepositoryImpl
import cu.shalev.data.respaldo.RespaldoRepositoryImpl
import cu.shalev.domain.repository.ExportadorDocumentos
import cu.shalev.domain.repository.InsumoRepository
import cu.shalev.domain.repository.LicenciaRepository
import cu.shalev.domain.repository.PerfilRepository
import cu.shalev.domain.repository.PreciosRepository
import cu.shalev.domain.repository.ConfiguracionInicialRepository
import cu.shalev.domain.repository.PreferenciasRepository
import cu.shalev.domain.repository.ProductoRepository
import cu.shalev.domain.repository.RegistroRepository
import cu.shalev.domain.repository.RespaldoRepository
import cu.shalev.domain.repository.TurnoRepository
import cu.shalev.domain.repository.VentaRepository
import cu.shalev.licencia.LicenseStore
import cu.shalev.data.security.DatabasePassphrase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds abstract fun licenseStore(impl: DataStoreLicenseStore): LicenseStore
    @Binds abstract fun licenciaRepository(impl: LicenciaRepositoryImpl): LicenciaRepository
    /** 0.31.0 (mitigación 7): clave con la que la principal firma los sub-tokens de las secundarias. */
    @Binds abstract fun firmanteLicencia(impl: cu.shalev.data.licencia.FirmanteLicenciaAndroid): cu.shalev.licencia.secondary.FirmanteLicencia
    // 0.26.0 (P74): registro de la prueba fuera de la app + reloj monótono.
    @Binds abstract fun registroExterno(impl: cu.shalev.data.licencia.prueba.RegistroPruebaAndroid): cu.shalev.licencia.prueba.RegistroExterno
    @Binds abstract fun pruebaRepository(impl: cu.shalev.data.licencia.prueba.RegistroPruebaAndroid): cu.shalev.domain.repository.PruebaRepository
    @Binds abstract fun detectorRetroceso(impl: cu.shalev.data.licencia.prueba.DetectorRelojAndroid): cu.shalev.licencia.prueba.DetectorRetroceso
    @Binds abstract fun perfilRepository(impl: PerfilRepositoryImpl): PerfilRepository
    @Binds abstract fun preferenciasRepository(impl: PreferenciasRepositoryImpl): PreferenciasRepository
    @Binds abstract fun configuracionInicialRepository(impl: ConfiguracionInicialRepositoryImpl): ConfiguracionInicialRepository
    @Binds abstract fun ajustesDispositivoRepository(impl: cu.shalev.data.repository.AjustesDispositivoRepositoryImpl): cu.shalev.domain.repository.AjustesDispositivoRepository
    @Binds abstract fun clienteFijoRepository(impl: cu.shalev.data.repository.ClienteFijoRepositoryImpl): cu.shalev.domain.repository.ClienteFijoRepository
    @Binds abstract fun productoRepository(impl: cu.shalev.data.sync.ProductoRepositorySegunTipo): ProductoRepository
    @Binds abstract fun insumoRepository(impl: cu.shalev.data.sync.InsumoRepositorySegunTipo): InsumoRepository
    @Binds abstract fun servicioRepository(impl: cu.shalev.data.sync.ServicioRepositorySegunTipo): ServicioRepository
    @Binds abstract fun ventaRepository(impl: cu.shalev.data.sync.VentaRepositorySegunTipo): VentaRepository
    @Binds abstract fun turnoRepository(impl: cu.shalev.data.sync.TurnoRepositorySegunTipo): TurnoRepository
    @Binds abstract fun registroRepository(impl: RegistroRepositoryImpl): RegistroRepository
    @Binds abstract fun preciosRepository(impl: cu.shalev.data.sync.PreciosRepositorySegunTipo): PreciosRepository
    @Binds abstract fun respaldoRepository(impl: RespaldoRepositoryImpl): RespaldoRepository
    @Binds abstract fun mantenimientoRepository(impl: cu.shalev.data.repository.MantenimientoRepositoryImpl): cu.shalev.domain.repository.MantenimientoRepository
    @Binds abstract fun exportador(impl: cu.shalev.data.sync.ExportadorSegunTipo): ExportadorDocumentos

    // P37: Principal / Secundaria
    @Binds abstract fun tipoAppRepository(impl: cu.shalev.data.sync.TipoAppRepositoryImpl): cu.shalev.domain.repository.TipoAppRepository
    @Binds abstract fun principalRepository(impl: cu.shalev.data.sync.PrincipalRepositoryImpl): cu.shalev.domain.repository.PrincipalRepository
    @Binds abstract fun secundariaRepository(impl: cu.shalev.data.sync.SecundariaRepositoryImpl): cu.shalev.domain.repository.SecundariaRepository
    @Binds abstract fun puertaTurno(impl: cu.shalev.data.sync.PuertaTurnoImpl): cu.shalev.domain.repository.PuertaTurno
    @Binds abstract fun estadoApp(impl: cu.shalev.data.local.EstadoAppRepositoryImpl): cu.shalev.domain.repository.EstadoAppRepository
    @Binds abstract fun estadoPantallas(impl: cu.shalev.data.local.EstadoPantallasRepositoryImpl): cu.shalev.domain.repository.EstadoPantallasRepository
    @Binds abstract fun actualizaciones(impl: cu.shalev.data.network.ActualizacionesRepositoryImpl): cu.shalev.domain.repository.ActualizacionesRepository

    companion object {
        @Provides fun clock(): Clock = Clock.System
        @Provides @IoDispatcher fun io(): CoroutineDispatcher = Dispatchers.IO

        /** La passphrase (envuelta por el Keystore) se obtiene una vez; la instancia vive toda la app. */
        @Provides @Singleton
        fun database(@ApplicationContext context: Context, passphrase: DatabasePassphrase): ShalevDatabase =
            ShalevDatabase.crear(context, passphrase.get())
    }
}
