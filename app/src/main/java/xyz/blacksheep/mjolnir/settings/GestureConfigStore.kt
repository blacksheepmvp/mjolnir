package xyz.blacksheep.mjolnir.settings

import android.content.Context
import android.util.Log
import android.view.ViewConfiguration
import xyz.blacksheep.mjolnir.home.Action
import xyz.blacksheep.mjolnir.KEY_ACTIVE_GESTURE_CONFIG
import xyz.blacksheep.mjolnir.settings.settingsPrefs
import java.io.File

object GestureConfigStore {
    private const val TAG = "GestureConfigStore"
    private const val DEFAULT_ACTIVE_FILE = "type-c.cfg"
    private const val DEFAULT_TYPE_A_NAME = "Type-A"
    private const val DEFAULT_TYPE_B_NAME = "Type-B"
    private const val DEFAULT_TYPE_C_NAME = "Type-C"
    private const val UNTITLED_PREFIX = "untitled"
    private val reservedFiles = setOf("type-a.cfg", "type-b.cfg", "type-c.cfg")

    data class GestureConfig(
        val fileName: String,
        val name: String,
        val single: Action,
        val double: Action,
        val triple: Action,
        val long: Action,
        val longPressDelayMs: Int
    )

    private data class CacheEntry(
        val config: GestureConfig,
        val lastModified: Long
    )

    @Volatile private var cachedActive: CacheEntry? = null
    @Volatile private var draftConfig: GestureConfig? = null

    fun ensureDefaults(context: Context) {
        val dir = gestureDir(context)
        if (!dir.exists()) dir.mkdirs()

        val legacyCustom = File(dir, "custom.cfg")
        if (legacyCustom.exists()) {
            val untitledBase = nextUntitledBase(context)
            val renamedCustom = File(dir, "$untitledBase.cfg")
            if (!renamedCustom.exists()) {
                legacyCustom.renameTo(renamedCustom)
                val loaded = readConfig(renamedCustom)
                if (loaded != null) {
                    writeConfig(context, loaded.copy(fileName = renamedCustom.name, name = untitledBase))
                }
            }
        }

        val existing = dir.listFiles { file -> file.isFile && file.extension == "cfg" }.orEmpty()
        if (existing.isEmpty()) {
            val prefs = context.settingsPrefs()
            val hasLegacy = prefs.contains(xyz.blacksheep.mjolnir.KEY_SINGLE_HOME_ACTION) ||
                prefs.contains(xyz.blacksheep.mjolnir.KEY_DOUBLE_HOME_ACTION) ||
                prefs.contains(xyz.blacksheep.mjolnir.KEY_TRIPLE_HOME_ACTION) ||
                prefs.contains(xyz.blacksheep.mjolnir.KEY_LONG_HOME_ACTION)
            if (hasLegacy) {
                createCustomFromLegacyPrefs(context)
                return
            }
        }

        val typeAFile = File(dir, "type-a.cfg")
        if (!typeAFile.exists()) {
            writeConfig(
                context,
                GestureConfig(
                    fileName = "type-a.cfg",
                    name = DEFAULT_TYPE_A_NAME,
                    single = Action.FOCUS_AUTO,
                    double = Action.BOTH_HOME,
                    triple = Action.APP_SWITCH,
                    long = Action.DEFAULT_HOME,
                    longPressDelayMs = ViewConfiguration.getLongPressTimeout()
                )
            )
        }

        val typeBFile = File(dir, "type-b.cfg")
        if (!typeBFile.exists()) {
            writeConfig(
                context,
                GestureConfig(
                    fileName = "type-b.cfg",
                    name = DEFAULT_TYPE_B_NAME,
                    single = Action.DEFAULT_HOME,
                    double = Action.BOTH_HOME_DEFAULT,
                    triple = Action.APP_SWITCH,
                    long = Action.FOCUS_AUTO,
                    longPressDelayMs = ViewConfiguration.getLongPressTimeout()
                )
            )
        }

        val typeCFile = File(dir, "type-c.cfg")
        if (!typeCFile.exists()) {
            writeConfig(
                context,
                GestureConfig(
                    fileName = "type-c.cfg",
                    name = DEFAULT_TYPE_C_NAME,
                    single = Action.BOTH_HOME,
                    double = Action.FOCUS_AUTO,
                    triple = Action.APP_SWITCH,
                    long = Action.BOTH_HOME_DEFAULT,
                    longPressDelayMs = ViewConfiguration.getLongPressTimeout()
                )
            )
        }
    }

