package com.example.rescuer.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import com.example.rescuer.ui.theme.AppColors
import com.example.rescuer.ui.theme.LocalAppScale
import com.example.rescuer.ui.theme.scaledDp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class MeshNode(
    val id: String,
    val hop: Int,
    val signal: Float,
    val isSelf: Boolean = false,
    val label: String? = null
)

@Composable
fun MeshMap(
    nodes: List<MeshNode>,
    modifier: Modifier = Modifier
) {
    val scaleFactor = LocalAppScale.current
    val ringStepDp = scaledDp(78, scaleFactor)
    val nodeRadiusDp = scaledDp(10, scaleFactor)
    val baseRingColor = AppColors.Gray700.copy(alpha = 0.55f)
    val edgeColor = AppColors.Gray500.copy(alpha = 0.55f)
    val nodeStrokeColor = AppColors.White.copy(alpha = 0.9f)
    val labelColor = AppColors.Gray400.copy(alpha = 0.95f)
    val density = LocalDensity.current

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var zoom by remember { mutableStateOf(1f) }
    var currentDragId by remember { mutableStateOf<String?>(null) }

    val ringStepPx = with(density) { ringStepDp.toPx() }
    val nodeRadiusPx = with(density) { nodeRadiusDp.toPx() }
    val labelTextSizePx = with(density) { scaledDp(10, scaleFactor).toPx() }
    val labelPaddingPx = with(density) { scaledDp(6, scaleFactor).toPx() }
    val labelPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
    }

    val manualOffsets = remember { mutableStateMapOf<String, Offset>() }
    LaunchedEffect(nodes) {
        val ids = nodes.map { it.id }.toSet()
        manualOffsets.keys.toList().forEach { key ->
            if (key !in ids) {
                manualOffsets.remove(key)
            }
        }
    }

    val layoutPositions by remember(nodes, ringStepDp, scaleFactor) {
        derivedStateOf {
            buildLayoutPositions(nodes, ringStepPx)
        }
    }

    val maxHop = nodes.maxOfOrNull { it.hop } ?: 1
    val minZoom = 0.7f
    val maxZoom = 2.2f

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTransformGestures { _, panChange, zoomChange, _ ->
                    val nextZoom = (zoom * zoomChange).coerceIn(minZoom, maxZoom)
                    zoom = nextZoom
                    pan += panChange
                }
            }
            .pointerInput(nodes, zoom, pan, containerSize) {
                detectDragGestures(
                    onDragStart = { start ->
                        val center = Offset(
                            containerSize.width / 2f,
                            containerSize.height / 2f
                        )
                        val graphPoint = screenToGraph(start, center, pan, zoom)
                        val hit = findNodeHit(
                            nodes = nodes,
                            layoutPositions = layoutPositions,
                            manualOffsets = manualOffsets,
                            point = graphPoint,
                            radius = nodeRadiusPx * 1.8f / zoom
                        )
                        currentDragId = hit
                    },
                    onDragCancel = { currentDragId = null },
                    onDragEnd = { currentDragId = null },
                    onDrag = { change, dragAmount ->
                        if (currentDragId == null) {
                            pan += dragAmount
                        } else {
                            val center = Offset(
                                containerSize.width / 2f,
                                containerSize.height / 2f
                            )
                            val graphPoint = screenToGraph(change.position, center, pan, zoom)
                            manualOffsets[currentDragId!!] = graphPoint
                        }
                        change.consume()
                    }
                )
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val center = Offset(size.width / 2f, size.height / 2f) + pan
            val scaledRingStep = ringStepPx * zoom
            val nodeBaseRadiusPx = nodeRadiusPx * zoom

            for (hop in 1..maxHop) {
                drawCircle(
                    color = baseRingColor,
                    center = center,
                    radius = scaledRingStep * hop,
                    style = Stroke(width = 1.2f * zoom)
                )
            }

            nodes.filter { !it.isSelf && it.hop == 1 }.forEach { node ->
                val nodeOffset = layoutPositions[node.id] ?: Offset.Zero
                val draggedOffset = manualOffsets[node.id]
                val graphOffset = draggedOffset ?: nodeOffset
                val screenOffset = center + (graphOffset * zoom)
                drawLine(
                    color = edgeColor,
                    start = center,
                    end = screenOffset,
                    strokeWidth = 1.3f * zoom,
                    cap = StrokeCap.Round
                )
            }

            nodes.forEach { node ->
                val nodeOffset = layoutPositions[node.id] ?: Offset.Zero
                val draggedOffset = manualOffsets[node.id]
                val graphOffset = draggedOffset ?: nodeOffset
                val screenOffset = center + (graphOffset * zoom)
                val signalScale = (0.65f + (node.signal.coerceIn(0.1f, 1f) * 0.55f))
                val nodeRadius = nodeBaseRadiusPx * signalScale
                val fill = when {
                    node.isSelf -> AppColors.Green
                    node.hop == 1 -> AppColors.White
                    else -> AppColors.Gray400
                }
                drawCircle(
                    color = fill.copy(alpha = 0.9f),
                    center = screenOffset,
                    radius = nodeRadius
                )
                drawCircle(
                    color = nodeStrokeColor,
                    center = screenOffset,
                    radius = nodeRadius,
                    style = Stroke(width = 1f * zoom)
                )
                val label = node.label
                if (!label.isNullOrBlank() && !node.isSelf) {
                    labelPaint.color = labelColor.toArgb()
                    labelPaint.textSize = labelTextSizePx
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        screenOffset.x,
                        screenOffset.y + nodeRadius + labelPaddingPx + labelTextSizePx,
                        labelPaint
                    )
                }
            }
        }
    }
}

private fun buildLayoutPositions(nodes: List<MeshNode>, ringStep: Float): Map<String, Offset> {
    val positions = mutableMapOf<String, Offset>()
    val byHop = nodes.filter { !it.isSelf }.groupBy { it.hop }
    byHop.forEach { (hop, group) ->
        val sorted = group.sortedBy { it.id }
        val step = if (sorted.isNotEmpty()) 360f / sorted.size else 360f
        sorted.forEachIndexed { index, node ->
            val angle = (step * index).toRadians()
            val radius = ringStep * hop
            positions[node.id] = Offset(
                x = cos(angle) * radius,
                y = sin(angle) * radius
            )
        }
    }
    nodes.filter { it.isSelf }.forEach { node ->
        positions[node.id] = Offset.Zero
    }
    return positions
}

private fun Float.toRadians(): Float {
    return (this / 180f * PI).toFloat()
}

private fun screenToGraph(
    screenPoint: Offset,
    center: Offset,
    pan: Offset,
    zoom: Float
): Offset {
    val translated = screenPoint - center - pan
    return Offset(translated.x / zoom, translated.y / zoom)
}

private fun findNodeHit(
    nodes: List<MeshNode>,
    layoutPositions: Map<String, Offset>,
    manualOffsets: Map<String, Offset>,
    point: Offset,
    radius: Float
): String? {
    var closestId: String? = null
    var closestDistance = Float.MAX_VALUE
    nodes.forEach { node ->
        val base = layoutPositions[node.id] ?: Offset.Zero
        val offset = manualOffsets[node.id] ?: base
        val distance = hypot(point.x - offset.x, point.y - offset.y)
        if (distance <= radius && distance < closestDistance) {
            closestDistance = distance
            closestId = node.id
        }
    }
    return closestId
}
