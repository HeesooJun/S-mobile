package com.example.lifesaivior.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.example.lifesaivior.R
import com.example.lifesaivior.presentation.MeshVisualEvent
import com.example.lifesaivior.ui.theme.AppColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect

data class MeshNode(
    val id: String,
    val hop: Int,
    val signal: Float,
    val isSelf: Boolean = false,
    val label: String? = null
)

data class MeshEdge(
    val a: String,
    val b: String,
    val isConfirmed: Boolean = false,
    val confirmedBy: String? = null
)

private const val REPULSION_FORCE = 90000f
private const val SPRING_LENGTH = 160f
private const val SPRING_STRENGTH = 0.02f
private const val CENTER_GRAVITY = 0.02f
private const val DAMPING = 0.86f
private const val MAX_VELOCITY = 26f
private const val PULSE_DECAY = 0.05f

private class GraphNodeState(
    val id: String,
    var label: String,
    var isSelf: Boolean,
    var hop: Int,
    var signal: Float,
    var x: Float,
    var y: Float
) {
    var vx: Float = 0f
    var vy: Float = 0f
    var isDragged: Boolean = false
    var pulseLevel: Float = 0f
}

private class Simulation {
    val nodes = mutableMapOf<String, GraphNodeState>()
    val edges = mutableListOf<MeshEdge>()
    var width: Float = 1000f
    var height: Float = 1000f

    fun updateTopology(newNodes: List<MeshNode>, newEdges: List<MeshEdge>) {
        val newIds = newNodes.map { it.id }.toSet()
        nodes.keys.toList().forEach { id ->
            if (id !in newIds) nodes.remove(id)
        }

        newNodes.forEach { node ->
            val displayLabel = node.label?.takeIf { it.isNotBlank() } ?: node.id.take(8)
            val existing = nodes[node.id]
            if (existing != null) {
                existing.label = displayLabel
                existing.isSelf = node.isSelf
                existing.hop = node.hop
                existing.signal = node.signal
            } else {
                val angle = Random.nextFloat() * 2f * PI.toFloat()
                val radius = 40f + Random.nextFloat() * 60f
                val cosA = cos(angle.toDouble()).toFloat()
                val sinA = sin(angle.toDouble()).toFloat()
                nodes[node.id] = GraphNodeState(
                    id = node.id,
                    label = displayLabel,
                    isSelf = node.isSelf,
                    hop = node.hop,
                    signal = node.signal,
                    x = (width / 2f) + cosA * radius,
                    y = (height / 2f) + sinA * radius
                )
            }
        }

        edges.clear()
        edges.addAll(newEdges)
    }

    fun step() {
        val nodeList = nodes.values.toList()
        val cx = width / 2f
        val cy = height / 2f

        for (i in nodeList.indices) {
            val n1 = nodeList[i]
            for (j in i + 1 until nodeList.size) {
                val n2 = nodeList[j]
                val dx = n1.x - n2.x
                val dy = n1.y - n2.y
                val distSq = dx * dx + dy * dy
                if (distSq > 0.1f) {
                val dist = sqrt(distSq.toDouble()).toFloat()
                    val force = REPULSION_FORCE / distSq
                    val fx = (dx / dist) * force
                    val fy = (dy / dist) * force
                    if (!n1.isDragged) {
                        n1.vx += fx
                        n1.vy += fy
                    }
                    if (!n2.isDragged) {
                        n2.vx -= fx
                        n2.vy -= fy
                    }
                }
            }
        }

        edges.forEach { edge ->
            val n1 = nodes[edge.a]
            val n2 = nodes[edge.b]
            if (n1 != null && n2 != null) {
                val dx = n1.x - n2.x
                val dy = n1.y - n2.y
                val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                if (dist > 0.1f) {
                    val force = (dist - SPRING_LENGTH) * SPRING_STRENGTH
                    val fx = (dx / dist) * force
                    val fy = (dy / dist) * force
                    if (!n1.isDragged) {
                        n1.vx -= fx
                        n1.vy -= fy
                    }
                    if (!n2.isDragged) {
                        n2.vx += fx
                        n2.vy += fy
                    }
                }
            }
        }

        nodeList.forEach { node ->
            if (!node.isDragged) {
                val dx = node.x - cx
                val dy = node.y - cy
                node.vx -= dx * CENTER_GRAVITY
                node.vy -= dy * CENTER_GRAVITY

                val vMag = sqrt((node.vx * node.vx + node.vy * node.vy).toDouble()).toFloat()
                if (vMag > MAX_VELOCITY) {
                    node.vx = (node.vx / vMag) * MAX_VELOCITY
                    node.vy = (node.vy / vMag) * MAX_VELOCITY
                }

                node.x += node.vx
                node.y += node.vy

                node.vx *= DAMPING
                node.vy *= DAMPING
            } else {
                node.vx = 0f
                node.vy = 0f
            }

            if (node.pulseLevel > 0f) {
                node.pulseLevel = (node.pulseLevel - PULSE_DECAY).coerceAtLeast(0f)
            }
        }
    }

    fun triggerNodePulse(peerId: String) {
        nodes[peerId]?.pulseLevel = 1f
    }
}

