package com.dungeonmasterai.data

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import kotlinx.coroutines.Dispatchers

internal fun openMemoryDatabase(): AppDatabase =
    Room.inMemoryDatabaseBuilder(PathContext(File("build")), AppDatabase::class.java)
        .setDriver(BundledSQLiteDriver())
        .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

internal fun openFileDatabase(file: File): AppDatabase =
    Room.databaseBuilder(PathContext(file.parentFile ?: file), AppDatabase::class.java, file.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
        .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
