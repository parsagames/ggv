package com.example.snappvoiceassistant

import android.content.Context
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * موتورهای صحبت‌کردن را یکی‌یکی امتحان می‌کند (اول SherpaTTS، بعد گوگل، بعد پیش‌فرض گوشی)
 * و اولین موتوری را که واقعاً صدای فارسی دارد انتخاب می‌کند.
 * کد زبان فارسی در موتورهای مختلف فرق دارد (fa / fas / per)، پس همهٔ آن‌ها چک می‌شود.
 */
class PersianTts(
    private val context: Context,
    private val onResult: (ready: Boolean) -> Unit
) {
    companion object {
        const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"
        const val SHERPA_TTS_PACKAGE = "org.woheller69.ttsengine"
        private val PERSIAN_LANGS = setOf("fa", "fas", "per")
    }

    var tts: TextToSpeech? = null
        private set

    private val candidates = mutableListOf<String?>()
    private var index = 0

    fun start() {
        candidates.clear()
        if (isInstalled(SHERPA_TTS_PACKAGE)) candidates.add(SHERPA_TTS_PACKAGE)
        if (isInstalled(GOOGLE_TTS_PACKAGE)) candidates.add(GOOGLE_TTS_PACKAGE)
        candidates.add(null) // موتور پیش‌فرض گوشی
        index = 0
        tryNext()
    }

    private fun tryNext() {
        tts?.shutdown()
        tts = null
        if (index >= candidates.size) {
            onResult(false)
            return
        }
        val engine = candidates[index++]
        tts = TextToSpeech(context, { status ->
            val t = tts
            if (status == TextToSpeech.SUCCESS && t != null && configurePersian(t)) {
                onResult(true)
            } else {
                tryNext()
            }
        }, engine)
    }

    private fun configurePersian(t: TextToSpeech): Boolean {
        // ۱) دنبال صدایی بگرد که زبانش فارسی باشد (با هر کد زبانی)
        try {
            val voice = t.voices?.firstOrNull { v ->
                v.locale.language.lowercase() in PERSIAN_LANGS &&
                        !v.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
            }
            if (voice != null) {
                t.voice = voice
                return true
            }
        } catch (e: Exception) {
            // ادامه با روش دوم
        }
        // ۲) روش دوم: امتحان کردن کدهای زبانِ ممکن
        val locales = listOf(
            Locale("fa", "IR"), Locale("fa"),
            Locale("fas", "IR"), Locale("fas"),
            Locale("per")
        )
        for (l in locales) {
            try {
                if (t.isLanguageAvailable(l) >= TextToSpeech.LANG_AVAILABLE) {
                    t.language = l
                    return true
                }
            } catch (e: Exception) {
                // این کد را رد کن
            }
        }
        return false
    }

    private fun isInstalled(pkg: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
