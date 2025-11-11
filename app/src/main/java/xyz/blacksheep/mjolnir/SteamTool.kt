package xyz.blacksheep.mjolnir

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Data class representing the essential information for a Steam game.
 */
data class GameInfo(val appId: String, val name: String, val headerImage: String)

/**
 * Data class to hold information about a file overwrite operation.
 */
data class OverwriteInfo(val gameInfo: GameInfo, val oldAppId: String)

/**
 * A sealed interface to represent the different states of the game search UI.
 */
sealed interface UiState {
    object Idle : UiState
    data class Loading(val appId: String) : UiState
    data class Success(val gameInfo: GameInfo) : UiState
    data class Failure(val error: String) : UiState
}

object SteamTool {
    fun extractAppIdFromUrl(url: String): String? {
        return url.substringAfter("/app/", "").split("/").firstOrNull()?.filter { it.isDigit() }
    }

    suspend fun readSteamFileContent(context: Context, dirUri: Uri, fileName: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val dir = DocumentFile.fromTreeUri(context, dirUri)
                val file = dir?.findFile(fileName)

                if (file == null || !file.exists()) {
                    return@withContext null
                }

                context.contentResolver.openInputStream(file.uri)?.use {
                    it.bufferedReader().readText()
                } ?: "Error: Could not open file for reading."
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    suspend fun deleteSteamFile(context: Context, dirUri: Uri, fileName: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val dir = DocumentFile.fromTreeUri(context, dirUri)
                    ?: return@withContext "Error: Failed to access directory."
                val file = dir.findFile(fileName)
                if (file?.delete() == true) {
                    "Successfully deleted $fileName"
                } else {
                    "Error: Could not delete $fileName"
                }
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    suspend fun createSteamFileFromDetails(context: Context, dirUri: Uri, title: String, content: String, extension: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val dir = DocumentFile.fromTreeUri(context, dirUri)
                    ?: return@withContext "Error: Failed to access directory."

                val fileName = "$title.$extension"
                dir.findFile(fileName)?.delete()
                val file = dir.createFile("application/octet-stream", fileName)
                    ?: return@withContext "Error: Could not create file."

                context.contentResolver.openOutputStream(file.uri)?.use {
                    it.write(content.toByteArray())
                }
                "Successfully created $fileName"
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    suspend fun fetchGameInfo(appId: String): GameInfo {
        return withContext(Dispatchers.IO) {
            val client = HttpClient(Android)
            try {
                val apiUrl = "https://store.steampowered.com/api/appdetails?appids=$appId"
                val jsonResponse = client.get(apiUrl).bodyAsText()
                val root = JSONObject(jsonResponse).getJSONObject(appId)
                if (!root.getBoolean("success")) throw Exception("Invalid AppID. No game found on Steam.")
                val data = root.getJSONObject("data")
                val gameName = data.getString("name").replace(Regex("[/:*?\"<>|]"), "").trim()
                val headerImage = data.getString("header_image")
                GameInfo(appId, gameName, headerImage)
            } finally {
                client.close()
            }
        }
    }

    suspend fun refreshFileList(context: Context, uri: Uri): List<String> {
        return withContext(Dispatchers.IO) {
            val dir = DocumentFile.fromTreeUri(context, uri)
            dir?.listFiles()
                ?.filter { it.name?.endsWith(".steam", true) == true }
                ?.mapNotNull { it.name }
                ?.sorted()
                ?: emptyList()
        }
    }
}
