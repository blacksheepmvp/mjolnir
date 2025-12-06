package xyz.blacksheep.mjolnir.onboarding

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import xyz.blacksheep.mjolnir.KEY_BOTTOM_APP
import xyz.blacksheep.mjolnir.KEY_DOUBLE_HOME_ACTION
import xyz.blacksheep.mjolnir.KEY_DSS_AUTO_STITCH
import xyz.blacksheep.mjolnir.KEY_HOME_INTERCEPTION_ACTIVE
import xyz.blacksheep.mjolnir.KEY_LONG_HOME_ACTION
import xyz.blacksheep.mjolnir.KEY_SINGLE_HOME_ACTION
import xyz.blacksheep.mjolnir.KEY_TOP_APP
import xyz.blacksheep.mjolnir.KEY_TRIPLE_HOME_ACTION
import xyz.blacksheep.mjolnir.PREFS_NAME
import xyz.blacksheep.mjolnir.home.Action

/**
 * Holds the in-memory state for the onboarding flow.
 * All user selections are buffered here and only committed to SharedPreferences
 * at the end of the flow.
 */
data class OnboardingState(
    val topAppPackage: String? = null,
    val bottomAppPackage: String? = null,
    val homeInterceptionActive: Boolean = false,
    val singleHomeAction: Action = Action.BOTH_HOME,
    val doubleHomeAction: Action = Action.NONE,
    val tripleHomeAction: Action = Action.NONE,
    val longHomeAction: Action = Action.NONE,
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
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        fun getAction(key: String, default: Action): Action {
            return try {
                val name = prefs.getString(key, default.name)
                Action.valueOf(name!!)
            } catch (e: Exception) {
                default
            }
        }

        uiState.value = OnboardingState(
            topAppPackage = prefs.getString(KEY_TOP_APP, null),
            bottomAppPackage = prefs.getString(KEY_BOTTOM_APP, null),
            homeInterceptionActive = prefs.getBoolean(KEY_HOME_INTERCEPTION_ACTIVE, false),
            singleHomeAction = getAction(KEY_SINGLE_HOME_ACTION, Action.BOTH_HOME),
            doubleHomeAction = getAction(KEY_DOUBLE_HOME_ACTION, Action.NONE),
            tripleHomeAction = getAction(KEY_TRIPLE_HOME_ACTION, Action.NONE),
            longHomeAction = getAction(KEY_LONG_HOME_ACTION, Action.NONE),
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
    }

    fun setDssAutoStitch(enabled: Boolean) {
        uiState.value = uiState.value.copy(dssAutoStitch = enabled)
    }
}

enum class Gesture {
    SINGLE,
    DOUBLE,
    TRIPLE,
    LONG
}
