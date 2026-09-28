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

class MainActivity : AppCompatActivity() {

    private var ttsReady = false
    private lateinit var persianTts: PersianTts
    private lateinit var statusView: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnPermissions: Button
    private lateinit var btnOpenAccessibility: Button

    companion object {
        const val PREFS_NAME = "voice_assistant_prefs"
        const val KEY_ENABLED = "assistant_enabled"
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

        persianTts = PersianTts(this) { ready ->
            runOnUiThread {
                ttsReady = ready
                if (ready) {
                    btnStart.isEnabled = true
                    statusView.text = "آماده — دکمهٔ «شروع دستیار صوتی» را بزنید."
                } else {
                    btnStart.isEnabled = false
                    statusView.text =
                        "هیچ موتور صحبت‌کردن فارسی روی این گوشی پیدا نشد. صفحهٔ دانلود «SherpaTTS» " +
                                "(یک موتور رایگان و مستقل با صدای فارسی) باز می‌شود؛ لطفاً نصبش کنید و دوباره اپ را باز کنید."
                    openInstallPage(PersianTts.SHERPA_TTS_PACKAGE)
                }
            }
        }
        persianTts.start()

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

    private fun openInstallPage(packageNameToInstall: String) {
        val uris = if (packageNameToInstall == PersianTts.SHERPA_TTS_PACKAGE) {
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

    private fun speak(text: String) {
        if (ttsReady) {
            persianTts.tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "main_activity_utt")
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
        persianTts.shutdown()
        super.onDestroy()
    }
}
