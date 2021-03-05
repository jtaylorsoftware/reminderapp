package com.github.jeremyt135.reminderapp.di

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

@Qualifier
annotation class MainDispatcher

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

    @MainDispatcher
    @Provides
    @Singleton
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @AppCoroutineScope
    @Provides
    @Singleton
    fun provideApplicationScope() =
        MainScope() + CoroutineName("ReminderApplication:applicationScope")

}