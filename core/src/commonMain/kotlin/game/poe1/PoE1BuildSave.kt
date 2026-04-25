package game.poe1

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class BuildAction {
    abstract val nodeId: String
    abstract val nodeName: String

    @Serializable
    @SerialName("allocate")
    data class Allocate(override val nodeId: String, override val nodeName: String) : BuildAction()

    @Serializable
    @SerialName("deallocate")
    data class Deallocate(override val nodeId: String, override val nodeName: String) : BuildAction()
}

@Serializable
data class BuildSave(
    val name: String,
    val className: String,
    val actions: List<BuildAction>,
)

private const val UNNAMED_KEY = "__unnamed__"

fun BuildSave.storageKey() = "builds/${name.ifEmpty { UNNAMED_KEY }}"
