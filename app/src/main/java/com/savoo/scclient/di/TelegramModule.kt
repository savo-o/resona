package com.savoo.scclient.di

import com.savoo.scclient.telegram.TdLibTelegramClient
import com.savoo.scclient.telegram.TelegramClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** BETA: binds [TelegramClient] to the real TDLib-backed implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class TelegramModule {
    @Binds
    abstract fun bindTelegramClient(impl: TdLibTelegramClient): TelegramClient
}
