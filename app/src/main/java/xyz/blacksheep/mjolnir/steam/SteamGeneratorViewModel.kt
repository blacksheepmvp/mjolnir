package xyz.blacksheep.mjolnir.steam

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import xyz.blacksheep.mjolnir.utils.GameInfo

class SteamGeneratorViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = mutableStateOf(restoreState())
    val uiState: State<SteamSearchState> get() = _uiState

    fun setIdle() = updateState(SteamSearchState.Idle)

    fun setLoading(appId: String) = updateState(SteamSearchState.Loading(appId))

    fun setSuccess(gameInfo: GameInfo) = updateState(SteamSearchState.Success(gameInfo))

    fun setFailure(error: String) = updateState(SteamSearchState.Failure(error))

    private fun updateState(state: SteamSearchState) {
        _uiState.value = state
        saveState(state)
    }

    private fun restoreState(): SteamSearchState {
        val type = savedStateHandle.get<String>(KEY_STATE_TYPE) ?: STATE_IDLE
        return when (type) {
            STATE_LOADING -> {
                val appId = savedStateHandle.get<String>(KEY_APP_ID) ?: ""
                SteamSearchState.Loading(appId)
            }
            STATE_SUCCESS -> {
                val appId = savedStateHandle.get<String>(KEY_APP_ID) ?: ""
                val name = savedStateHandle.get<String>(KEY_NAME) ?: ""
                val headerImage = savedStateHandle.get<String>(KEY_HEADER_IMAGE) ?: ""
                if (appId.isNotBlank() && name.isNotBlank()) {
                    SteamSearchState.Success(GameInfo(appId, name, headerImage))
                } else {
                    SteamSearchState.Idle
                }
            }
            STATE_FAILURE -> {
                val error = savedStateHandle.get<String>(KEY_ERROR) ?: "Unknown error"
                SteamSearchState.Failure(error)
            }
            else -> SteamSearchState.Idle
        }
    }

    private fun saveState(state: SteamSearchState) {
        when (state) {
            is SteamSearchState.Idle -> {
                savedStateHandle[KEY_STATE_TYPE] = STATE_IDLE
                savedStateHandle.remove<String>(KEY_APP_ID)
                savedStateHandle.remove<String>(KEY_NAME)
                savedStateHandle.remove<String>(KEY_HEADER_IMAGE)
                savedStateHandle.remove<String>(KEY_ERROR)
            }
            is SteamSearchState.Loading -> {
                savedStateHandle[KEY_STATE_TYPE] = STATE_LOADING
                savedStateHandle[KEY_APP_ID] = state.appId
                savedStateHandle.remove<String>(KEY_NAME)
                savedStateHandle.remove<String>(KEY_HEADER_IMAGE)
                savedStateHandle.remove<String>(KEY_ERROR)
            }
            is SteamSearchState.Success -> {
                savedStateHandle[KEY_STATE_TYPE] = STATE_SUCCESS
                savedStateHandle[KEY_APP_ID] = state.gameInfo.appId
                savedStateHandle[KEY_NAME] = state.gameInfo.name
                savedStateHandle[KEY_HEADER_IMAGE] = state.gameInfo.headerImage
                savedStateHandle.remove<String>(KEY_ERROR)
            }
            is SteamSearchState.Failure -> {
                savedStateHandle[KEY_STATE_TYPE] = STATE_FAILURE
                savedStateHandle[KEY_ERROR] = state.error
            }
        }
    }

    private companion object {
        const val KEY_STATE_TYPE = "steam_state_type"
        const val KEY_APP_ID = "steam_app_id"
        const val KEY_NAME = "steam_name"
        const val KEY_HEADER_IMAGE = "steam_header_image"
        const val KEY_ERROR = "steam_error"

        const val STATE_IDLE = "Idle"
        const val STATE_LOADING = "Loading"
        const val STATE_SUCCESS = "Success"
        const val STATE_FAILURE = "Failure"
    }
}
