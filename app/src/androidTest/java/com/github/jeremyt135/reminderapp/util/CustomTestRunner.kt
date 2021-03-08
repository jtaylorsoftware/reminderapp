package com.github.jeremyt135.reminderapp.util

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.test.runner.AndroidJUnitRunner
import androidx.work.Configuration
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EarlyEntryPoint
import dagger.hilt.android.EarlyEntryPoints
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.CustomTestApplication
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

@EarlyEntryPoint
@InstallIn(SingletonComponent::class)
interface WorkerFactoryEntryPoint {
    fun getWorkerFactory(): HiltWorkerFactory
}

abstract class CustomTestAppBase : Application(), Configuration.Provider {

    private fun getWorkerFactory() =
        EarlyEntryPoints.get(this, WorkerFactoryEntryPoint::class.java).getWorkerFactory()

    final override fun getWorkManagerConfiguration() =
        Configuration.Builder()
            .setWorkerFactory(getWorkerFactory())
            .build()
}

@CustomTestApplication(CustomTestAppBase::class)
interface MyTestApplication

/**
 * A custom TestRunner that allows Hilt to be used in tests.
 */
class CustomTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application {
        return super.newApplication(cl, MyTestApplication_Application::class.java.name, context)
    }
}


