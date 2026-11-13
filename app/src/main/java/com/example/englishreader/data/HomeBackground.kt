package com.example.englishreader.data

import android.content.Context

data class HomeBackground(
    val uri: String? = null,
    val opacity: Float = 1f,
    val cardAlpha: Float = 0.62f,
) {
    val hasImage: Boolean get() = !uri.isNullOrBlank()
}

class HomeBackgroundStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("home_background", Context.MODE_PRIVATE)

    fun load(): HomeBackground = HomeBackground(
        uri = prefs.getString(KEY_URI, null),
        opacity = prefs.getFloat(KEY_OPACITY, 1f).coerceIn(0f, 1f),
        cardAlpha = prefs.getFloat(KEY_CARD_ALPHA, 0.62f).coerceIn(MIN_CARD_ALPHA, 1f),
    )

    fun save(background: HomeBackground) {
        prefs.edit()
            .putString(KEY_URI, background.uri)
            .putFloat(KEY_OPACITY, background.opacity.coerceIn(0f, 1f))
            .putFloat(KEY_CARD_ALPHA, background.cardAlpha.coerceIn(MIN_CARD_ALPHA, 1f))
            .apply()
    }

    private companion object {
        const val KEY_URI = "uri"
        const val KEY_OPACITY = "opacity"
        const val KEY_CARD_ALPHA = "card_alpha"
        const val MIN_CARD_ALPHA = 0.2f
    }
}
