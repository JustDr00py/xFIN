package com.xfin.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xfin.core.database.MIGRATION_2_3
import com.xfin.core.database.MIGRATION_3_4
import com.xfin.core.database.MIGRATION_4_5
import com.xfin.core.database.MIGRATION_5_6
import com.xfin.core.database.narrowFtsUpdateTriggers
import com.xfin.core.database.XfinDatabase
import com.xfin.core.database.dao.ChannelDao
import com.xfin.core.database.dao.ProgramDao
import com.xfin.core.database.dao.SourceDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): XfinDatabase =
        Room.databaseBuilder(context, XfinDatabase::class.java, "xfin.db")
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) = narrowFtsUpdateTriggers(db)
                },
            )
            .build()

    @Provides
    fun sourceDao(db: XfinDatabase): SourceDao = db.sourceDao()

    @Provides
    fun programDao(db: XfinDatabase): ProgramDao = db.programDao()

    @Provides
    fun channelDao(db: XfinDatabase): ChannelDao = db.channelDao()
}
