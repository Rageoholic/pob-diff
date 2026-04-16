package com.parengus.pob_diff

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import core.Engine

fun main() = application {
    val c = Engine().compute()
    println("ComposeApp started: core says $c")
    Window(
        onCloseRequest = ::exitApplication,
        title = "PoBDiff",
    ) {
        App()
    }
}