    fun getActiveConfig(context: Context, forceRefresh: Boolean = false): GestureConfig {
        ensureDefaults(context)
        val prefs = context.settingsPrefs()
        var activeFile = prefs.getString(KEY_ACTIVE_GESTURE_CONFIG, DEFAULT_ACTIVE_FILE)

        if (activeFile == null && hasLegacyGesturePrefs(context)) {
            val migrated = createCustomFromLegacyPrefs(context)
            prefs.edit().putString(KEY_ACTIVE_GESTURE_CONFIG, migrated.fileName).apply()
            activeFile = migrated.fileName
        }

        if (activeFile == null) {
            activeFile = DEFAULT_ACTIVE_FILE
        }
        val config = loadConfig(context, activeFile, forceRefresh)
        if (config.fileName != activeFile) {
            prefs.edit().putString(KEY_ACTIVE_GESTURE_CONFIG, config.fileName).apply()
        }
        return config
    }

    fun setActiveConfig(context: Context, fileName: String) {
        val prefs = context.settingsPrefs()
        prefs.edit().putString(KEY_ACTIVE_GESTURE_CONFIG, fileName).apply()
        cachedActive = null
    }

    fun listConfigs(context: Context): List<GestureConfig> {
        ensureDefaults(context)
        val dir = gestureDir(context)
        val configs = dir.listFiles { file -> file.isFile && file.extension == "cfg" }
            ?.sortedBy { it.name.lowercase() }
            ?.mapNotNull { file -> readConfig(file) }
            ?: emptyList()
        return configs
    }

    fun saveConfig(context: Context, config: GestureConfig) {
        writeConfig(context, config)
        cachedActive = null
    }

    fun createCustomFromLegacyPrefs(context: Context): GestureConfig {
        val prefs = context.settingsPrefs()
        val untitledBase = nextUntitledBase(context)
        val config = GestureConfig(
            fileName = "$untitledBase.cfg",
            name = untitledBase,
            single = Action.valueOf(prefs.getString(xyz.blacksheep.mjolnir.KEY_SINGLE_HOME_ACTION, Action.FOCUS_AUTO.name)!!),
            double = Action.valueOf(prefs.getString(xyz.blacksheep.mjolnir.KEY_DOUBLE_HOME_ACTION, Action.BOTH_HOME.name)!!),
            triple = Action.valueOf(prefs.getString(xyz.blacksheep.mjolnir.KEY_TRIPLE_HOME_ACTION, Action.APP_SWITCH.name)!!),
            long = Action.valueOf(prefs.getString(xyz.blacksheep.mjolnir.KEY_LONG_HOME_ACTION, Action.DEFAULT_HOME.name)!!),
            longPressDelayMs = ViewConfiguration.getLongPressTimeout()
        )
        writeConfig(context, config)
        setActiveConfig(context, config.fileName)
        return config
    }

    private fun hasLegacyGesturePrefs(context: Context): Boolean {
        val prefs = context.settingsPrefs()
        return prefs.contains(xyz.blacksheep.mjolnir.KEY_SINGLE_HOME_ACTION) ||
            prefs.contains(xyz.blacksheep.mjolnir.KEY_DOUBLE_HOME_ACTION) ||
            prefs.contains(xyz.blacksheep.mjolnir.KEY_TRIPLE_HOME_ACTION) ||
            prefs.contains(xyz.blacksheep.mjolnir.KEY_LONG_HOME_ACTION)
    }

    fun createPresetFromActive(context: Context): GestureConfig {
        val active = getActiveConfig(context)
        val untitledBase = nextUntitledBase(context)
        val fileName = "$untitledBase.cfg"
        val config = active.copy(fileName = fileName, name = untitledBase)
        writeConfig(context, config)
        return config
    }

