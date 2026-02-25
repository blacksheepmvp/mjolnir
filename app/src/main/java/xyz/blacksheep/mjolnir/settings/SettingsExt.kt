package xyz.blacksheep.mjolnir.settings

import android.content.Context
import android.content.SharedPreferences
fun Context.settingsPrefs(): SharedPreferences = SettingsStore.prefs(this)
