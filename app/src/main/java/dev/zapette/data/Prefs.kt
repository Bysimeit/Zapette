package dev.zapette.data

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("zapette", Context.MODE_PRIVATE)

    var account: Account?
        get() {
            val server = sp.getString("server", null) ?: return null
            val user = sp.getString("username", null) ?: return null
            val pass = sp.getString("password", null) ?: return null
            return Account(server, user, pass)
        }
        set(value) {
            sp.edit().apply {
                if (value == null) {
                    remove("server"); remove("username"); remove("password")
                } else {
                    putString("server", value.server)
                    putString("username", value.username)
                    putString("password", value.password)
                }
            }.apply()
        }

    var liveFormat: LiveFormat
        get() = runCatching { LiveFormat.valueOf(sp.getString("live_format", null) ?: "TS") }
            .getOrDefault(LiveFormat.TS)
        set(value) = sp.edit().putString("live_format", value.name).apply()

    var userAgent: String
        get() = sp.getString("user_agent", null)?.takeIf { it.isNotBlank() } ?: Http.DEFAULT_USER_AGENT
        set(value) = sp.edit().putString("user_agent", value).apply()

    var language: String?
        get() = sp.getString("language", null)
        set(value) = sp.edit().putString("language", value).apply()

    fun favorites(kind: Kind): Set<String> =
        sp.getStringSet("fav_${kind.name}", null)?.toSet() ?: emptySet()

    fun toggleFavorite(kind: Kind, id: String): Boolean {
        val current = favorites(kind).toMutableSet()
        val added = if (id in current) {
            current.remove(id); false
        } else {
            current.add(id); true
        }
        sp.edit().putStringSet("fav_${kind.name}", current).apply()
        return added
    }

    fun position(key: String): Long = sp.getLong("pos_$key", 0L)

    fun savePosition(key: String, positionMs: Long) =
        sp.edit().putLong("pos_$key", positionMs).apply()

    fun clearPosition(key: String) = sp.edit().remove("pos_$key").apply()
}