    fun duplicatePreset(context: Context, source: GestureConfig): GestureConfig {
        val draft = createDraftFromPreset(context, source)
        return draft
    }

    fun createDraftFromPreset(context: Context, source: GestureConfig): GestureConfig {
        val displayName = source.name.ifBlank { nextUntitledBase(context) }
        val stem = normalizeTitle(displayName).ifBlank { nextUntitledBase(context) }
        val config = source.copy(fileName = nextAvailableFileName(context, "$stem.cfg"), name = displayName)
        draftConfig = config
        return config
    }

    fun peekDraft(): GestureConfig? = draftConfig

    fun clearDraft() {
        draftConfig = null
    }

    fun saveDraft(context: Context, draft: GestureConfig, desiredName: String): GestureConfig {
        val displayName = desiredName.trim().ifBlank { nextUntitledBase(context) }
        val stem = normalizeTitle(displayName).ifBlank { nextUntitledBase(context) }
        val fileName = nextAvailableFileName(context, "$stem.cfg")
        val saved = draft.copy(fileName = fileName, name = displayName)
        writeConfig(context, saved)
        setActiveConfig(context, saved.fileName)
        draftConfig = null
        return saved
    }

    fun renamePreset(context: Context, config: GestureConfig, newDisplayName: String): GestureConfig {
        if (isReserved(config.fileName)) return config
        val displayName = newDisplayName.trim().ifBlank { nextUntitledBase(context) }
        val stem = normalizeTitle(displayName).ifBlank { nextUntitledBase(context) }
        val newFileName = nextAvailableFileName(context, "$stem.cfg")
        val dir = gestureDir(context)
        val oldFile = File(dir, config.fileName)
        val newFile = File(dir, newFileName)

        if (oldFile.exists() && oldFile.name != newFile.name) {
            oldFile.renameTo(newFile)
        }

        val updated = config.copy(fileName = newFile.name, name = displayName)
        writeConfig(context, updated)

        val prefs = context.settingsPrefs()
        val activeFile = prefs.getString(KEY_ACTIVE_GESTURE_CONFIG, DEFAULT_ACTIVE_FILE)
        if (activeFile == config.fileName) {
            prefs.edit().putString(KEY_ACTIVE_GESTURE_CONFIG, updated.fileName).apply()
        }

        cachedActive = null
        return updated
    }

    fun deletePreset(context: Context, fileName: String): Boolean {
        if (isReserved(fileName)) return false
        val dir = gestureDir(context)
        val target = File(dir, File(fileName).name)
        val deleted = if (target.exists()) target.delete() else false

        val prefs = context.settingsPrefs()
        val activeFile = prefs.getString(KEY_ACTIVE_GESTURE_CONFIG, DEFAULT_ACTIVE_FILE)
        if (activeFile == fileName) {
            setActiveConfig(context, DEFAULT_ACTIVE_FILE)
        }
        cachedActive = null
        return deleted
    }

    fun isReserved(fileName: String): Boolean {
        val normalized = File(fileName).name.lowercase()
        return reservedFiles.contains(normalized)
    }

    fun getConfigFile(context: Context, fileName: String): File {
        return File(gestureDir(context), File(fileName).name)
    }

    private fun loadConfig(context: Context, fileName: String, forceRefresh: Boolean = false): GestureConfig {
        val dir = gestureDir(context)
        val file = File(dir, fileName)
        if (!file.exists()) {
            return createFallbackConfig(context, fileName)
        }

        val cached = cachedActive
        if (!forceRefresh && cached != null && cached.config.fileName == fileName && cached.lastModified == file.lastModified()) {
            return cached.config
        }

        val config = readConfig(file) ?: createFallbackConfig(context, fileName)
        cachedActive = CacheEntry(config, file.lastModified())
        return config
    }

    private fun createFallbackConfig(context: Context, fileName: String): GestureConfig {
        val fallback = GestureConfig(
            fileName = fileName,
            name = fileName.removeSuffix(".cfg"),
            single = Action.FOCUS_AUTO,
            double = Action.BOTH_HOME,
            triple = Action.APP_SWITCH,
            long = Action.DEFAULT_HOME,
            longPressDelayMs = ViewConfiguration.getLongPressTimeout()
        )
        writeConfig(context, fallback)
        return fallback
    }

