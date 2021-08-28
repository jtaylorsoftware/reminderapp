package com.github.jeremyt135.reminderapp.di

import android.content.Context
import android.content.SharedPreferences
import com.github.jeremyt135.R
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


@InstallIn(SingletonComponent::class)
@Module
object SharedPreferencesModule {
    @Singleton
    @Provides
    fun provideGlobalSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return with(context) {
            getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE)
        }
    }
}