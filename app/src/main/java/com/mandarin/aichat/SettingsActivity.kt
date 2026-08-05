package com.mandarin.aichat

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.Slider

class SettingsActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "settings"
        const val KEY_THINKING_EFFORT = "thinking_effort"
        const val DEFAULT_THINKING_EFFORT = "low"

        private val VALUES = arrayOf("disabled", "low", "high", "xhigh", "max")

        fun getThinkingEffort(): String {
            val prefs = App.instance.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getString(KEY_THINKING_EFFORT, DEFAULT_THINKING_EFFORT)
                    ?: DEFAULT_THINKING_EFFORT
        }
    }

    private lateinit var slider: Slider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        slider = findViewById(R.id.slider_thinking_effort)

        val current = getThinkingEffort()
        slider.value = VALUES.indexOf(current).coerceIn(0, 4).toFloat()

        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                saveThinkingEffort(VALUES[value.toInt()])
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun saveThinkingEffort(value: String) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_THINKING_EFFORT, value)
                .apply()
    }
}
