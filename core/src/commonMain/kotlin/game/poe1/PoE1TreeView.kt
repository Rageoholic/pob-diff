package game.poe1

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import platform.chooseOpenFile
import platform.chooseSaveFile
import platform.readFile
import platform.writeFile
import storage.appStorage
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

sealed interface ErrorSegment {
    data class Plain(val text: String) : ErrorSegment
    data class Link(
        val text: String,
        val onClick: () -> Unit,
        val onHoverChange: ((Offset?) -> Unit)? = null,
    ) : ErrorSegment
}

interface DisplayError {
    fun segments(): List<ErrorSegment>
}

private val BG = Color(0xFF0A0A0F)
private val EDGE_DEFAULT = Color(0xFF333344)
private val EDGE_ALLOCATED = Color(0xFFAA8833)
private val NODE_BASE = Color(0xFF1A1A2A)
private val NODE_BORDER = Color(0xFF555566)

private val HALO_ALLOCATED = Color(0xFF2277BB)
private val HALO_ALLOCATED_NOTABLE = Color(0xFF33AAFF)
private val HALO_ALLOCATED_KEYSTONE = Color(0xFFFFDD44)
private val HALO_REACHABLE = Color(0xFF44AA44)
private val HALO_CLASS_START = Color(0xFFDD8833)
private val HALO_HOVER = Color(0xFFFFFFFF)
private val HALO_FLASH = Color(0xFFFF2222)

private val buildJson = Json { ignoreUnknownKeys = true }
private const val BUILDS_PREFIX = "builds"

private fun DrawScope.drawHalo(
    pos: Offset,
    nodeRadius: Float,
    color: Color,
    alpha: Float = 0.75f,
    width: Float = 18f,
    falloff: Float = 0.4f,
) {
    val outerRadius = nodeRadius + width
    val peakStop = nodeRadius / outerRadius
    val colorAt = color.copy(alpha = alpha)
    val stops = if (falloff <= 0f) {
        arrayOf(0f to Color.Transparent, peakStop to colorAt, 1f to colorAt)
    } else {
        val tailStart = (peakStop + (1f - peakStop) * (1f - falloff)).coerceAtMost(0.999f)
        arrayOf(0f to Color.Transparent, peakStop to colorAt, tailStart to colorAt, 1f to Color.Transparent)
    }
    drawCircle(
        brush = Brush.radialGradient(*stops, center = pos, radius = outerRadius),
        radius = outerRadius,
        center = pos,
    )
}

