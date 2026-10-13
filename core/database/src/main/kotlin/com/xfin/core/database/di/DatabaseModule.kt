package com.xfin.core.database.di

import android.content.Context
import androidx.room.Room
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
        Room.databaseBuilder(context, XfinDatabase::class.java, "xfin.db").build()

    @Provides
    fun sourceDao(db: XfinDatabase): SourceDao = db.sourceDao()

    @Provides
    fun programDao(db: XfinDatabase): ProgramDao = db.programDao()

    @Provides
    fun channelDao(db: XfinDatabase): ChannelDao = db.channelDao()
}
