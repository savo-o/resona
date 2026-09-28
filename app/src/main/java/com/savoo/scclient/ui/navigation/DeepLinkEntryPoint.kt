package com.savoo.scclient.ui.navigation

import com.savoo.scclient.data.remote.SoundCloudImportRepository
import com.savoo.scclient.data.repository.TrackRepository
import com.savoo.scclient.player.PlayerController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DeepLinkEntryPoint {
    fun soundCloudImportRepository(): SoundCloudImportRepository
    fun trackRepository(): TrackRepository
    fun playerController(): PlayerController
}
