package game.poe1

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import storage.Decoder
import storage.Storable

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

@Serializable
data class PoE1TreeData(
    val nodes: Map<String, PoE1Node>,
    val groups: Map<String, PoE1Group>,
    @SerialName("min_x") val minX: Double = 0.0,
    @SerialName("min_y") val minY: Double = 0.0,
    @SerialName("max_x") val maxX: Double = 0.0,
    @SerialName("max_y") val maxY: Double = 0.0,
    val constants: PoE1Constants = PoE1Constants(),
    val points: PoE1Points = PoE1Points(),
) : Storable {
    override fun toBytes() = json.encodeToString(this).encodeToByteArray()

    companion object : Decoder<PoE1TreeData> {
        override fun fromBytes(bytes: ByteArray) =
            json.decodeFromString<PoE1TreeData>(bytes.decodeToString())
    }
}

@Serializable
data class PoE1Node(
    val skill: Int = 0,
    val name: String = "",
    val icon: String = "",
    val stats: List<String> = emptyList(),
    val reminderText: List<String> = emptyList(),
    val group: Int = 0,
    val orbit: Int = 0,
    val orbitIndex: Int = 0,
    val out: List<String> = emptyList(),
    @SerialName("in") val into: List<String> = emptyList(),
    val isKeystone: Boolean = false,
    val isNotable: Boolean = false,
    val isMastery: Boolean = false,
    val isBloodline: Boolean = false,
    val isProxy: Boolean = false,
    val isAscendancyStart: Boolean = false,
    val isMultipleChoice: Boolean = false,
    val isMultipleChoiceOption: Boolean = false,
    val ascendancyName: String = "",
    val classStartIndex: Int? = null,
)

@Serializable
data class PoE1Group(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val orbits: List<Int> = emptyList(),
    val nodes: List<String> = emptyList(),
    val background: PoE1GroupBackground? = null,
    val isProxy: Boolean = false,
)

@Serializable
data class PoE1GroupBackground(
    val image: String = "",
    val isHalfImage: Boolean = false,
)

@Serializable
data class PoE1Constants(
    val skillsPerOrbit: List<Int> = emptyList(),
    val orbitRadii: List<Int> = emptyList(),
)

@Serializable
data class PoE1Points(
    val totalPoints: Int = 0,
    val ascendancyPoints: Int = 0,
)
