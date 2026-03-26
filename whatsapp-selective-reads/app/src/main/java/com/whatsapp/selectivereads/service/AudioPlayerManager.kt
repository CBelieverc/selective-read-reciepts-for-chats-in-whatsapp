package com.whatsapp.selectivereads.service

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.IOException

class AudioPlayerManager {

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var currentFilePath: String? = null
    private var onProgressUpdate: ((Int, Int) -> Unit)? = null
    private var onCompletion: (() -> Unit)? = null
    private var isPlaying = false

    companion object {
        private var instance: AudioPlayerManager? = null

        fun getInstance(): AudioPlayerManager {
            if (instance == null) {
                instance = AudioPlayerManager()
            }
            return instance!!
        }
    }

    fun play(
        filePath: String,
        onProgress: (currentMs: Int, totalMs: Int) -> Unit,
        onComplete: () -> Unit
    ) {
        if (currentFilePath == filePath && isPlaying) {
            pause()
            return
        }

        stop()

        val file = File(filePath)
        if (!file.exists()) {
            onComplete()
            return
        }

        onProgressUpdate = onProgress
        onCompletion = onComplete
        currentFilePath = filePath

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                setOnCompletionListener {
                    isPlaying = false
                    currentFilePath = null
                    onComplete()
                    handler.removeCallbacksAndMessages(null)
                }
                start()
                isPlaying = true
                startProgressTracking()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            onComplete()
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isPlaying = false
                handler.removeCallbacksAndMessages(null)
            }
        }
    }

    fun resume() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                isPlaying = true
                startProgressTracking()
            }
        }
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            // ignore
        }
        mediaPlayer = null
        isPlaying = false
        currentFilePath = null
    }

    fun seekTo(ms: Int) {
        mediaPlayer?.seekTo(ms)
    }

    fun isPlayingFile(filePath: String): Boolean {
        return currentFilePath == filePath && isPlaying
    }

    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0
    fun getDuration(): Int = mediaPlayer?.duration ?: 0

    private fun startProgressTracking() {
        handler.post(object : Runnable {
            override fun run() {
                try {
                    mediaPlayer?.let {
                        if (it.isPlaying) {
                            onProgressUpdate?.invoke(it.currentPosition, it.duration)
                            handler.postDelayed(this, 100)
                        }
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }
        })
    }

    fun release() {
        stop()
        instance = null
    }
}