@Composable
fun PoE1TreeView(tree: PoE1TreeData) {
    val build = remember { PoE1Build() }
    val positions = remember(tree) { precomputePositions(tree) }
    val scope = rememberCoroutineScope()

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var initialized by remember { mutableStateOf(false) }
    var hoveredNodeId by remember { mutableStateOf<String?>(null) }
    var hoveredScreenPos by remember { mutableStateOf(Offset.Zero) }
    var displayError by remember { mutableStateOf<DisplayError?>(null) }
    var flashingNodes by remember { mutableStateOf(emptySet<String>()) }

    // Pending class selection waiting for the name dialog.
    var pendingClass by remember { mutableStateOf<PoE1Class?>(null) }
    var showLoadDialog by remember { mutableStateOf(false) }

    fun autoSave() {
        val save = build.toBuildSave() ?: return
        scope.launch(Dispatchers.IO) {
            appStorage().persistent.write(save.storageKey(), buildJson.encodeToString(save).encodeToByteArray())
        }
    }

    fun commitName(newName: String) {
        val save = build.toBuildSave() ?: run { build.name = newName; return }
        val oldKey = save.storageKey()
        val newKey = save.copy(name = newName).storageKey()
        if (oldKey == newKey) return
        scope.launch(Dispatchers.IO) {
            appStorage().persistent.rename(oldKey, newKey)
            withContext(Dispatchers.Main) { build.name = newName }
            // Auto-save current state to the new slot.
            val updated = build.toBuildSave() ?: return@launch
            appStorage().persistent.write(updated.storageKey(), buildJson.encodeToString(updated).encodeToByteArray())
        }
    }

    fun zoomToNode(nodeId: String, targetScale: Float = 3f) {
        val pos = positions[nodeId] ?: return
        scale = targetScale
        offset = Offset(
            x = canvasSize.width / 2f - pos.x * targetScale,
            y = canvasSize.height / 2f - pos.y * targetScale,
        )
    }

    fun zoomToClassStart(
        nodeId: String,
        scoreThreshold: Float = 28f,
        notableScore: Float = 5f,
        keystoneScore: Float = 8f,
        normalScore: Float = 1f,
        viewportPadding: Float = 0.15f,
    ) {
        val startPos = positions[nodeId] ?: return

        val visited = mutableSetOf(nodeId)
        val queue = ArrayDeque<String>().also { it.add(nodeId) }
        val collected = mutableListOf(startPos)
        var score = 0f

        while (queue.isNotEmpty() && score < scoreThreshold) {
            val current = queue.removeFirst()
            val node = tree.nodes[current] ?: continue
            for (neighborId in node.out + node.into) {
                if (neighborId !in visited && neighborId in positions) {
                    visited.add(neighborId)
                    queue.add(neighborId)
                    val neighbor = tree.nodes[neighborId] ?: continue
                    collected.add(positions[neighborId]!!)
                    score += when {
                        neighbor.isKeystone -> keystoneScore
                        neighbor.isNotable -> notableScore
                        else -> normalScore
                    }
                }
            }
        }

        val minX = collected.minOf { it.x }
        val maxX = collected.maxOf { it.x }
        val minY = collected.minOf { it.y }
        val maxY = collected.maxOf { it.y }
        val targetScale = minOf(
            canvasSize.width * (1f - viewportPadding) / (maxX - minX).coerceAtLeast(1f),
            canvasSize.height * (1f - viewportPadding) / (maxY - minY).coerceAtLeast(1f),
        )
        scale = targetScale
        offset = Offset(
            x = canvasSize.width / 2f - ((minX + maxX) / 2f) * targetScale,
            y = canvasSize.height / 2f - ((minY + maxY) / 2f) * targetScale,
        )
    }

    // Restore last unnamed build on startup.
    LaunchedEffect(Unit) {
        val bytes = appStorage().persistent.read("$BUILDS_PREFIX/__unnamed__") ?: return@LaunchedEffect
        val save = runCatching { buildJson.decodeFromString<BuildSave>(bytes.decodeToString()) }.getOrNull()
            ?: return@LaunchedEffect
        build.loadFromSave(save, tree)
    }

    LaunchedEffect(flashingNodes) {
        if (flashingNodes.isNotEmpty()) {
            delay(450.milliseconds)
            flashingNodes = emptySet()
        }
    }

    LaunchedEffect(build.startNodeId) {
        build.startNodeId?.let { zoomToClassStart(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(BG)
                .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            when (event.type) {
                                PointerEventType.Scroll -> {
                                    val delta = change.scrollDelta
                                    val cursor = change.position
                                    val zoomFactor = if (delta.y < 0) 1.1f else 0.9f
                                    val newScale = (scale * zoomFactor).coerceIn(0.05f, 8f)
                                    offset = cursor - (cursor - offset) * (newScale / scale)
                                    scale = newScale
                                }
                                PointerEventType.Move -> {
                                    val pos = change.position
                                    hoveredScreenPos = pos
                                    val treePos = Offset(
                                        (pos.x - offset.x) / scale,
                                        (pos.y - offset.y) / scale,
                                    )
                                    val hit = positions.minByOrNull { (_, p) ->
                                        (p - treePos).getDistance()
                                    }
                                    hoveredNodeId = if (hit != null &&
                                        (hit.value - treePos).getDistance() < 60f
                                    ) hit.key else null
                                }
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { _, dragAmount -> offset += dragAmount }
                }
                .pointerInput(positions) {
                    detectTapGestures { tapOffset ->
                        val treePos = Offset(
                            (tapOffset.x - offset.x) / scale,
                            (tapOffset.y - offset.y) / scale,
                        )
                        val hit = positions.minByOrNull { (_, p) -> (p - treePos).getDistance() }
                        if (hit != null && (hit.value - treePos).getDistance() < 60f) {
                            when (val result = build.toggle(hit.key, tree)) {
                                is PoE1Build.ToggleResult.Allocated,
                                is PoE1Build.ToggleResult.Deallocated -> {
                                    displayError = null
                                    autoSave()
                                }
                                is PoE1Build.ToggleResult.NotReachable -> {
                                    displayError = object : DisplayError {
                                        override fun segments() = listOf(
                                            ErrorSegment.Link(
                                                text = result.nodeName,
                                                onClick = { zoomToNode(result.nodeId) },
                                                onHoverChange = { pos ->
                                                    hoveredNodeId = if (pos != null) result.nodeId else null
                                                    if (pos != null) hoveredScreenPos = pos
                                                },
                                            ),
                                            ErrorSegment.Plain(" is not connected to your build"),
                                        )
                                    }
                                    flashingNodes = setOf(result.nodeId)
                                }
                                is PoE1Build.ToggleResult.RemoveBlocked -> {
                                    displayError = object : DisplayError {
                                        override fun segments() = listOf(
                                            ErrorSegment.Link(
                                                text = result.removedName,
                                                onClick = { zoomToNode(result.removedId) },
                                                onHoverChange = { pos ->
                                                    hoveredNodeId = if (pos != null) result.removedId else null
                                                    if (pos != null) hoveredScreenPos = pos
                                                },
                                            ),
                                            ErrorSegment.Plain(" is needed to take "),
                                            ErrorSegment.Link(
                                                text = result.dependentName,
                                                onClick = { zoomToNode(result.dependentId) },
                                                onHoverChange = { pos ->
                                                    hoveredNodeId = if (pos != null) result.dependentId else null
                                                    if (pos != null) hoveredScreenPos = pos
                                                },
                                            ),
                                        )
                                    }
                                    flashingNodes = setOf(result.removedId, result.dependentId)
                                }
                            }
                        }
                    }
                }
        ) {
            if (!initialized && size.width > 0f) {
                val treeWidth = (tree.maxX - tree.minX).toFloat()
                val treeHeight = (tree.maxY - tree.minY).toFloat()
                if (treeWidth > 0f && treeHeight > 0f) {
                    scale = minOf(size.width / treeWidth, size.height / treeHeight) * 0.9f
                    offset = Offset(
                        x = (size.width - treeWidth * scale) / 2f - tree.minX.toFloat() * scale,
                        y = (size.height - treeHeight * scale) / 2f - tree.minY.toFloat() * scale,
                    )
                    initialized = true
                }
            }

            withTransform({
                translate(offset.x, offset.y)
                scale(scale, scale, Offset.Zero)
            }) {
                for ((id, node) in tree.nodes) {
                    val from = positions[id] ?: continue
                    for (toId in node.out) {
                        val to = positions[toId] ?: continue
                        val allocated = id in build.allocatedNodes && toId in build.allocatedNodes
                        drawLine(
                            color = if (allocated) EDGE_ALLOCATED else EDGE_DEFAULT,
                            start = from,
                            end = to,
                            strokeWidth = if (allocated) 4f else 2f,
                        )
                    }
                }

                for ((id, node) in tree.nodes) {
                    val pos = positions[id] ?: continue
                    val allocated = id in build.allocatedNodes
                    val isClassStart = node.classStartIndex != null
                    val reachable = !allocated && build.canAllocate(id, tree)
                    val radius = when {
                        node.isKeystone -> 38f
                        node.isNotable -> 26f
                        isClassStart -> 32f
                        else -> 16f
                    }

                    when {
                        allocated && node.isKeystone -> drawHalo(pos, radius, HALO_ALLOCATED_KEYSTONE, width = 6f, falloff = 0f)
                        allocated && node.isNotable  -> drawHalo(pos, radius, HALO_ALLOCATED_NOTABLE,  width = 5f, falloff = 0f)
                        allocated                    -> drawHalo(pos, radius, HALO_ALLOCATED,          width = 4f, falloff = 0f)
                        isClassStart                 -> drawHalo(pos, radius, HALO_CLASS_START,         width = 6f, falloff = 0f)
                        reachable                    -> drawHalo(pos, radius, HALO_REACHABLE, alpha = 0.45f, width = 8f, falloff = 0.6f)
                    }
                    if (id in flashingNodes) drawHalo(pos, radius, HALO_FLASH, alpha = 0.9f, width = 24f, falloff = 0.8f)
                    if (id == hoveredNodeId) drawHalo(pos, radius, HALO_HOVER, alpha = 0.5f, width = 20f, falloff = 0.7f)

                    drawCircle(NODE_BASE, radius, pos)
                    drawCircle(NODE_BORDER, radius, pos, style = Stroke(1.5f))
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ClassDropdown(
                selected = build.selectedClass,
                onSelect = { pendingClass = it },
                onClear = { build.clearClass(); autoSave() },
            )
            NameField(
                name = build.name,
                onCommit = { newName -> commitName(newName) },
            )
            BuildButton(label = "Load") { showLoadDialog = true }
            BuildButton(label = "Undo", enabled = build.canUndo) { build.undo(tree); autoSave() }
            BuildButton(label = "Redo", enabled = build.canRedo) { build.redo(tree); autoSave() }
            BuildButton(label = "Export") {
                scope.launch(Dispatchers.IO) {
                    val save = build.toBuildSave() ?: return@launch
                    val path = chooseSaveFile("Export Build", "json") ?: return@launch
                    writeFile(path, buildJson.encodeToString(save))
                }
            }
            BuildButton(label = "Import") {
                scope.launch(Dispatchers.IO) {
                    val path = chooseOpenFile("Import Build", "json") ?: return@launch
                    val json = readFile(path) ?: return@launch
                    val save = runCatching { buildJson.decodeFromString<BuildSave>(json) }.getOrNull()
                        ?: return@launch
                    withContext(Dispatchers.Main) { build.loadFromSave(save, tree); autoSave() }
                }
            }
        }

        pendingClass?.let { cls ->
            ClassNameDialog(
                onStart = { name ->
                    build.selectClass(cls, tree)
                    build.name = name
                    pendingClass = null
                    autoSave()
                },
                onUseDefault = {
                    build.selectClass(cls, tree)
                    build.name = ""
                    pendingClass = null
                    autoSave()
                },
                onDismiss = { pendingClass = null },
            )
        }

        if (showLoadDialog) {
            LoadDialog(
                onLoad = { save ->
                    build.loadFromSave(save, tree)
                    showLoadDialog = false
                    autoSave()
                },
                onDismiss = { showLoadDialog = false },
            )
        }

        displayError?.let { err ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .background(Color(0xCCAA1111), RoundedCornerShape(6.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row {
                    err.segments().forEach { segment ->
                        when (segment) {
                            is ErrorSegment.Plain -> Text(
                                text = segment.text,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            is ErrorSegment.Link -> ErrorLink(segment)
                        }
                    }
                }
            }
        }

        hoveredNodeId?.let { nodeId ->
            val node = tree.nodes[nodeId]
            if (node != null && node.name.isNotBlank()) {
                Popup(
                    offset = IntOffset(
                        hoveredScreenPos.x.roundToInt() + 16,
                        hoveredScreenPos.y.roundToInt() + 16,
                    )
                ) {
                    NodeTooltip(nodeId, node, onZoom = { zoomToNode(nodeId) })
                }
            }
        }
    }
}

@Composable
private fun NameField(name: String, onCommit: (String) -> Unit) {
    var draft by remember(name) { mutableStateOf(name) }
    val changed = draft != name

    fun commit() {
        if (changed) onCommit(draft)
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color(0xCC0A0A1A),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (changed) Color(0xFF8888BB) else Color(0xFF555566),
        ),
    ) {
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = true,
            textStyle = TextStyle(color = Color(0xFFCCCCCC), fontSize = 13.sp),
            cursorBrush = SolidColor(Color(0xFFCCCCCC)),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            decorationBox = { inner ->
                Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    if (draft.isEmpty()) Text(
                        "(unnamed)",
                        color = Color(0xFF555566),
                        fontSize = 13.sp,
                    )
                    inner()
                }
            },
            modifier = Modifier
                .width(160.dp)
                .onFocusChanged { if (!it.isFocused) commit() },
        )
    }
}

@Composable
private fun ClassNameDialog(
    onStart: (name: String) -> Unit,
    onUseDefault: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name this character", color = Color(0xFFDDDDDD)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Leave blank to use the default slot.", color = Color(0xFF888888), fontSize = 13.sp)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF0A0A1A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF555566)),
                ) {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color(0xFFCCCCCC), fontSize = 14.sp),
                        cursorBrush = SolidColor(Color(0xFFCCCCCC)),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onStart(name.trim()) }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onStart(name.trim()) }) { Text("Start") }
        },
        dismissButton = {
            TextButton(onClick = onUseDefault) { Text("Use Default") }
        },
        containerColor = Color(0xFF1A1A2A),
    )
}

