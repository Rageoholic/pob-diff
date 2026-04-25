package game.poe1

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import game.Game
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import storage.appStorage
import storage.read
import storage.write

private const val TAGS_URL =
    "https://api.github.com/repos/grindinggear/skilltree-export/tags"
private const val TREE_URL =
    "https://raw.githubusercontent.com/grindinggear/skilltree-export/{tag}/data.json"

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

private val httpClient = HttpClient {
    install(ContentNegotiation) { json(json) }
}

@Serializable
private data class GitHubTag(val name: String)

private suspend fun fetchVersions(): List<String> =
    httpClient.get(TAGS_URL).body<List<GitHubTag>>().map { it.name }

private suspend fun loadTree(tag: String): PoE1TreeData {
    val storage = appStorage()
    val key = "poe1/tree/$tag"
    storage.cache.read(key, PoE1TreeData)?.let { return it }
    val raw: String = httpClient.get(TREE_URL.replace("{tag}", tag)).body()
    val tree = json.decodeFromString<PoE1TreeData>(raw)
    storage.cache.write(key, tree)
    return tree
}

object PoE1Game : Game {
    override val name = "Path of Exile"

    @Composable
    override fun App() {
        var state by remember { mutableStateOf<PoE1AppState>(PoE1AppState.Loading) }

        LaunchedEffect(Unit) {
            state = try {
                appStorage().recover()
                val versions = fetchVersions()
                val latest = versions.first()
                PoE1AppState.Loaded(latest, versions, loadTree(latest))
            } catch (e: Exception) {
                PoE1AppState.Error(e.message ?: "Unknown error")
            }
        }

        when (val s = state) {
            is PoE1AppState.Loading -> Text("Loading…")
            is PoE1AppState.Error -> Text("Error: ${s.message}")
            is PoE1AppState.Loaded -> PoE1TreeView(s.tree)
        }
    }
}

private sealed interface PoE1AppState {
    data object Loading : PoE1AppState
    data class Error(val message: String) : PoE1AppState
    data class Loaded(
        val version: String,
        val versions: List<String>,
        val tree: PoE1TreeData,
    ) : PoE1AppState
}
