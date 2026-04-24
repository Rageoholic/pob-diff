package game

import androidx.compose.runtime.Composable

interface Game {
    val name: String

    @Composable
    fun App()
}
