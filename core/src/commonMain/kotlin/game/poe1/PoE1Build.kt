package game.poe1

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.setValue

class PoE1Build {
    var name by mutableStateOf("")
    var selectedClass by mutableStateOf<PoE1Class?>(null)
        private set
    var startNodeId by mutableStateOf<String?>(null)
        private set
    val allocatedNodes = mutableStateSetOf<String>()

    val history = mutableStateListOf<BuildAction>()
    var historyCursor by mutableStateOf(0)
        private set

    val canUndo get() = historyCursor > 0
    val canRedo get() = historyCursor < history.size

    fun selectClass(cls: PoE1Class, tree: PoE1TreeData) {
        history.clear()
        historyCursor = 0
        name = ""
        allocatedNodes.clear()
        selectedClass = cls
        val startNode = tree.nodes.entries
            .firstOrNull { it.value.classStartIndex == cls.classStartIndex }
        startNodeId = startNode?.key
        startNode?.key?.let { allocatedNodes.add(it) }
    }

    fun clearClass() {
        history.clear()
        historyCursor = 0
        name = ""
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
        val result = if (nodeId in allocatedNodes) tryRemove(nodeId, tree)
        else tryAllocate(nodeId, tree)
        if (result is ToggleResult.Allocated || result is ToggleResult.Deallocated) {
            while (history.size > historyCursor) history.removeLast()
            val name = tree.nodes[nodeId]?.name?.trim() ?: nodeId
            history.add(
                if (result is ToggleResult.Allocated) BuildAction.Allocate(nodeId, name)
                else BuildAction.Deallocate(nodeId, name)
            )
            historyCursor = history.size
        }
        return result
    }

    fun undo(tree: PoE1TreeData) {
        if (!canUndo) return
        historyCursor--
        replay(tree, history.subList(0, historyCursor))
    }

    fun redo(tree: PoE1TreeData) {
        if (!canRedo) return
        replay(tree, history.subList(0, historyCursor + 1))
        historyCursor++
    }

    fun toBuildSave(): BuildSave? {
        val cls = selectedClass ?: return null
        return BuildSave(
            name = name,
            className = cls.name,
            actions = history.subList(0, historyCursor).toList(),
        )
    }

    fun loadFromSave(save: BuildSave, tree: PoE1TreeData) {
        val cls = PoE1Class.entries.firstOrNull { it.name == save.className } ?: return
        name = save.name
        allocatedNodes.clear()
        selectedClass = cls
        startNodeId = null
        history.clear()
        history.addAll(save.actions)
        historyCursor = 0
        replay(tree, save.actions)
        historyCursor = save.actions.size
    }

    fun canAllocate(nodeId: String, tree: PoE1TreeData): Boolean {
        if (nodeId in allocatedNodes) return false
        if (startNodeId == null) return tree.nodes[nodeId]?.classStartIndex != null
        val node = tree.nodes[nodeId] ?: return false
        return (node.out + node.into).any { it in allocatedNodes }
    }

    private fun replay(tree: PoE1TreeData, actions: List<BuildAction>) {
        val cls = selectedClass ?: return
        allocatedNodes.clear()
        startNodeId = null
        val startNode = tree.nodes.entries.firstOrNull { it.value.classStartIndex == cls.classStartIndex }
        startNodeId = startNode?.key
        startNode?.key?.let { allocatedNodes.add(it) }
        for (action in actions) {
            when (action) {
                is BuildAction.Allocate -> {
                    if (canAllocate(action.nodeId, tree)) {
                        if (startNodeId == null) startNodeId = action.nodeId
                        allocatedNodes.add(action.nodeId)
                    }
                }

                is BuildAction.Deallocate -> {
                    if (findDependent(action.nodeId, tree) == null) {
                        allocatedNodes.remove(action.nodeId)
                        if (action.nodeId == startNodeId) startNodeId = null
                    }
                }
            }
        }
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
