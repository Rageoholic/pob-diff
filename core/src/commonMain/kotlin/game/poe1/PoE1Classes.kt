package game.poe1

enum class PoE1Class(val classStartIndex: Int, @Suppress("unused") val ascendancies: List<String>) {
    Scion(0, listOf("Ascendant")),
    Marauder(1, listOf("Juggernaut", "Berserker", "Chieftain")),
    Ranger(2, listOf("Deadeye", "Raider", "Pathfinder")),
    Witch(3, listOf("Elementalist", "Occultist", "Necromancer")),
    Duelist(4, listOf("Slayer", "Gladiator", "Champion")),
    Templar(5, listOf("Inquisitor", "Hierophant", "Guardian")),
    Shadow(6, listOf("Assassin", "Saboteur", "Trickster")),
}
