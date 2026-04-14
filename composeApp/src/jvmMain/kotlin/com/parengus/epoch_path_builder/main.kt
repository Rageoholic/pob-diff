package com.parengus.epoch_path_builder

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import core.Engine

fun main() = application {
    val c = Engine().compute()
    println("ComposeApp started: core says $c")
    Window(
        onCloseRequest = ::exitApplication,
        title = "Epoch Path Builder",
    ) {
        App()
    }
}