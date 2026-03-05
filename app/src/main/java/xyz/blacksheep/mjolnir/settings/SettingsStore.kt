package xyz.blacksheep.mjolnir.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import xyz.blacksheep.mjolnir.*
import xyz.blacksheep.mjolnir.home.Action
import xyz.blacksheep.mjolnir.utils.KEY_DIAGNOSTICS_ENABLED
import xyz.blacksheep.mjolnir.utils.KEY_DIAGNOSTICS_MAX_BYTES
import java.io.File
import java.io.StringReader
import java.util.Properties
import xyz.blacksheep.mjolnir.settings.settingsPrefs

object SettingsStore {
    private const val TAG = "SettingsStore"
    private const val SETTINGS_FILE = "settings.json"
    private const val CONFIG_FILE = "config.ini"
    private const val BLACKLIST_FILE = "blacklist.json"
    private const val MIGRATION_KEY = "migration_done"

    private enum class KeyType { STRING, BOOLEAN, INT, LONG, STRING_SET }

    private val userFacingKeys = setOf(
        KEY_ROM_DIR_URI,
        KEY_THEME,
        KEY_CONFIRM_DELETE,
        KEY_AUTO_CREATE_FILE,
        KEY_DEV_MODE,
        KEY_TOP_APP,
        KEY_BOTTOM_APP,
        KEY_SHOW_ALL_APPS,
        KEY_MAIN_SCREEN,
        KEY_HOME_INTERCEPTION_ACTIVE,
        KEY_SINGLE_HOME_ACTION,
        KEY_DOUBLE_HOME_ACTION,
        KEY_TRIPLE_HOME_ACTION,
        KEY_LONG_HOME_ACTION,
        KEY_ACTIVE_GESTURE_CONFIG,
        KEY_USE_SYSTEM_DOUBLE_TAP_DELAY,
        KEY_CUSTOM_DOUBLE_TAP_DELAY,
        KEY_DSS_AUTO_STITCH,
        KEY_DSS_SHARE_AFTER_CAPTURE,
        KEY_AUTO_BOOT_BOTH_HOME,
        KEY_BOTH_AUTO_NOTHING_TO_HOME,
        KEY_TOP_BOTTOM_LAUNCH_DELAY_MS
    )

    private val internalKeys = setOf(
        KEY_SWAP_SCREENS_REQUESTED,
        KEY_LAUNCH_FAILURE_COUNT,
        KEY_ENABLE_FOCUS_LOCK_WORKAROUND,
        KEY_DSS_PROJECTION_DATA,
        KEY_ONBOARDING_COMPLETE,
        KEY_LAST_SEEN_VERSION,
        KEY_LAST_SEEN_VERSION_CODE,
        KEY_SAFETY_NET_PENDING,
        KEY_LAST_BOOT_COUNT,
        KEY_LAST_BOOT_ELAPSED,
        KEY_DIAGNOSTICS_ENABLED,
        KEY_DIAGNOSTICS_MAX_BYTES
    )

