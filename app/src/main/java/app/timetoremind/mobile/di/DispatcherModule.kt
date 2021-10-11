package app.timetoremind.mobile.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import javax.inject.Qualifier
import javax.inject.Singleton


@Qualifier
annotation class IoDispatcher

@Qualifier
annotation class DefaultDispatcher

/**
 * Dispatchers.main bound scope
 */
@Qualifier
annotation class AppCoroutineScope

@InstallIn(SingletonComponent::class)
@Module
object DispatcherModule {
    @IoDispatcher
    @Provides
    @Singleton
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @DefaultDispatcher
    @Provides
    @Singleton
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @AppCoroutineScope
    @Provides
    @Singleton
    fun provideApplicationScope() =
        MainScope() + CoroutineName("ReminderApplication:applicationScope")
}