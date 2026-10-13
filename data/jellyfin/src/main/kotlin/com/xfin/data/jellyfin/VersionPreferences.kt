package com.xfin.data.jellyfin

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Remembers which version (media source) the user picked for each title. */
@Singleton
class VersionPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("jellyfin_versions", Context.MODE_PRIVATE)

    fun get(itemId: String): String? = prefs.getString(itemId, null)

    fun set(itemId: String, mediaSourceId: String) {
        prefs.edit().putString(itemId, mediaSourceId).apply()
    }
}
