package cu.shalev.data.security.di

import cu.shalev.data.security.AndroidDeviceKey
import cu.shalev.licencia.crypto.DeviceKey
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {
    @Binds
    abstract fun deviceKey(impl: AndroidDeviceKey): DeviceKey
}
