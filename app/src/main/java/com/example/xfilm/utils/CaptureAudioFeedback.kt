package com.example.xfilm.utils

import android.content.Context
import android.media.MediaActionSound

object CaptureAudioFeedback {

    private val sound = MediaActionSound()

    fun playShutterSound(context: Context) {
        try {
            sound.play(MediaActionSound.SHUTTER_CLICK)
        } catch (e: Exception) {
            // Silently fail if no sound available
        }
    }
}

