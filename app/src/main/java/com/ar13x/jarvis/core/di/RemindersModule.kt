package com.ar13x.jarvis.core.di

import android.content.Context
import androidx.room.Room
import com.ar13x.jarvis.reminders.data.JarvisDatabase
import com.ar13x.jarvis.reminders.data.OccurrenceDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RemindersModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): JarvisDatabase =
        Room.databaseBuilder(context, JarvisDatabase::class.java, "jarvis.db")
            // The mirror is derived data, re-fetchable in one request, so a
            // schema change is not worth a migration path. Destroying it costs a
            // window that the next refresh rebuilds; the tasks themselves live
            // on the gateway and are never at risk.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideOccurrenceDao(db: JarvisDatabase): OccurrenceDao = db.occurrences()
}
