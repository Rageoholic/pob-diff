package core

class Greeting(private val platformName: String) {
    fun greet(): String = "Hello, $platformName!"
}
