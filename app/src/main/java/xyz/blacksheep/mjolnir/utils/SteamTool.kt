package xyz.blacksheep.mjolnir.utils

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.saveable.Saver
import androidx.documentfile.provider.DocumentFile
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Represents metadata for a Steam game retrieved from the Steam Store API.
 *
 * @property appId The unique numeric identifier for the Steam game (e.g., "10").
 * @property name The sanitized name of the game suitable for file naming (illegal characters removed).
 * @property headerImage The URL to the game's header image (capsule image).
 */
data class GameInfo(val appId: String, val name: String, val headerImage: String)

/**
 * Represents the state required to handle a file overwrite conflict.
 *
 * @property gameInfo The [GameInfo] of the new game attempting to be created.
 * @property oldAppId The AppID extracted from the existing file that would be overwritten.
 */
data class OverwriteInfo(val gameInfo: GameInfo, val oldAppId: String)

/**
 * A Jetpack Compose [Saver] implementation for persisting [GameInfo] across process death or configuration changes.
 * Serializes the object into a List<String>.
 */
val GameInfoSaver = Saver<GameInfo, List<String>>(
    save = { listOf(it.appId, it.name, it.headerImage) },
    restore = { GameInfo(it[0], it[1], it[2]) }
)

/**
 * A Jetpack Compose [Saver] implementation for persisting [OverwriteInfo] across process death or configuration changes.
 * Handles the nested serialization of the contained [GameInfo] object.
 */
val OverwriteInfoSaver = Saver<OverwriteInfo?, Any>(
    save = { it?.let { with(GameInfoSaver) { save(it.gameInfo)?.let { list -> listOf(it.oldAppId) + list } } } },
    restore = {
        (it as? List<*>)?.let { value ->
            val oldAppId = value[0] as String
            @Suppress("UNCHECKED_CAST")
            val gameInfo = GameInfoSaver.restore(value.drop(1) as? List<String> ?: emptyList())
            if (gameInfo != null) {
                OverwriteInfo(gameInfo, oldAppId)
            } else {
                null
            }
        }
    }
)

/**
 * A utility singleton for interacting with Steam game data and managing Steam shortcut files (`.steam`).
 *
 * This tool handles:
 * 1. Parsing Steam Store URLs.
 * 2. Fetching game metadata from the public Steam Store API.
 * 3. Reading, creating, and deleting files within a user-selected directory using Android's Storage Access Framework (DocumentFile).
 *
 * **Key Operations:**
 * - Use [fetchGameInfo] to get metadata given an App ID.
 * - Use [createSteamFileFromDetails] to generate the physical file on disk.
 * - Use [refreshFileList] to see what is currently installed.
 */
object SteamTool {

    /**
     * Extracts the Steam App ID from a raw Steam Store URL.
     *
     * Example input: `https://store.steampowered.com/app/234141/My_Game`
     * Example output: `234141`
     *
     * @param url The full URL string to parse.
     * @return The numeric App ID as a String, or null if the URL is invalid or contains no ID.
     */
    fun extractAppIdFromUrl(url: String): String? {
        return url.substringAfter("/app/", "").split("/").firstOrNull()?.filter { it.isDigit() }
    }

    /**
     * Reads the text content of a specific file within a directory.
     *
     * @param context Android Context required for ContentResolver access.
     * @param dirUri The persistent URI of the directory (granted via ACTION_OPEN_DOCUMENT_TREE).
     * @param fileName The exact name of the file to read (e.g., "Half-Life.steam").
     * @return The string content of the file, or null if the file does not exist. Returns an error string starting with "Error:" on failure.
     */
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

    /**
     * Deletes a specific file from the given directory.
     *
     * @param context Android Context required for ContentResolver access.
     * @param dirUri The persistent URI of the directory.
     * @param fileName The name of the file to delete.
     * @return A status message: "Successfully deleted [fileName]" on success, or an error message starting with "Error:" on failure.
     */
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

    /**
     * Creates or overwrites a file with the specified content.
     *
     * This operation is destructive: if a file with the same name exists, it is deleted before the new one is created.
     *
     * @param context Android Context required for ContentResolver access.
     * @param dirUri The persistent URI of the target directory.
     * @param title The base name for the file (without extension). Special characters in this title should be pre-sanitized.
     * @param content The string content to write into the file.
     * @param extension The file extension to append (e.g., "steam").
     * @return A status message: "Successfully created [filename]" on success, or an error message starting with "Error:" on failure.
     */
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

    /**
     * Fetches metadata for a game from the Valve Steam Store API.
     *
     * @param appId The numeric Steam App ID.
     * @throws Exception if the API call fails, or if the API returns "success": false (invalid ID).
     * @return A [GameInfo] object containing the ID, sanitized name, and image URL.
     */
    suspend fun fetchGameInfo(appId: String): GameInfo {
        return withContext(Dispatchers.IO) {
            val client = HttpClient(Android)
            try {
                val apiUrl = "https://store.steampowered.com/api/appdetails?appids=$appId"
                val jsonResponse = client.get(apiUrl).bodyAsText()
                val root = JSONObject(jsonResponse).getJSONObject(appId)
                if (!root.getBoolean("success")) throw Exception("Invalid AppID. No game found on Steam.")
                val data = root.getJSONObject("data")
                val gameName = data.getString("name").replace(Regex("[/:*?\\\"<>|]"), "").trim()
                val headerImage = data.getString("header_image")
                GameInfo(appId, gameName, headerImage)
            } finally {
                client.close()
            }
        }
    }

    /**
     * Scans the target directory for files ending in ".steam" and returns their names.
     *
     * @param context Android Context required for ContentResolver access.
     * @param uri The persistent URI of the directory to scan.
     * @return A sorted list of filenames (e.g., ["Game A.steam", "Game B.steam"]). Returns empty list on error or if empty.
     */
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