@Composable
private fun LoadDialog(
    onLoad: (BuildSave) -> Unit,
    onDismiss: () -> Unit,
) {
    var saves by remember { mutableStateOf<List<BuildSave>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val keys = appStorage().persistent.list(BUILDS_PREFIX)
        saves = keys.mapNotNull { key ->
            val bytes = appStorage().persistent.read(key) ?: return@mapNotNull null
            runCatching { buildJson.decodeFromString<BuildSave>(bytes.decodeToString()) }.getOrNull()
        }.sortedBy { it.name }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Load Build", color = Color(0xFFDDDDDD)) },
        text = {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else if (saves.isEmpty()) {
                Text("No saved builds found.", color = Color(0xFF888888), fontSize = 13.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    saves.forEach { save ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF0A0A1A),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333344)),
                            modifier = Modifier.fillMaxWidth().clickable { onLoad(save) },
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(
                                    text = save.name.ifEmpty { "(unnamed)" },
                                    color = Color(0xFFCCCCCC),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = "${save.className}  •  ${save.actions.size} actions",
                                    color = Color(0xFF666677),
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = Color(0xFF1A1A2A),
    )
}

@Composable
private fun BuildButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (enabled) Color(0xCC0A0A1A) else Color(0x660A0A1A),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (enabled) Color(0xFF555566) else Color(0xFF333344),
        ),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            text = label,
            color = if (enabled) Color(0xFFCCCCCC) else Color(0xFF555566),
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ErrorLink(segment: ErrorSegment.Link) {
    var windowPos by remember { mutableStateOf(Offset.Zero) }
    Text(
        text = segment.text,
        color = Color(0xFFFFDD88),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier
            .clickable { segment.onClick() }
            .onGloballyPositioned { windowPos = it.positionInWindow() }
            .pointerInput(segment) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val localPos = event.changes.firstOrNull()?.position
                        when (event.type) {
                            PointerEventType.Move,
                            PointerEventType.Enter -> segment.onHoverChange?.invoke(
                                localPos?.let { windowPos + it }
                            )
                            PointerEventType.Exit -> segment.onHoverChange?.invoke(null)
                        }
                    }
                }
            },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassDropdown(
    selected: PoE1Class?,
    onSelect: (PoE1Class) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xCC0A0A1A),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF555566)),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .width(180.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selected?.name ?: "Select class",
                    color = if (selected != null) Color(0xFFFFFFFF) else Color(0xFF888888),
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                ExposedDropdownMenuDefaults.TrailingIcon(expanded)
            }
        }
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Select class", color = Color(0xFF888888)) },
                onClick = { onClear(); expanded = false },
            )
            PoE1Class.entries.forEach { cls ->
                DropdownMenuItem(
                    text = { Text(cls.name) },
                    onClick = { onSelect(cls); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun NodeTooltip(nodeId: String, node: PoE1Node, onZoom: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xEE1A1A2A),
        tonalElevation = 4.dp,
        modifier = Modifier.widthIn(max = 280.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = node.name,
                color = when {
                    node.isKeystone -> Color(0xFFFFDD44)
                    node.isNotable -> Color(0xFF88CCFF)
                    node.classStartIndex != null -> Color(0xFFDD8833)
                    else -> Color(0xFFDDDDDD)
                },
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { onZoom() },
            )
            node.stats.forEach { stat ->
                Text(text = stat, color = Color(0xFFAAAAAA), fontSize = 12.sp)
            }
            node.reminderText.forEach { reminder ->
                Text(text = reminder, color = Color(0xFF777777), fontSize = 11.sp)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "id: $nodeId  •  skill: ${node.skill}",
                color = Color(0xFF555566),
                fontSize = 10.sp,
                fontStyle = FontStyle.Italic,
            )
        }
    }
}
