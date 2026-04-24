package game.poe1

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

fun nodePosition(node: PoE1Node, group: PoE1Group, constants: PoE1Constants): Offset {
    val radius = constants.orbitRadii.getOrElse(node.orbit) { 0 }.toFloat()
    val skillsInOrbit = constants.skillsPerOrbit.getOrElse(node.orbit) { 1 }.coerceAtLeast(1)
    val angle = (2.0 * PI * node.orbitIndex / skillsInOrbit).toFloat()
    return Offset(
        x = group.x.toFloat() + radius * sin(angle),
        y = group.y.toFloat() - radius * cos(angle),
    )
}

private fun PoE1Node.isAscendancy() = ascendancyName.isNotEmpty() || isAscendancyStart

fun precomputePositions(tree: PoE1TreeData): Map<String, Offset> {
    val ascendancyIds = tree.nodes.entries
        .filter { it.value.isAscendancy() }
        .map { it.key }
        .toSet()
    return tree.nodes.mapNotNull { (id, node) ->
        if (node.isProxy || id == "root" || node.isAscendancy()) return@mapNotNull null
        // Skip nodes whose only neighbors are ascendancy or proxy nodes
        val neighbors = (node.out + node.into).filter { it !in ascendancyIds }
        if (neighbors.isEmpty() && node.classStartIndex == null) return@mapNotNull null
        val group = tree.groups[node.group.toString()] ?: return@mapNotNull null
        id to nodePosition(node, group, tree.constants)
    }.toMap()
}
