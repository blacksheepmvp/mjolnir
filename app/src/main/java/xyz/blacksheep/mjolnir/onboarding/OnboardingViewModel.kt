package xyz.blacksheep.mjolnir.onboarding

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import xyz.blacksheep.mjolnir.KEY_BOTTOM_APP
import xyz.blacksheep.mjolnir.KEY_ACTIVE_GESTURE_CONFIG
import xyz.blacksheep.mjolnir.KEY_DSS_AUTO_STITCH
import xyz.blacksheep.mjolnir.KEY_HOME_INTERCEPTION_ACTIVE
import xyz.blacksheep.mjolnir.KEY_TOP_APP
import xyz.blacksheep.mjolnir.home.Action
import xyz.blacksheep.mjolnir.settings.GestureConfigStore
import xyz.blacksheep.mjolnir.settings.settingsPrefs

/**
 * Holds the in-memory state for the onboarding flow.
 * All user selections are buffered here and only committed to SharedPreferences
 * at the end of the flow.
 */
data class OnboardingState(
    val topAppPackage: String? = null,
    val bottomAppPackage: String? = null,
    val homeInterceptionActive: Boolean = false,
    val gesturePresetFile: String = "type-a.cfg",
    val gesturePresetName: String = "Type-A",
    val singleHomeAction: Action = Action.FOCUS_AUTO,
    val doubleHomeAction: Action = Action.BOTH_HOME,
    val tripleHomeAction: Action = Action.APP_SWITCH,
    val longHomeAction: Action = Action.DEFAULT_HOME,
    val longPressDelayMs: Int = 0,
    val dssAutoStitch: Boolean = false,
)

/**
 * ViewModel for the onboarding process. It holds the buffered [OnboardingState]
 * and provides methods to update it.
 * 
 * It initializes itself from SharedPreferences to preserve existing configuration
 * if the user is re-running onboarding or switching modes.
 */
class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    var uiState = mutableStateOf(OnboardingState())
        private set

    init {
        initializeFromPrefs()
    }

    private fun initializeFromPrefs() {
        val prefs = getApplication<Application>().settingsPrefs()
        val config = GestureConfigStore.getActiveConfig(getApplication())

        uiState.value = OnboardingState(
            topAppPackage = prefs.getString(KEY_TOP_APP, null),
            bottomAppPackage = prefs.getString(KEY_BOTTOM_APP, null),
            homeInterceptionActive = prefs.getBoolean(KEY_HOME_INTERCEPTION_ACTIVE, false),
            gesturePresetFile = config.fileName,
            gesturePresetName = config.name,
            singleHomeAction = config.single,
            doubleHomeAction = config.double,
            tripleHomeAction = config.triple,
            longHomeAction = config.long,
            longPressDelayMs = config.longPressDelayMs,
            dssAutoStitch = prefs.getBoolean(KEY_DSS_AUTO_STITCH, false)
        )
    }

    fun setTopApp(pkg: String?) {
        uiState.value = uiState.value.copy(topAppPackage = pkg)
    }

    fun setBottomApp(pkg: String?) {
        uiState.value = uiState.value.copy(bottomAppPackage = pkg)
    }

    fun setHomeInterception(active: Boolean) {
        uiState.value = uiState.value.copy(homeInterceptionActive = active)
    }

    fun setGestureAction(gesture: Gesture, action: Action) {
        uiState.value = when (gesture) {
            Gesture.SINGLE -> uiState.value.copy(singleHomeAction = action)
            Gesture.DOUBLE -> uiState.value.copy(doubleHomeAction = action)
            Gesture.TRIPLE -> uiState.value.copy(tripleHomeAction = action)
            Gesture.LONG -> uiState.value.copy(longHomeAction = action)
        }
        persistGestureConfig()
    }

    fun setLongPressDelay(delayMs: Int) {
        uiState.value = uiState.value.copy(longPressDelayMs = delayMs)
        persistGestureConfig()
    }

    fun setGesturePreset(fileName: String) {
        GestureConfigStore.setActiveConfig(getApplication(), fileName)
        val loaded = GestureConfigStore.getActiveConfig(getApplication(), forceRefresh = true)
        uiState.value = uiState.value.copy(
            gesturePresetFile = loaded.fileName,
            gesturePresetName = loaded.name,
            singleHomeAction = loaded.single,
            doubleHomeAction = loaded.double,
            tripleHomeAction = loaded.triple,
            longHomeAction = loaded.long,
            longPressDelayMs = loaded.longPressDelayMs
        )
    }

    fun setDssAutoStitch(enabled: Boolean) {
        uiState.value = uiState.value.copy(dssAutoStitch = enabled)
    }

    private fun persistGestureConfig() {
        val state = uiState.value
        val config = GestureConfigStore.GestureConfig(
            fileName = state.gesturePresetFile,
            name = state.gesturePresetName,
            single = state.singleHomeAction,
            double = state.doubleHomeAction,
            triple = state.tripleHomeAction,
            long = state.longHomeAction,
            longPressDelayMs = state.longPressDelayMs
        )
        GestureConfigStore.saveConfig(getApplication(), config)
        getApplication<Application>().settingsPrefs()
            .edit()
            .putString(KEY_ACTIVE_GESTURE_CONFIG, state.gesturePresetFile)
            .apply()
    }
}

enum class Gesture {
    SINGLE,
    DOUBLE,
    TRIPLE,
    LONG
}
