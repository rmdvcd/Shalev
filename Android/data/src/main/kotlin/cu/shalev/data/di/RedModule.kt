package cu.shalev.data.di

import cu.shalev.data.foto.FotoRepositoryImpl
import cu.shalev.data.network.Red
import cu.shalev.domain.repository.FotoRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * INTERNET solo para GitHub (actualizaciones y lista de revocadas) y la red local (sincronización):
 * este es el ÚNICO cliente HTTP de la app. Coil no tiene módulo de red (solo muestra archivos locales).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RedModule {
    @Binds abstract fun fotos(impl: FotoRepositoryImpl): FotoRepository

    companion object {
        @Provides @Singleton fun okHttp(): OkHttpClient = Red.cliente()
    }
}
