package com.dungeonmasterai.presentation.dice

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.dungeonmasterai.presentation.PlayerCopy
import com.dungeonmasterai.presentation.attributeLabel
import com.dungeonmasterai.presentation.reducedMotion
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun DiceRollOverlay(
    overlay: DiceOverlay,
    onRoll: () -> Unit,
    onWithdraw: () -> Unit,
    onSkipToResult: () -> Unit,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val reduceMotion = reducedMotion()
    val feedback = remember(context) {
        DiceFeedback(haptics = HapticDiceFeedback(context))
    }
    val accelerometer = remember(context) {
        context.getSystemService(SensorManager::class.java)
            ?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
    }
    val scrim = remember { Animatable(0f) }
    var pose by remember { mutableStateOf(D20Choreography.idle(0f)) }
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        scrim.animateTo(0.66f, tween(if (reduceMotion) 1 else 420))
    }
    LaunchedEffect(leaving) {
        if (!leaving) return@LaunchedEffect
        scrim.animateTo(0f, tween(if (reduceMotion) 1 else 280))
        onContinue()
    }
    DisposableEffect(overlay is DiceOverlay.Waiting, accelerometer) {
        if (overlay !is DiceOverlay.Waiting || !accelerometer) {
            return@DisposableEffect onDispose { }
        }
        val sensors = context.getSystemService(SensorManager::class.java)
        val sensor = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensors == null || sensor == null) return@DisposableEffect onDispose { }
        val tracker = ShakeTracker().also { it.arm(SystemClock.elapsedRealtime()) }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val values = event.values
                if (tracker.sample(values[0], values[1], values[2], SystemClock.elapsedRealtime())) {
                    feedback.onCue(DiceCue.ShakeAccepted)
                    onRoll()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sensors.unregisterListener(listener) }
    }
    LaunchedEffect(overlay) {
        when (val current = overlay) {
            is DiceOverlay.Waiting -> {
                feedback.onCue(DiceCue.Appear)
                val started = SystemClock.uptimeMillis()
                while (isActive) {
                    pose = D20Choreography.idle((SystemClock.uptimeMillis() - started) / 1000f)
                    delay(16)
                }
            }
            is DiceOverlay.Playing -> {
                if (reduceMotion) {
                    pose = D20Choreography.poseAt(1f, current.reading.roll)
                    naturalAccent(current.reading.roll)?.let(feedback::onCue)
                    onSkipToResult()
                    return@LaunchedEffect
                }
                var previous = 0f
                while (isActive) {
                    val elapsed = SystemClock.uptimeMillis() - current.startedAtMs
                    val progress = elapsed.toFloat() / D20Choreography.TUMBLE_MS.toFloat()
                    val clamped = progress.coerceAtMost(1f)
                    pose = D20Choreography.poseAt(clamped, current.reading.roll)
                    D20Choreography.cuesCrossing(previous, clamped).forEach(feedback::onCue)
                    previous = clamped
                    if (progress >= 1f) {
                        naturalAccent(current.reading.roll)?.let(feedback::onCue)
                        onSkipToResult()
                        break
                    }
                    delay(16)
                }
            }
            is DiceOverlay.Holding -> {
                pose = D20Choreography.poseAt(1f, current.reading.roll)
                delay(D20Choreography.HOLD_MS)
                if (isActive && !leaving) leaving = true
            }
            else -> Unit
        }
    }
    val pending = when (overlay) {
        is DiceOverlay.Waiting -> overlay.pending
        is DiceOverlay.Throwing -> overlay.pending
        is DiceOverlay.Playing -> overlay.pending
        is DiceOverlay.Holding -> overlay.pending
        DiceOverlay.Closed -> return
    }
    val reading = when (overlay) {
        is DiceOverlay.Playing -> overlay.reading
        is DiceOverlay.Holding -> overlay.reading
        else -> null
    }
    val heading = PlayerCopy.checkHeading(attributeLabel(pending.attribute))
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(overlay, leaving) {
                detectTapGestures {
                    when {
                        leaving -> Unit
                        overlay is DiceOverlay.Playing -> onSkipToResult()
                        overlay is DiceOverlay.Holding -> leaving = true
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrim.value)),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (leaving) scrim.value / 0.66f else 1f }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = heading,
                style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 0.08.em),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Reliquary {
                Text(
                    text = PlayerCopy.DIFFICULTY.uppercase(SPANISH),
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.28.em),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = pending.difficulty.toString(),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 60.sp,
                        lineHeight = 64.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.em,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(168.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    GoldBraid(modifier = Modifier.fillMaxWidth().height(8.dp))
                    FilamentDie(pose = pose, modifier = Modifier.size(148.dp))
                }
                when {
                    reading != null && overlay is DiceOverlay.Holding -> ResultPlate(reading)
                    overlay is DiceOverlay.Waiting -> Text(
                        text = if (accelerometer) PlayerCopy.SHAKE_TO_ROLL else PlayerCopy.TAP_TO_ROLL,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    else -> Spacer(modifier = Modifier.height(40.dp))
                }
            }
            if (overlay is DiceOverlay.Waiting) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        feedback.onCue(DiceCue.ShakeAccepted)
                        onRoll()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(PlayerCopy.ROLL_DIE)
                }
                TextButton(onClick = onWithdraw, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(PlayerCopy.WITHDRAW_HAND)
                }
            }
        }
    }
}

