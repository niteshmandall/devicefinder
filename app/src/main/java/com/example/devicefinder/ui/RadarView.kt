package com.example.devicefinder.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.devicefinder.ble.SeenDevice
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * A Radar-style view that displays devices as blips on a circular grid.
 *
 * @param devices List of devices to display.
 * @param maxDistanceMeters The distance represented by the edge of the radar.
 */
@Composable
fun RadarView(
    devices: List<SeenDevice>,
    maxDistanceMeters: Double = 20.0,
    onDeviceSelected: (SeenDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarRotation"
    )

    // Gestures State
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    
    val state = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.5f, 5f)
        offset += panChange
    }

    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    val blipColor = MaterialTheme.colorScheme.primary
    val wifiBlipColor = MaterialTheme.colorScheme.secondary
    val sweepColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)

    Box(
        modifier = modifier
            .clip(RectangleShape) // Clip to bounds
            .onSizeChanged { size = it }
            .transformable(state = state)
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                     if (size == IntSize.Zero) return@detectTapGestures
                     
                     val center = Offset(size.width / 2f, size.height / 2f)
                     val radius = min(size.width, size.height) / 2f
                     
                     // Helper map to find clicked device
                     val clickTolerance = 40f // px
                     
                     val clickedDevice = devices.find { device ->
                        val angleDegrees = abs(device.id.hashCode() % 360).toFloat()
                        val angleRad = Math.toRadians(angleDegrees.toDouble())
                        
                        val exponent = (-59 - device.rssi) / (10.0 * 2.7)
                        val dist = 10.0.pow(exponent)
                        val normalizedDist = (dist / maxDistanceMeters).coerceIn(0.0, 1.0).toFloat()
                        val deviceRadius = radius * normalizedDist
                        
                        val internalX = deviceRadius * cos(angleRad).toFloat()
                        val internalY = deviceRadius * sin(angleRad).toFloat()
                        
                        // Project internal point to screen space
                        val screenX = center.x + offset.x + (internalX * scale)
                        val screenY = center.y + offset.y + (internalY * scale)
                        
                        val dx = screenX - tapOffset.x
                        val dy = screenY - tapOffset.y
                        (dx * dx + dy * dy) < (clickTolerance * clickTolerance)
                     }
                     
                     if (clickedDevice != null) {
                         onDeviceSelected(clickedDevice)
                     }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = center + offset
            val radius = (min(size.width, size.height) / 2) * scale
            
            // Draw grid circles (Scale checks)
            for (i in 1..4) {
               drawCircle(
                    color = gridColor,
                    radius = radius * (i / 4f),
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            
            // Draw standard crosshairs
            // We just draw long lines that cover the screen, centered on 'center'
            val bigSize = max(size.width, size.height) * 2
            drawLine(
                color = gridColor,
                start = Offset(center.x, center.y - bigSize),
                end = Offset(center.x, center.y + bigSize),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = gridColor,
                start = Offset(center.x - bigSize, center.y),
                end = Offset(center.x + bigSize, center.y),
                strokeWidth = 1.dp.toPx()
            )

            // Draw Rotating Sweep (Fixed at center of "radar", so it moves with pan/zoom)
            // Note: Gradient scaling with zoom is tricky, simpler to just draw circle
            rotate(rotation, center) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(Color.Transparent, sweepColor)
                    ),
                    radius = radius,
                    center = center
                )
            }

            // Draw Devices and Labels
            devices.forEach { device ->
                val angleDegrees = abs(device.id.hashCode() % 360).toFloat()
                val angleRad = Math.toRadians(angleDegrees.toDouble())

                val exponent = (-59 - device.rssi) / (10.0 * 2.7)
                val dist = 10.0.pow(exponent)
                val normalizedDist = (dist / maxDistanceMeters).coerceIn(0.0, 1.0).toFloat()
                
                // Device Internal Logic
                val deviceRadius = radius * normalizedDist

                val x = center.x + (deviceRadius * cos(angleRad).toFloat())
                val y = center.y + (deviceRadius * sin(angleRad).toFloat())

                drawCircle(
                    color = if (device.source == "WiFi") wifiBlipColor else blipColor,
                    radius = (6.dp.toPx() * scale).coerceAtMost(30.dp.toPx()).coerceAtLeast(4.dp.toPx()), // Scale blips slightly but clamp
                    center = Offset(x, y)
                )
            }
        }
    }
}

// Extension to get center of IntSize
fun androidx.compose.ui.unit.IntSize.center(): androidx.compose.ui.unit.IntOffset {
    return androidx.compose.ui.unit.IntOffset(width / 2, height / 2)
}
fun androidx.compose.ui.unit.IntOffset.toOffset(): Offset {
    return Offset(x.toFloat(), y.toFloat())
}