    private val keyTypes = mapOf(
        KEY_ROM_DIR_URI to KeyType.STRING,
        KEY_THEME to KeyType.STRING,
        KEY_CONFIRM_DELETE to KeyType.BOOLEAN,
        KEY_AUTO_CREATE_FILE to KeyType.BOOLEAN,
        KEY_DEV_MODE to KeyType.BOOLEAN,
        KEY_TOP_APP to KeyType.STRING,
        KEY_BOTTOM_APP to KeyType.STRING,
        KEY_SHOW_ALL_APPS to KeyType.BOOLEAN,
        KEY_MAIN_SCREEN to KeyType.STRING,
        KEY_HOME_INTERCEPTION_ACTIVE to KeyType.BOOLEAN,
        KEY_SINGLE_HOME_ACTION to KeyType.STRING,
        KEY_DOUBLE_HOME_ACTION to KeyType.STRING,
        KEY_TRIPLE_HOME_ACTION to KeyType.STRING,
        KEY_LONG_HOME_ACTION to KeyType.STRING,
        KEY_ACTIVE_GESTURE_CONFIG to KeyType.STRING,
        KEY_USE_SYSTEM_DOUBLE_TAP_DELAY to KeyType.BOOLEAN,
        KEY_CUSTOM_DOUBLE_TAP_DELAY to KeyType.INT,
        KEY_DSS_AUTO_STITCH to KeyType.BOOLEAN,
        KEY_DSS_SHARE_AFTER_CAPTURE to KeyType.BOOLEAN,
        KEY_SWAP_SCREENS_REQUESTED to KeyType.BOOLEAN,
        KEY_LAUNCH_FAILURE_COUNT to KeyType.INT,
        KEY_AUTO_BOOT_BOTH_HOME to KeyType.BOOLEAN,
        KEY_BOTH_AUTO_NOTHING_TO_HOME to KeyType.BOOLEAN,
        KEY_TOP_BOTTOM_LAUNCH_DELAY_MS to KeyType.INT,
        KEY_ENABLE_FOCUS_LOCK_WORKAROUND to KeyType.BOOLEAN,
        KEY_DSS_PROJECTION_DATA to KeyType.STRING,
        KEY_ONBOARDING_COMPLETE to KeyType.BOOLEAN,
        KEY_LAST_SEEN_VERSION to KeyType.STRING,
        KEY_LAST_SEEN_VERSION_CODE to KeyType.LONG,
        KEY_SAFETY_NET_PENDING to KeyType.BOOLEAN,
        KEY_LAST_BOOT_COUNT to KeyType.INT,
        KEY_LAST_BOOT_ELAPSED to KeyType.LONG,
        KEY_DIAGNOSTICS_ENABLED to KeyType.BOOLEAN,
        KEY_DIAGNOSTICS_MAX_BYTES to KeyType.LONG,
        KEY_APP_BLACKLIST to KeyType.STRING_SET
    )

    private val actionValues = Action.values().joinToString(" | ") { it.name }

    private val settingsComments = linkedMapOf(
        KEY_ROM_DIR_URI to "ROM_DIR_URI: Content URI string for the ROMs directory.",
        KEY_THEME to "THEME: LIGHT | DARK | SYSTEM.",
        KEY_CONFIRM_DELETE to "CONFIRM_DELETE: true | false.",
        KEY_AUTO_CREATE_FILE to "AUTO_CREATE_FILE: true | false.",
        KEY_DEV_MODE to "DEV_MODE: true | false.",
        KEY_TOP_APP to "TOP_APP: package name string or empty.",
        KEY_BOTTOM_APP to "BOTTOM_APP: package name string or empty.",
        KEY_SHOW_ALL_APPS to "SHOW_ALL_APPS: true | false.",
        KEY_MAIN_SCREEN to "MAIN_SCREEN: TOP | BOTTOM.",
        KEY_HOME_INTERCEPTION_ACTIVE to "HOME_INTERCEPTION_ACTIVE: true | false.",
        KEY_SINGLE_HOME_ACTION to "SINGLE_HOME_ACTION (legacy): $actionValues.",
        KEY_DOUBLE_HOME_ACTION to "DOUBLE_HOME_ACTION (legacy): $actionValues.",
        KEY_TRIPLE_HOME_ACTION to "TRIPLE_HOME_ACTION (legacy): $actionValues.",
        KEY_LONG_HOME_ACTION to "LONG_HOME_ACTION (legacy): $actionValues.",
        KEY_ACTIVE_GESTURE_CONFIG to "ACTIVE_GESTURE_CONFIG: gesture preset filename in /gestures (e.g. type-a.cfg).",
        KEY_USE_SYSTEM_DOUBLE_TAP_DELAY to "USE_SYSTEM_DOUBLE_TAP_DELAY: true | false.",
        KEY_CUSTOM_DOUBLE_TAP_DELAY to "CUSTOM_DOUBLE_TAP_DELAY: integer milliseconds.",
        KEY_DSS_AUTO_STITCH to "DSS_AUTO_STITCH: true | false.",
        KEY_DSS_SHARE_AFTER_CAPTURE to "DSS_SHARE_AFTER_CAPTURE: true | false.",
        KEY_AUTO_BOOT_BOTH_HOME to "START_ON_BOOT_AUTO (Advanced only): true = BOTH: Auto, false = BOTH: Home.",
        KEY_BOTH_AUTO_NOTHING_TO_HOME to "BOTH_AUTO_NOTHING_TO_HOME: true = empty slot launches Home, false = empty slot does nothing.",
        KEY_TOP_BOTTOM_LAUNCH_DELAY_MS to "TOP_BOTTOM_LAUNCH_DELAY_MS: integer milliseconds (0-500). Delay between sequenced top/bottom launches."
    )