@Composable
fun MeshMap(
    nodes: List<MeshNode>,
    edges: List<MeshEdge> = emptyList(),
    visualEvents: SharedFlow<MeshVisualEvent>? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val simulation = remember { Simulation() }
    val selfNodeIcon = ImageBitmap.imageResource(id = R.drawable.ic_mesh_node_icon_principal)
    val userNodeIcon = ImageBitmap.imageResource(id = R.drawable.ic_mesh_node_icon_user)
    val selfNodeTint = ColorFilter.tint(AppColors.Green)
    val userNodeTint = ColorFilter.tint(AppColors.White)
    var tick by remember { mutableStateOf(0L) }

    LaunchedEffect(nodes, edges) {
        simulation.updateTopology(nodes, edges)
    }

    LaunchedEffect(visualEvents) {
        if (visualEvents == null) return@LaunchedEffect
        visualEvents.collect { event ->
            when (event) {
                is MeshVisualEvent.PacketActivity -> simulation.triggerNodePulse(event.peerId)
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos {
                simulation.step()
                tick += 1L
            }
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val w = maxWidth.value * density.density
        val h = maxHeight.value * density.density
        SideEffect {
            simulation.width = w
            simulation.height = h
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val closest = simulation.nodes.values.minByOrNull {
                                val dx = it.x - offset.x
                                val dy = it.y - offset.y
                                dx * dx + dy * dy
                            }
                            if (closest != null) {
                                val dx = closest.x - offset.x
                                val dy = closest.y - offset.y
                                val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                                if (dist < 80f) {
                                    closest.isDragged = true
                                }
                            }
                        },
                        onDragEnd = { simulation.nodes.values.forEach { it.isDragged = false } },
                        onDragCancel = { simulation.nodes.values.forEach { it.isDragged = false } },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val dragged = simulation.nodes.values.find { it.isDragged }
                            if (dragged != null) {
                                dragged.x += dragAmount.x
                                dragged.y += dragAmount.y
                            }
                        }
                    )
                }
        ) {
            val tickValue = tick
            val nodeMap = simulation.nodes

            simulation.edges.forEach { edge ->
                val n1 = nodeMap[edge.a]
                val n2 = nodeMap[edge.b]
                if (n1 != null && n2 != null) {
                    val start = Offset(n1.x, n1.y)
                    val end = Offset(n2.x, n2.y)
                    if (edge.isConfirmed) {
                        drawLine(
                            color = AppColors.Green.copy(alpha = 0.55f),
                            start = start,
                            end = end,
                            strokeWidth = 4f,
                            cap = StrokeCap.Round
                        )
                    } else if (!edge.confirmedBy.isNullOrBlank()) {
                        val mid = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f)
                        val confirmedFromStart = edge.confirmedBy == edge.a
                        val solidStart = if (confirmedFromStart) start else end
                        val solidEnd = mid
                        val dashedStart = mid
                        val dashedEnd = if (confirmedFromStart) end else start

                        drawLine(
                            color = AppColors.Gray400.copy(alpha = 0.7f),
                            start = solidStart,
                            end = solidEnd,
                            strokeWidth = 3.5f,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = AppColors.Gray400.copy(alpha = 0.55f),
                            start = dashedStart,
                            end = dashedEnd,
                            strokeWidth = 3.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    } else {
                        drawLine(
                            color = AppColors.Gray500.copy(alpha = 0.6f),
                            start = start,
                            end = end,
                            strokeWidth = 3f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    }
                }
            }

            val labelPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = 12.sp.toPx()
            }
            val idPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = 10.sp.toPx()
            }

            nodeMap.values.forEach { node ->
                val center = Offset(node.x, node.y)
                val baseRadius = if (node.isSelf) 18f else 14f
                val signalScale = 0.7f + node.signal.coerceIn(0.1f, 1f) * 0.6f
                val pulse = node.pulseLevel.coerceIn(0f, 1f)
                val radius = baseRadius * signalScale * (1f + pulse * 0.12f)

                if (pulse > 0.05f) {
                    drawCircle(
                        color = AppColors.Green.copy(alpha = 0.45f * pulse),
                        radius = radius + 10f + (pulse * 14f),
                        center = center
                    )
                }

                val icon = if (node.isSelf) selfNodeIcon else userNodeIcon
                val iconTint = if (node.isSelf) selfNodeTint else userNodeTint
                val iconSize = (radius * 2f).toInt().coerceAtLeast(1)
                drawImage(
                    image = icon,
                    dstOffset = IntOffset((center.x - radius).toInt(), (center.y - radius).toInt()),
                    dstSize = IntSize(iconSize, iconSize),
                    colorFilter = iconTint
                )

                val labelColor = if (node.isSelf) AppColors.Green else AppColors.Gray400
                val idColor = if (node.isSelf) AppColors.Green.copy(alpha = 0.7f) else AppColors.Gray500
                val labelText = node.label?.trim()?.takeIf { it.isNotEmpty() } ?: node.id
                val shortId = node.id.take(8)
                labelPaint.color = labelColor.toArgb()
                drawContext.canvas.nativeCanvas.drawText(
                    labelText,
                    node.x + radius + 10f,
                    node.y + 4f,
                    labelPaint
                )
                if (shortId.isNotBlank() && shortId != labelText) {
                    idPaint.color = idColor.toArgb()
                    drawContext.canvas.nativeCanvas.drawText(
                        shortId,
                        node.x + radius + 10f,
                        node.y + 4f + idPaint.textSize + 6f,
                        idPaint
                    )
                }
            }
        }
    }
}
