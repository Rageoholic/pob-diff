package game.poe1

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.setValue

class PoE1Build {
    var selectedClass by mutableStateOf<PoE1Class?>(null)
        private set
    var startNodeId by mutableStateOf<String?>(null)
        private set
    val allocatedNodes = mutableStateSetOf<String>()

    fun selectClass(cls: PoE1Class, tree: PoE1TreeData) {
        allocatedNodes.clear()
        selectedClass = cls
        val startNode = tree.nodes.entries
            .firstOrNull { it.value.classStartIndex == cls.classStartIndex }
        startNodeId = startNode?.key
        startNode?.key?.let { allocatedNodes.add(it) }
    }

    fun clearClass() {
        allocatedNodes.clear()
        selectedClass = null
        startNodeId = null
    }

    sealed interface ToggleResult {
        data object Allocated : ToggleResult
        data object Deallocated : ToggleResult
        data class NotReachable(val nodeId: String, val nodeName: String) : ToggleResult
        data class RemoveBlocked(
            val removedId: String,
            val removedName: String,
            val dependentId: String,
            val dependentName: String,
        ) : ToggleResult
    }

    fun toggle(nodeId: String, tree: PoE1TreeData): ToggleResult {
        return if (nodeId in allocatedNodes) tryRemove(nodeId, tree)
        else tryAllocate(nodeId, tree)
    }

    fun canAllocate(nodeId: String, tree: PoE1TreeData): Boolean {
        if (nodeId in allocatedNodes) return false
        if (startNodeId == null) return tree.nodes[nodeId]?.classStartIndex != null
        val node = tree.nodes[nodeId] ?: return false
        return (node.out + node.into).any { it in allocatedNodes }
    }

    private fun tryAllocate(nodeId: String, tree: PoE1TreeData): ToggleResult {
        if (canAllocate(nodeId, tree)) {
            if (startNodeId == null) startNodeId = nodeId
            allocatedNodes.add(nodeId)
            return ToggleResult.Allocated
        }
        val name = tree.nodes[nodeId]?.name?.trim() ?: nodeId
        return ToggleResult.NotReachable(nodeId, name)
    }

    private fun tryRemove(nodeId: String, tree: PoE1TreeData): ToggleResult {
        val dependent = findDependent(nodeId, tree)
        if (dependent != null) {
            val removedName = tree.nodes[nodeId]?.name?.trim() ?: nodeId
            val dependentName = tree.nodes[dependent]?.name?.trim() ?: dependent
            return ToggleResult.RemoveBlocked(nodeId, removedName, dependent, dependentName)
        }
        allocatedNodes.remove(nodeId)
        if (nodeId == startNodeId) startNodeId = null
        return ToggleResult.Deallocated
    }

    private fun findDependent(nodeId: String, tree: PoE1TreeData): String? {
        val start = startNodeId ?: return null
        val remaining = allocatedNodes - nodeId
        if (remaining.isEmpty()) return null
        if (nodeId == start) return remaining.first()

        val reachable = mutableSetOf(start)
        val queue = ArrayDeque<String>().also { it.add(start) }
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val node = tree.nodes[current] ?: continue
            for (neighbor in node.out + node.into) {
                if (neighbor in remaining && neighbor !in reachable) {
                    reachable.add(neighbor)
                    queue.add(neighbor)
                }
            }
        }
        return remaining.firstOrNull { it !in reachable }
    }
}
