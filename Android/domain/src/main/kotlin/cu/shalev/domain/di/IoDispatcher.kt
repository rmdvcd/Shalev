package cu.shalev.domain.di

import javax.inject.Qualifier

/**
 * Dispatcher para trabajo de E/S y cálculos pesados (consultas, mapeo de listas grandes, agregaciones).
 *
 * 0.30.0 (F1): vivía en `cu.shalev.data.di`, pero los casos de uso son los que deben sacar el trabajo del hilo
 * principal y están en `:domain`, que no puede depender de `:data`. Aquí lo provee `DataModule`
 * (`@Provides @IoDispatcher fun io() = Dispatchers.IO`) y lo usan las tres capas.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