    private val configComments = linkedMapOf(
        KEY_SWAP_SCREENS_REQUESTED to "SWAP_SCREENS_REQUESTED: true | false.",
        KEY_LAUNCH_FAILURE_COUNT to "LAUNCH_FAILURE_COUNT: integer.",
        KEY_ENABLE_FOCUS_LOCK_WORKAROUND to "enable_focus_lock_workaround: true | false.",
        KEY_DSS_PROJECTION_DATA to "dss_projection_data: internal JSON string.",
        KEY_ONBOARDING_COMPLETE to "onboarding_complete: true | false.",
        KEY_LAST_SEEN_VERSION to "last_seen_version: version string.",
        KEY_LAST_SEEN_VERSION_CODE to "last_seen_version_code: version code (long).",
        KEY_SAFETY_NET_PENDING to "safety_net_pending: true | false.",
        KEY_LAST_BOOT_COUNT to "last_boot_count: internal boot counter snapshot.",
        KEY_LAST_BOOT_ELAPSED to "last_boot_elapsed: internal uptime snapshot.",
        KEY_DIAGNOSTICS_ENABLED to "diagnostics_enabled: true | false.",
        KEY_DIAGNOSTICS_MAX_BYTES to "diagnostics_max_bytes: integer bytes.",
        MIGRATION_KEY to "migration_done: true | false."
    )

    private data class State(
        val values: MutableMap<String, Any?>,
        val blacklist: MutableSet<String>,
        var migrationDone: Boolean
    )

    @Volatile private var cachedState: State? = null
    private val lock = Any()

    fun prefs(context: Context): SharedPreferences = FileBackedPreferences.get(context.applicationContext)

    fun regenerateFiles(context: Context) {
        synchronized(lock) {
            val state = loadState(context)
            writeAll(context, state)
        }
    }

    internal fun getValue(context: Context, key: String): Any? {
        synchronized(lock) {
            val state = loadState(context)
            if (key == KEY_APP_BLACKLIST) return state.blacklist
            return state.values[key]
        }
    }

    internal fun getAll(context: Context): Map<String, *> {
        synchronized(lock) {
            val state = loadState(context)
            val map = state.values.toMutableMap()
            map[KEY_APP_BLACKLIST] = state.blacklist.toSet()
            return map
        }
    }

    internal fun applyEdits(context: Context, edits: Map<String, Any?>, removals: Set<String>, clear: Boolean) {
        synchronized(lock) {
            val state = loadState(context)
            if (clear) {
                state.values.clear()
                state.blacklist.clear()
            }
            removals.forEach {
                if (it == KEY_APP_BLACKLIST) {
                    state.blacklist.clear()
                } else {
                    state.values.remove(it)
                }
            }
            edits.forEach { (key, value) ->
                if (key == KEY_APP_BLACKLIST) {
                    state.blacklist.clear()
                    @Suppress("UNCHECKED_CAST")
                    state.blacklist.addAll(
                        (value as? Set<String>).orEmpty().filterNot { it == context.packageName }
                    )
                } else {
                    state.values[key] = value
                }
            }
            writeAll(context, state)
        }
    }

    private fun loadState(context: Context): State {
        cachedState?.let { return it }
        val baseDir = baseDir(context)
        val settingsFile = File(baseDir, SETTINGS_FILE)
        val configFile = File(baseDir, CONFIG_FILE)
        val blacklistFile = File(baseDir, BLACKLIST_FILE)

        val values = mutableMapOf<String, Any?>()
        val blacklist = mutableSetOf<String>()
        var migrationDone = false

        if (settingsFile.exists() || configFile.exists() || blacklistFile.exists()) {
            values.putAll(readJson(settingsFile))
            val iniValues = readIni(configFile)
            values.putAll(iniValues)
            blacklist.addAll(readBlacklist(blacklistFile))
            val migrationValue = iniValues[MIGRATION_KEY]?.toString()?.lowercase()
            migrationDone = migrationValue == "true"
        }

        if (!migrationDone) {
            migrateFromPrefs(context, values, blacklist)
            migrationDone = true
        }

        val state = State(values, blacklist, migrationDone)
        writeAll(context, state)
        cachedState = state
        return state
    }

