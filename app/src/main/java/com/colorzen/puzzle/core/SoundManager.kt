package com.colorzen.puzzle.core

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.colorzen.puzzle.R

/**
 * The complete sound design of the game.
 *
 * There is deliberately NO music in Color Zen: no background track, no menu
 * music, no victory music. Only short, non-melodic sound effects. `COMPLETE` is
 * a brief two-note chime used as a success stinger, not a musical piece.
 *
 * Every effect is synthesised offline (see `tools/generate_sfx.py`) so the app
 * ships no third-party or licensed audio at all.
 */
enum class Sfx(val rawRes: Int, val defaultVolume: Float) {
    /** Liquid moving from one bottle to another. */
    POUR(R.raw.sfx_pour, 1.0f),

    /** A bottle was picked up / selected. */
    SELECT(R.raw.sfx_select, 0.9f),

    /** Level solved. */
    COMPLETE(R.raw.sfx_complete, 1.0f),

    /** Generic UI button press. */
    CLICK(R.raw.sfx_click, 0.7f),

    /** A hint was spent and revealed. */
    HINT(R.raw.sfx_hint, 0.9f),

    /** A bottle just became completely full. */
    BUBBLE(R.raw.sfx_bubble, 0.9f),
}

/**
 * Low-latency [SoundPool] playback with a settings-driven mute.
 *
 * Samples load asynchronously, so [play] silently drops a request whose sample
 * is not resident yet instead of playing nothing at the wrong time.
 */
class SoundManager(
    private val context: Context,
    private val store: PlayerStore,
) {

    private var pool: SoundPool? = null
    private val sampleIds = mutableMapOf<Sfx, Int>()
    private val loadedSamples = mutableSetOf<Int>()

    fun init() {
        if (pool != null) return
        try {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val soundPool = SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(attributes)
                .build()
            soundPool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == LOAD_SUCCESS) loadedSamples += sampleId
            }
            Sfx.entries.forEach { sfx ->
                sampleIds[sfx] = soundPool.load(context, sfx.rawRes, PRIORITY)
            }
            pool = soundPool
        } catch (error: RuntimeException) {
            // A device without audio must not crash the game.
            Log.w(TAG, "sound unavailable", error)
            pool = null
        }
    }

    /** Plays [sfx] unless the player turned sound effects off in Settings. */
    fun play(sfx: Sfx, volumeScale: Float = 1f) {
        if (!store.soundEffects.value) return
        val soundPool = pool ?: return
        val sampleId = sampleIds[sfx] ?: return
        if (sampleId !in loadedSamples) return
        val volume = (sfx.defaultVolume * volumeScale).coerceIn(0f, 1f)
        soundPool.play(sampleId, volume, volume, PRIORITY, NO_LOOP, PLAYBACK_RATE)
    }

    fun release() {
        pool?.release()
        pool = null
        sampleIds.clear()
        loadedSamples.clear()
    }

    private companion object {
        const val TAG = "ColorZenSound"
        const val MAX_STREAMS = 4
        const val PRIORITY = 1
        const val NO_LOOP = 0
        const val PLAYBACK_RATE = 1.0f
        const val LOAD_SUCCESS = 0
    }
}
