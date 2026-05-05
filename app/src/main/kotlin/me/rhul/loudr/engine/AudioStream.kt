package me.rhul.loudr.engine

/**
 * Represents the audio streams that Loudr can boost independently.
 * Each stream corresponds to an Android AudioManager stream type.
 */
enum class AudioStream(
    /** Human-readable label shown in the UI. */
    val label: String,
    /** Android AudioManager stream constant. */
    val streamType: Int,
) {
    MEDIA(
        label      = "Media",
        streamType = android.media.AudioManager.STREAM_MUSIC,
    ),
    CALL(
        label      = "Calls",
        streamType = android.media.AudioManager.STREAM_VOICE_CALL,
    ),
    NOTIFICATION(
        label      = "Notifications",
        streamType = android.media.AudioManager.STREAM_NOTIFICATION,
    ),
    ALARM(
        label      = "Alarms",
        streamType = android.media.AudioManager.STREAM_ALARM,
    );

    companion object {
        /** Default set of enabled streams on first launch. */
        val DEFAULT_ENABLED: Set<AudioStream> = setOf(MEDIA)
    }
}