    private fun migrateFromPrefs(context: Context, values: MutableMap<String, Any?>, blacklist: MutableSet<String>) {
        val legacy = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        keyTypes.keys.forEach { key ->
            if (key == KEY_APP_BLACKLIST) return@forEach
            if (!legacy.contains(key)) return@forEach
            val type = keyTypes[key]
            val value: Any? = when (type) {
                KeyType.STRING -> legacy.getString(key, null)
                KeyType.BOOLEAN -> legacy.getBoolean(key, false)
                KeyType.INT -> legacy.getInt(key, 0)
                KeyType.LONG -> legacy.getLong(key, 0L)
                KeyType.STRING_SET -> legacy.getStringSet(key, emptySet())
                null -> null
            }
            if (value != null) values[key] = value
        }
        if (legacy.contains(KEY_APP_BLACKLIST)) {
            val legacyBlacklist = legacy.getStringSet(KEY_APP_BLACKLIST, emptySet()) ?: emptySet()
            blacklist.addAll(legacyBlacklist)
        } else {
            blacklist.addAll(
                setOf(
                    "com.android.settings"
                )
            )
        }
    }

    private fun readJson(file: File): Map<String, Any?> {
        if (!file.exists()) return emptyMap()
        return try {
            val json = JSONObject(file.readText())
            val map = mutableMapOf<String, Any?>()
            json.keys().forEach { key ->
                if (!userFacingKeys.contains(key)) return@forEach
                val type = keyTypes[key]
                val value: Any? = when (type) {
                    KeyType.STRING -> if (json.isNull(key)) null else json.optString(key, "")
                    KeyType.BOOLEAN -> json.optBoolean(key, false)
                    KeyType.INT -> json.optInt(key, 0)
                    KeyType.LONG -> json.optLong(key, 0L)
                    else -> json.opt(key)
                }
                if (value != null) map[key] = value
            }
            map
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read settings.json: ${e.message}")
            emptyMap()
        }
    }

    private fun readIni(file: File): Map<String, Any?> {
        if (!file.exists()) return emptyMap()
        return try {
            val props = Properties()
            props.load(StringReader(file.readText()))
            val map = mutableMapOf<String, Any?>()
            props.stringPropertyNames().forEach { key ->
                val type = keyTypes[key]
                val raw = props.getProperty(key)
                val value: Any? = when (type) {
                    KeyType.STRING -> raw
                    KeyType.BOOLEAN -> raw.toBooleanStrictOrNull() ?: false
                    KeyType.INT -> raw.toIntOrNull() ?: 0
                    KeyType.LONG -> raw.toLongOrNull() ?: 0L
                    else -> raw
                }
                map[key] = value
            }
            map
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read config.ini: ${e.message}")
            emptyMap()
        }
    }

