package com.elyedali.makhzan.i18n

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.elyedali.makhzan.data.Prefs
import java.util.Locale

data class AppLanguage(val code: String, val nativeName: String, val rtl: Boolean)

/**
 * لغات واجهة التطبيق. النص العربي هو المفتاح نفسه، وكل لغة أخرى قاموس يترجمه.
 * لإضافة لغة جديدة: يكفي إضافة قاموس لها في Translations.kt وسطر هنا.
 */
object I18n {
    val languages = listOf(
        AppLanguage("ar", "العربية", rtl = true),
        AppLanguage("fr", "Français", rtl = false),
        AppLanguage("en", "English", rtl = false),
    )

    var lang by mutableStateOf("ar")
        private set

    val isRtl: Boolean get() = languages.firstOrNull { it.code == lang }?.rtl ?: true

    fun init(context: Context) {
        val saved = Prefs.language(context)
        lang = saved ?: Locale.getDefault().language.takeIf { l -> languages.any { it.code == l } } ?: "ar"
    }

    fun set(context: Context, code: String) {
        if (languages.none { it.code == code }) return
        lang = code
        Prefs.setLanguage(context, code)
    }

    internal fun lookup(key: String): String = when (lang) {
        "fr" -> Translations.fr[key]
        "en" -> Translations.en[key]
        else -> null
    } ?: key
}

/** يترجم النص العربي إلى لغة الواجهة الحالية. args تملأ %1$s و %2$s … */
fun tr(key: String, vararg args: Any?): String {
    val s = I18n.lookup(key)
    return if (args.isEmpty()) s else String.format(Locale.US, s, *args)
}
