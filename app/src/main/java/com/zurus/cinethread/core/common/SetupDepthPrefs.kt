package com.zurus.cinethread.core.common

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Настройки стартового экрана игры (docs/debugging.md D5): глубина пары,
 * выбранная игроком до старта.
 */
interface SetupDepthPrefs {

    /** Выбранная глубина пары (2..4). */
    var selectedDepth: Int

    companion object {
        /** Минимальная глубина пары: два хода игрока. */
        const val MIN_DEPTH = 2

        /** Максимальная глубина пары в MVP (docs/debugging.md D5). */
        const val MAX_DEPTH = 4

        /** Глубина по умолчанию: самая дешёвая (0-1 запросы на пару). */
        const val DEFAULT_DEPTH = MIN_DEPTH
    }
}

/** [SetupDepthPrefs] поверх SharedPreferences (выбор сохраняется между запусками). */
class SharedPreferencesSetupDepthPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) : SetupDepthPrefs {

    override var selectedDepth: Int
        get() = prefs().getInt(KEY_DEPTH, SetupDepthPrefs.DEFAULT_DEPTH)
        set(value) {
            val normalized = value.coerceIn(SetupDepthPrefs.MIN_DEPTH, SetupDepthPrefs.MAX_DEPTH)
            prefs().edit().putInt(KEY_DEPTH, normalized).apply()
        }

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private companion object {
        const val PREFS_NAME = "cinethread_setup"
        const val KEY_DEPTH = "pair_depth"
    }
}
