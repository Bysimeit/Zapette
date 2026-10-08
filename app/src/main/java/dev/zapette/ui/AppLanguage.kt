package dev.zapette.ui

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import dev.zapette.data.Prefs
import java.util.Locale

object AppLanguage {
    val tags = listOf("en", "fr", "nl", "de")

    fun label(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
    }

    fun current(context: Context): String? =
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales
                .takeUnless { it.isEmpty }?.get(0)?.language
        } else {
            Prefs(context).language
        }

    fun set(activity: Activity, tag: String?) {
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            Prefs(activity).language = tag
            activity.recreate()
        }
    }

    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val locales = Prefs(base).language?.let { LocaleList(Locale.forLanguageTag(it)) }
            ?: Resources.getSystem().configuration.locales
        Locale.setDefault(locales[0])
        val config = Configuration(base.resources.configuration).apply { setLocales(locales) }
        base.applicationContext?.resources?.let {
            @Suppress("DEPRECATION")
            it.updateConfiguration(Configuration(it.configuration).apply { setLocales(locales) }, it.displayMetrics)
        }
        return base.createConfigurationContext(config)
    }
}