    private fun readBlacklist(file: File): Set<String> {
        if (!file.exists()) return emptySet()
        return try {
            val root = JSONTokener(file.readText()).nextValue()
            val set = mutableSetOf<String>()
            when (root) {
                is JSONArray -> {
                    for (i in 0 until root.length()) {
                        val value = root.optString(i, null) ?: continue
                        set.add(value)
                    }
                }
                is JSONObject -> {
                    val arr = root.optJSONArray("packages") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val value = arr.optString(i, null) ?: continue
                        set.add(value)
                    }
                }
            }
            set
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read blacklist.json: ${e.message}")
            emptySet()
        }
    }

    private fun writeAll(context: Context, state: State) {
        val baseDir = baseDir(context)
        baseDir.mkdirs()
        writeJson(File(baseDir, SETTINGS_FILE), state.values)
        writeIni(File(baseDir, CONFIG_FILE), state.values, state.migrationDone)
        writeBlacklist(File(baseDir, BLACKLIST_FILE), state.blacklist, context.packageName)
    }

    private fun writeJson(file: File, values: Map<String, Any?>) {
        val json = JSONObject()
        val header = JSONArray()
        header.put("Mjolnir settings.json")
        header.put("This file is human-editable. Keys are listed below with legal values.")
        header.put("Unknown keys are ignored. Remove keys to reset to defaults.")
        json.put("_comment", header)
        settingsComments.forEach { (key, comment) ->
            json.put("_comment_$key", comment)
        }
        userFacingKeys.forEach { key ->
            val value = values[key] ?: return@forEach
            json.put(key, value)
        }
        atomicWrite(file, json.toString(2))
    }

    private fun writeIni(file: File, values: Map<String, Any?>, migrationDone: Boolean) {
        val content = buildString {
            appendLine("# Mjolnir config.ini")
            appendLine("# Internal settings. Edit only if you know what you're doing.")
            appendLine("# Keys and legal values:")
            configComments.forEach { (_, comment) ->
                appendLine("# $comment")
            }
            appendLine()

            internalKeys.forEach { key ->
                val value = values[key] ?: return@forEach
                appendLine("$key=$value")
            }
            appendLine("$MIGRATION_KEY=$migrationDone")
        }
        atomicWrite(file, content)
    }

    private fun writeBlacklist(file: File, blacklist: Set<String>, forcedPackage: String) {
        val json = JSONObject()
        val header = JSONArray()
        header.put("Mjolnir app blacklist")
        header.put("Package names listed here will be hidden from app pickers.")
        header.put("Example: com.example.app")
        json.put("_comment", header)
        val arr = JSONArray()
        blacklist
            .filterNot { it == forcedPackage }
            .forEach { arr.put(it) }
        json.put("packages", arr)
        atomicWrite(file, json.toString(2))
    }

    private fun atomicWrite(file: File, content: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(file)) {
            file.writeText(content)
        }
    }

    private fun baseDir(context: Context): File {
        val externalBase = context.getExternalFilesDir(null)?.parentFile
        return externalBase ?: context.filesDir
    }
}

private class FileBackedPreferences private constructor(private val context: Context) : SharedPreferences {
    companion object {
        @Volatile private var instance: FileBackedPreferences? = null
        fun get(context: Context): FileBackedPreferences {
            return instance ?: synchronized(this) {
                instance ?: FileBackedPreferences(context).also { instance = it }
            }
        }
    }

    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): Map<String, *> = SettingsStore.getAll(context)

    override fun getString(key: String?, defValue: String?): String? {
        if (key == null) return defValue
        return (SettingsStore.getValue(context, key) as? String) ?: defValue
    }

    override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? {
        if (key == null) return defValues
        @Suppress("UNCHECKED_CAST")
        return (SettingsStore.getValue(context, key) as? Set<String>) ?: defValues
    }

    override fun getInt(key: String?, defValue: Int): Int {
        if (key == null) return defValue
        return (SettingsStore.getValue(context, key) as? Int) ?: defValue
    }

    override fun getLong(key: String?, defValue: Long): Long {
        if (key == null) return defValue
        return (SettingsStore.getValue(context, key) as? Long) ?: defValue
    }

    override fun getFloat(key: String?, defValue: Float): Float = defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        if (key == null) return defValue
        return (SettingsStore.getValue(context, key) as? Boolean) ?: defValue
    }

    override fun contains(key: String?): Boolean {
        if (key == null) return false
        return SettingsStore.getAll(context).containsKey(key)
    }

    override fun edit(): SharedPreferences.Editor = EditorImpl(context, listeners)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        if (listener != null) listeners.add(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        if (listener != null) listeners.remove(listener)
    }

    private class EditorImpl(
        private val context: Context,
        private val listeners: Set<SharedPreferences.OnSharedPreferenceChangeListener>
    ) : SharedPreferences.Editor {
        private val edits = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = applyEdit(key, value)
        override fun putStringSet(key: String?, values: Set<String>?): SharedPreferences.Editor = applyEdit(key, values)
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = applyEdit(key, value)
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = applyEdit(key, value)
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = applyEdit(key, value)
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = applyEdit(key, value)

        private fun applyEdit(key: String?, value: Any?): SharedPreferences.Editor {
            if (key == null) return this
            edits[key] = value
            removals.remove(key)
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) removals.add(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            SettingsStore.applyEdits(context, edits, removals, clear)
            val changedKeys = edits.keys + removals
            changedKeys.forEach { key ->
                listeners.forEach { it.onSharedPreferenceChanged(null, key) }
            }
        }
    }
}
