package com.memorystream.ui.navigation

import androidx.lifecycle.ViewModel
import com.memorystream.audio.AudioPlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NavPlaybackHolder @Inject constructor(
    val playbackManager: AudioPlaybackManager
) : ViewModel()
