package com.dungeonmasterai.presentation.dice

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dungeonmasterai.domain.dice.Die
import com.dungeonmasterai.domain.dice.Difficulty
import com.dungeonmasterai.domain.dice.RandomDie
import com.dungeonmasterai.domain.dice.ScriptedDie
import com.dungeonmasterai.domain.dice.resolveCheck
import com.dungeonmasterai.presentation.CheckReading
import com.dungeonmasterai.presentation.PlayerCopy
import com.dungeonmasterai.presentation.checkReading

/**
 * Debug bench for the dice overlay. It rolls through [resolveCheck] and does not touch a save.
 */
@Composable
internal fun DiceLaboratory(onBack: () -> Unit) {
    val model = remember { DiceOverlayModel() }
    var overlay by remember { mutableStateOf(model.overlay) }
    var modifier by remember { mutableIntStateOf(3) }
    var difficulty by remember { mutableIntStateOf(Difficulty.EASY.threshold) }
    var die by remember { mutableStateOf<Die?>(null) }
    var last by remember { mutableStateOf<CheckReading?>(null) }
    BackHandler {
        if (overlay !is DiceOverlay.Closed) {
            model.close()
            overlay = model.overlay
            die = null
        } else {
            onBack()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(PlayerCopy.BACK)
            }
            Text(
                text = PlayerCopy.LABORATORY,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(text = PlayerCopy.LAB_NOTE, style = MaterialTheme.typography.bodyLarge)
            Text(text = PlayerCopy.LAB_MODIFIER, style = MaterialTheme.typography.titleMedium)
            ChoiceRow(
                values = (1..5).toList(),
                selected = modifier,
                onSelect = { modifier = it },
            )
            Text(text = PlayerCopy.DIFFICULTY, style = MaterialTheme.typography.titleMedium)
            ChoiceRow(
                values = listOf(
                    Difficulty.EASY.threshold,
                    Difficulty.MODERATE.threshold,
                    Difficulty.HARD.threshold,
                    Difficulty.VERY_HARD.threshold,
                ),
                selected = difficulty,
                onSelect = { difficulty = it },
            )
            Button(
                onClick = { openRoll(model, RandomDie(), modifier, difficulty) { die = it; overlay = model.overlay } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(PlayerCopy.LAB_FREE_ROLL)
            }
            Button(
                onClick = { openRoll(model, ScriptedDie(listOf(1)), modifier, difficulty) { die = it; overlay = model.overlay } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(PlayerCopy.LAB_NATURAL_ONE)
            }
            Button(
                onClick = { openRoll(model, ScriptedDie(listOf(20)), modifier, difficulty) { die = it; overlay = model.overlay } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(PlayerCopy.LAB_NATURAL_TWENTY)
            }
            last?.let { reading ->
                Text(
                    text = "${PlayerCopy.DIE} ${reading.roll} · ${PlayerCopy.TOTAL} ${reading.total} · ${reading.successLabel}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        if (overlay !is DiceOverlay.Closed) {
            DiceRollOverlay(
                overlay = overlay,
                onRoll = {
                    val chosen = die ?: return@DiceRollOverlay
                    val pending = model.requestThrow() ?: return@DiceRollOverlay
                    val result = resolveCheck(
                        roll = chosen.roll(),
                        attribute = pending.modifier,
                        difficulty = labDifficulty(pending.difficulty),
                    )
                    val reading = checkReading(result, pending.attribute)
                    last = reading
                    model.committed(reading, SystemClock.uptimeMillis())
                    overlay = model.overlay
                },
                onWithdraw = {
                    model.close()
                    overlay = model.overlay
                    die = null
                },
                onSkipToResult = {
                    model.hold()
                    overlay = model.overlay
                },
                onContinue = {
                    model.close()
                    overlay = model.overlay
                    die = null
                },
            )
        }
    }
}

private fun openRoll(
    model: DiceOverlayModel,
    die: Die,
    modifier: Int,
    difficulty: Int,
    publish: (Die) -> Unit,
) {
    if (model.overlay !is DiceOverlay.Closed) return
    model.open(
        PendingThrow(
            actionId = "laboratory",
            playerText = "",
            attribute = "AGILITY",
            modifier = modifier,
            difficulty = difficulty,
            revision = 0,
        ),
    )
    publish(die)
}

private fun labDifficulty(threshold: Int): Difficulty = when (threshold) {
    Difficulty.EASY.threshold -> Difficulty.EASY
    Difficulty.MODERATE.threshold -> Difficulty.MODERATE
    Difficulty.HARD.threshold -> Difficulty.HARD
    else -> Difficulty.VERY_HARD
}

@Composable
private fun ChoiceRow(values: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            TextButton(
                onClick = { onSelect(value) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    text = value.toString(),
                    color = if (value == selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}
