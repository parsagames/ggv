package com.example.snappvoiceassistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var ttsReady = false
    private lateinit var statusView: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnPermissions: Button
    private lateinit var btnOpenAccessibility: Button

    companion object {
        const val PREFS_NAME = "voice_assistant_prefs"
        const val KEY_ENABLED = "assistant_enabled"
        const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"
        const val SHERPA_TTS_PACKAGE = "org.woheller69.ttsengine"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusView = findViewById(R.id.statusText)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnPermissions = findViewById(R.id.btnPermissions)
        btnOpenAccessibility = findViewById(R.id.btnOpenAccessibility)

        // تا وقتی موتور صحبت‌کردن واقعاً آماده نشده، دکمهٔ شروع را غیرفعال می‌کنیم
        // تا کاربر هرگز روی «دکمه‌ای که سکوت می‌کند» کلیک نکند.
        btnStart.isEnabled = false
        statusView.text = "در حال آماده‌سازی موتور صحبت‌کردن..."

        initTts()

        btnPermissions.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
            } else {
                statusView.text = "مجوز میکروفون از قبل داده شده است."
            }
        }

        btnOpenAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnStart.setOnClickListener {
            setAssistantEnabled(true)
            speak("دستیار صوتی فعال شد")
            statusView.text = "دستیار صوتی فعال است"
        }

        btnStop.setOnClickListener {
            setAssistantEnabled(false)
            speak("دستیار صوتی غیر فعال شد")
            statusView.text = "دستیار صوتی غیر فعال است"
        }
    }

    /**
     * ابتدا موتور «گفتار گوگل» را امتحان می‌کنیم؛ اگر نصب نبود (یا فارسی نداشت)،
     * سراغ موتور متن‌باز و مستقل «SherpaTTS» می‌رویم که صدای فارسی واقعی دارد
     * و به حساب/منطقهٔ گوگل وابسته نیست. در نهایت اگر هیچ‌کدام نبود، کاربر را
     * برای نصب یکی از این دو راهنمایی می‌کنیم.
     */
    private fun initTts() {
        when {
            isPackageInstalled(GOOGLE_TTS_PACKAGE) -> {
                tts = TextToSpeech(this, this, GOOGLE_TTS_PACKAGE)
            }
            isPackageInstalled(SHERPA_TTS_PACKAGE) -> {
                tts = TextToSpeech(this, this, SHERPA_TTS_PACKAGE)
            }
            else -> {
                statusView.text =
                    "هیچ موتور صحبت‌کردن فارسی روی این گوشی پیدا نشد. صفحهٔ دانلود «SherpaTTS» " +
                            "(یک موتور رایگان و مستقل با صدای فارسی) باز می‌شود؛ لطفاً نصبش کنید و دوباره اپ را باز کنید."
                openInstallPage(SHERPA_TTS_PACKAGE)
                tts = TextToSpeech(this, this) // موتور پیش‌فرض به‌عنوان جایگزین موقت
            }
        }
    }

    private fun isPackageInstalled(packageNameToCheck: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageNameToCheck, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun openInstallPage(packageNameToInstall: String) {
        val uris = if (packageNameToInstall == SHERPA_TTS_PACKAGE) {
            // SherpaTTS در گوگل‌پلی/بازار نیست؛ مستقیم از F-Droid دانلودش می‌کنیم
            listOf("https://f-droid.org/repo/org.woheller69.ttsengine_34.apk")
        } else {
            listOf(
                "market://details?id=$packageNameToInstall",
                "bazaar://details?id=$packageNameToInstall",
                "https://play.google.com/store/apps/details?id=$packageNameToInstall"
            )
        }
        for (uri in uris) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                return
            } catch (e: Exception) {
                // این راه کار نکرد، راه بعدی را امتحان کن
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val faLocale = Locale("fa", "IR")
            val result = tts.isLanguageAvailable(faLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // بستهٔ زبان فارسی روی موتور تبدیل‌متن‌به‌گفتار گوشی نصب نیست؛
                // صفحهٔ نصب بستهٔ زبان را باز می‌کنیم تا کاربر آن را دانلود کند.
                statusView.text =
                    "بستهٔ زبان فارسی برای صحبت‌کردن نصب نیست. صفحه‌ای برای نصب آن باز می‌شود؛ لطفاً «فارسی» را دانلود و نصب کنید و دوباره اپ را باز کنید."
                btnStart.isEnabled = false
                try {
                    startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                } catch (e: Exception) {
                    // اگر موتور TTS گوشی صفحهٔ نصب نداشت، کاربر را به تنظیمات راهنمایی می‌کنیم
                    statusView.text =
                        "برای نصب زبان فارسی به: تنظیمات > سیستم > زبان‌ها و ورودی > خروجی تبدیل متن به گفتار، بروید، موتور را روی «گفتار گوگل» بگذارید و بستهٔ فارسی را نصب کنید."
                }
            } else {
                tts.language = faLocale
                ttsReady = true
                btnStart.isEnabled = true
                statusView.text = "آماده — دکمهٔ «شروع دستیار صوتی» را بزنید."
            }
        } else {
            btnStart.isEnabled = false
            statusView.text = "راه‌اندازی موتور صحبت‌کردن ناموفق بود."
        }
    }

    private fun speak(text: String) {
        if (ttsReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "main_activity_utt")
        }
    }

    private fun setAssistantEnabled(enabled: Boolean) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = "$packageName/.OrderAccessibilityService"
        val enabledServicesSetting = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServicesSetting.contains(expectedComponentName)
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}
