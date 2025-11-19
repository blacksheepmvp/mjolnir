package xyz.blacksheep.mjolnir

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

var isHomeInterceptionActive by mutableStateOf(false)

const val PREFS_NAME = "mjolnir_prefs"
const val KEY_ROM_DIR_URI = "ROM_DIR_URI"
const val KEY_THEME = "THEME"
const val KEY_CONFIRM_DELETE = "CONFIRM_DELETE"
const val KEY_AUTO_CREATE_FILE = "AUTO_CREATE_FILE"
const val KEY_DEV_MODE = "DEV_MODE"
const val KEY_TOP_APP = "TOP_APP"
const val KEY_BOTTOM_APP = "BOTTOM_APP"
const val KEY_SHOW_ALL_APPS = "SHOW_ALL_APPS"
const val KEY_MAIN_SCREEN = "MAIN_SCREEN"
const val KEY_HOME_INTERCEPTION_ACTIVE = "HOME_INTERCEPTION_ACTIVE"
const val KEY_SWAP_SCREENS_REQUESTED = "SWAP_SCREENS_REQUESTED"
const val KEY_LAUNCH_FAILURE_COUNT = "LAUNCH_FAILURE_COUNT"

// New keys for gesture to action mapping
const val KEY_SINGLE_HOME_ACTION = "SINGLE_HOME_ACTION"
const val KEY_DOUBLE_HOME_ACTION = "DOUBLE_HOME_ACTION"
const val KEY_TRIPLE_HOME_ACTION = "TRIPLE_HOME_ACTION"
const val KEY_LONG_HOME_ACTION = "LONG_HOME_ACTION"

const val KEY_USE_SYSTEM_DOUBLE_TAP_DELAY = "USE_SYSTEM_DOUBLE_TAP_DELAY"
const val KEY_CUSTOM_DOUBLE_TAP_DELAY = "CUSTOM_DOUBLE_TAP_DELAY"
const val KEY_AUTO_BOOT_BOTH_HOME = "autoBootBothHomeOnServiceStart"

// Key for app picker blacklist
const val KEY_APP_BLACKLIST = "APP_BLACKLIST"