    private fun readConfig(file: File): GestureConfig? {
        return try {
            val lines = file.readLines()
            val map = mutableMapOf<String, String>()
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                if (trimmed.startsWith("#") || trimmed.startsWith(";")) continue
                val index = trimmed.indexOf('=')
                if (index <= 0) continue
                val key = trimmed.substring(0, index).trim().lowercase()
                val value = trimmed.substring(index + 1).trim()
                map[key] = value
            }

            val name = map["name"]?.ifBlank { null } ?: file.nameWithoutExtension
            val single = parseAction(map["single"], Action.FOCUS_AUTO)
            val double = parseAction(map["double"], Action.BOTH_HOME)
            val triple = parseAction(map["triple"], Action.APP_SWITCH)
            val long = parseAction(map["long"], Action.DEFAULT_HOME)
            val longPressDelay = parseDelay(map["long_press_delay_ms"], ViewConfiguration.getLongPressTimeout())

            GestureConfig(
                fileName = file.name,
                name = name,
                single = single,
                double = double,
                triple = triple,
                long = long,
                longPressDelayMs = longPressDelay
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read gesture config ${file.name}: ${e.message}")
            null
        }
    }

    private fun parseAction(value: String?, fallback: Action): Action {
        if (value.isNullOrBlank()) return fallback
        return try {
            Action.valueOf(value.trim().uppercase())
        } catch (e: Exception) {
            fallback
        }
    }

    private fun parseDelay(value: String?, fallback: Int): Int {
        if (value.isNullOrBlank()) return fallback
        val raw = value.trim().lowercase()
        val parsed = raw.toIntOrNull()
        if (parsed == null) return fallback
        if (parsed <= 0) return fallback
        return parsed
    }

    private fun writeConfig(context: Context, config: GestureConfig) {
        val dir = gestureDir(context)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, config.fileName)

        val actions = Action.values().joinToString(" | ") { it.name }
        val content = buildString {
            appendLine("# Mjolnir Gesture Preset")
            appendLine("#")
            appendLine("# name = Display name for this preset.")
            appendLine("# single/double/triple/long = Action enum names.")
            appendLine("# legal actions = $actions")
            appendLine("# long_press_delay_ms = Delay in milliseconds before Long Press is recognized.")
            appendLine("# long_press_delay_ms = 0 or missing uses the system default.")
            appendLine()
            appendLine("name=${config.name}")
            appendLine("single=${config.single.name}")
            appendLine("double=${config.double.name}")
            appendLine("triple=${config.triple.name}")
            appendLine("long=${config.long.name}")
            appendLine("long_press_delay_ms=${config.longPressDelayMs}")
        }
        atomicWrite(file, content)
    }

    private fun gestureDir(context: Context): File {
        val baseDir = context.getExternalFilesDir(null)?.parentFile ?: context.filesDir
        return File(baseDir, "gestures")
    }

    private fun atomicWrite(file: File, content: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(file)) {
            file.writeText(content)
        }
    }

    private fun nextAvailableFileName(context: Context, baseName: String): String {
        val dir = gestureDir(context)
        var candidate = baseName
        if (!candidate.endsWith(".cfg")) candidate = "$candidate.cfg"
        if (!File(dir, candidate).exists()) return candidate

        val stem = candidate.removeSuffix(".cfg")
        var index = 2
        while (true) {
            val next = "$stem-$index.cfg"
            if (!File(dir, next).exists()) return next
            index++
        }
    }

    private fun normalizeTitle(name: String): String {
        val cleaned = name
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        val truncated = cleaned.take(32)
        return truncated
    }

    private fun nextUntitledBase(context: Context?): String {
        val dir = context?.let { gestureDir(it) }
        var index = 1
        while (true) {
            val candidate = "$UNTITLED_PREFIX-$index"
            if (dir == null || !File(dir, "$candidate.cfg").exists()) return candidate
            index++
        }
    }
}
