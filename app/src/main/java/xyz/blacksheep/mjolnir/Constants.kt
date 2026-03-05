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
const val KEY_ACTIVE_GESTURE_CONFIG = "ACTIVE_GESTURE_CONFIG"

const val KEY_USE_SYSTEM_DOUBLE_TAP_DELAY = "USE_SYSTEM_DOUBLE_TAP_DELAY"
const val KEY_CUSTOM_DOUBLE_TAP_DELAY = "CUSTOM_DOUBLE_TAP_DELAY"
const val KEY_AUTO_BOOT_BOTH_HOME = "autoBootBothHomeOnServiceStart"
const val KEY_BOTH_AUTO_NOTHING_TO_HOME = "both_auto_nothing_to_home"
const val KEY_TOP_BOTTOM_LAUNCH_DELAY_MS = "top_bottom_launch_delay_ms"
const val DEFAULT_TOP_BOTTOM_LAUNCH_DELAY_MS = 500
const val KEY_LAST_BOOT_COUNT = "last_boot_count"
const val KEY_LAST_BOOT_ELAPSED = "last_boot_elapsed"

// Key for app picker blacklist
const val KEY_APP_BLACKLIST = "APP_BLACKLIST"

const val KEY_ENABLE_FOCUS_LOCK_WORKAROUND = "enable_focus_lock_workaround"

const val KEY_DSS_SHARE_AFTER_CAPTURE = "dss_share_after_capture"
const val KEY_DSS_PROJECTION_DATA = "dss_projection_data"

/**
 * Boolean preference key for enabling rootless auto-stitch DSS.
 * If true, Mjolnir watches for system screenshots (Bottom) and triggers a Dual Shot.
 */
const val KEY_DSS_AUTO_STITCH = "dss_auto_stitch"

/**
 * Boolean preference key indicating if the onboarding flow has been completed or skipped.
 */
const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"

const val KEY_LAST_SEEN_VERSION = "last_seen_version"
const val KEY_LAST_SEEN_VERSION_CODE = "last_seen_version_code"
const val KEY_SAFETY_NET_PENDING = "safety_net_pending"

/**
 * Intent action for deleting the source (bottom) screenshot from the result notification.
 */
const val ACTION_DELETE_SOURCE = "xyz.blacksheep.mjolnir.ACTION_DELETE_SOURCE"

/**
 * Intent extra key for passing the source (bottom) screenshot URI to the service.
 */
const val EXTRA_SOURCE_URI = "xyz.blacksheep.mjolnir.EXTRA_SOURCE_URI"