@Composable
private fun Reliquary(content: @Composable () -> Unit) {
    val gold = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, gold.copy(alpha = 0.85f), shape),
    ) {
        Astrolabe(modifier = Modifier.matchParentSize(), color = gold)
        FrameCorners(modifier = Modifier.matchParentSize(), color = gold)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
        }
    }
}

@Composable
private fun ResultPlate(reading: com.dungeonmasterai.presentation.CheckReading) {
    val wash = when (reading.roll) {
        1 -> Color(0xFF8B1E2D)
        20 -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }
    val description = listOf(
        reading.successLabel,
        "${PlayerCopy.DIE} ${reading.roll}",
        "${PlayerCopy.MODIFIER} ${signed(reading.modifier)}",
        "${PlayerCopy.TOTAL} ${reading.total}",
        "${PlayerCopy.DIFFICULTY} ${reading.difficulty}",
    ).joinToString(". ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(wash.copy(alpha = if (reading.roll == 1 || reading.roll == 20) 0.28f else 0f))
            .padding(vertical = 8.dp)
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = reading.successLabel,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "${PlayerCopy.DIE} ${reading.roll}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "${PlayerCopy.MODIFIER} ${signed(reading.modifier)} · ${PlayerCopy.TOTAL} ${reading.total}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Astrolabe(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width, size.height) * 0.38f
        val ink = color.copy(alpha = 0.07f)
        drawCircle(ink, radius = radius, center = center, style = Stroke(width = 1.dp.toPx()))
        drawCircle(ink, radius = radius * 0.72f, center = center, style = Stroke(width = 1.dp.toPx()))
        drawCircle(ink, radius = radius * 0.46f, center = center, style = Stroke(width = 1.dp.toPx()))
        drawLine(ink, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), strokeWidth = 1.dp.toPx())
        drawLine(ink, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), strokeWidth = 1.dp.toPx())
    }
}

@Composable
private fun FrameCorners(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val inset = 6.dp.toPx()
        val leg = 16.dp.toPx()
        val stroke = Stroke(width = 1.5.dp.toPx())
        listOf(
            Offset(inset, inset) to Offset(1f, 1f),
            Offset(size.width - inset, inset) to Offset(-1f, 1f),
            Offset(inset, size.height - inset) to Offset(1f, -1f),
            Offset(size.width - inset, size.height - inset) to Offset(-1f, -1f),
        ).forEach { (origin, direction) ->
            val path = Path().apply {
                moveTo(origin.x, origin.y + leg * direction.y)
                quadraticTo(origin.x, origin.y, origin.x + leg * direction.x, origin.y)
            }
            drawPath(path, color, style = stroke)
            drawCircle(
                color = color,
                radius = 2.dp.toPx(),
                center = Offset(origin.x + 7.dp.toPx() * direction.x, origin.y + 7.dp.toPx() * direction.y),
            )
        }
    }
}

@Composable
private fun GoldBraid(modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val mid = size.height / 2f
        val step = 22.dp.toPx()
        val crest = size.height / 2f
        val over = Path()
        val under = Path()
        over.moveTo(0f, mid)
        under.moveTo(0f, mid)
        var x = 0f
        var rise = true
        while (x < size.width) {
            val next = (x + step).coerceAtMost(size.width)
            val control = (x + next) / 2f
            val lift = if (rise) -crest else crest
            over.quadraticTo(control, mid + lift, next, mid)
            under.quadraticTo(control, mid - lift, next, mid)
            x = next
            rise = !rise
        }
        val stroke = Stroke(width = 1.2.dp.toPx())
        drawPath(over, color.copy(alpha = 0.9f), style = stroke)
        drawPath(under, color.copy(alpha = 0.9f), style = stroke)
    }
}

@Composable
private fun FilamentDie(pose: DiePose, modifier: Modifier) {
    val context = LocalContext.current
    val renderer = remember(context) { FilamentDieRenderer(context) }
    DisposableEffect(renderer) {
        onDispose { renderer.destroy() }
    }
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            TextureView(viewContext).apply {
                isOpaque = false
                surfaceTextureListener = renderer
            }
        },
        update = { renderer.pose = pose },
    )
}

private fun signed(value: Int): String = if (value >= 0) "+$value" else value.toString()

private val SPANISH: Locale = Locale.forLanguageTag("es")
