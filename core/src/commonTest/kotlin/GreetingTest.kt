package core

import kotlin.test.Test
import kotlin.test.assertEquals

class GreetingTest {
    @Test
    fun greetIncludesPlatformName() {
        assertEquals("Hello, TestPlatform!", Greeting("TestPlatform").greet())
    }
}
