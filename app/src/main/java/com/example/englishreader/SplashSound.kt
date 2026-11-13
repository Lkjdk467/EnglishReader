package com.example.englishreader

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri

/**
 * 开场动画配乐。App 启动时就开始 prepare，动画第一帧只负责 start，
 * 不会在读文件上卡住首帧。动画结束、跳过或页面销毁时都要 release。
 */
class SplashSound(context: Context) {
    private val player = MediaPlayer()
    private var prepared = false
    private var playWhenReady = false
    private var paused = false
    private var released = false

    init {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        player.setDataSource(
            context,
            Uri.parse("android.resource://${context.packageName}/${R.raw.splash_intro}"),
        )
        player.setOnPreparedListener { media ->
            prepared = true
            if (playWhenReady && !paused) media.start()
        }
        player.setOnErrorListener { _, _, _ ->
            release()
            true
        }
        player.prepareAsync()
    }

    /** 动画开始时调用；若还没 prepare 完，会在 prepare 完成后自动开始。 */
    fun play() {
        if (released) return
        playWhenReady = true
        paused = false
        if (prepared) player.start()
    }

    /** 切到后台时暂停。 */
    fun pause() {
        if (released) return
        paused = true
        if (prepared && player.isPlaying) player.pause()
    }

    /** 回到前台时继续，前提是动画还没结束。 */
    fun resume() {
        if (released || !paused) return
        paused = false
        if (prepared && playWhenReady) player.start()
    }

    /** 停止并释放音频资源，可重复调用。 */
    fun release() {
        if (released) return
        released = true
        playWhenReady = false
        player.setOnPreparedListener(null)
        player.setOnErrorListener(null)
        runCatching { player.stop() }
        player.release()
    }
}
