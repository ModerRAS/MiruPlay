package com.miruplay.tv.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import com.miruplay.tv.data.db.MiruPlayDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MiruPlayDatabase {
        return Room.databaseBuilder(
            context,
            MiruPlayDatabase::class.java,
            "miruplay.db"
        )
            .addMigrations(*miruPlayDatabaseMigrations())
            .build()
    }
}

internal fun miruPlayDatabaseMigrations(): Array<Migration> =
    arrayOf(
        MiruPlayDatabase.MIGRATION_1_2,
        MiruPlayDatabase.MIGRATION_2_3,
        MiruPlayDatabase.MIGRATION_3_4,
        MiruPlayDatabase.MIGRATION_4_5,
        MiruPlayDatabase.MIGRATION_5_6,
        MiruPlayDatabase.MIGRATION_6_7,
        MiruPlayDatabase.MIGRATION_7_8,
    )
