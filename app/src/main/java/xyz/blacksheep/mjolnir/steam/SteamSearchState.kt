package xyz.blacksheep.mjolnir.steam

import xyz.blacksheep.mjolnir.utils.GameInfo

sealed interface SteamSearchState {
    object Idle : SteamSearchState
    data class Loading(val appId: String) : SteamSearchState
    data class Success(val gameInfo: GameInfo) : SteamSearchState
    data class Failure(val error: String) : SteamSearchState
}
