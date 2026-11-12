package com.dungeonmasterai.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dungeonmasterai.domain.character.Character
import com.dungeonmasterai.domain.character.CharacterClass
import com.dungeonmasterai.domain.character.Presentation
import com.dungeonmasterai.domain.game.GameEvent
import com.dungeonmasterai.domain.game.GameState
import com.dungeonmasterai.domain.game.Outcome
import com.dungeonmasterai.domain.game.PlayerIntent
import com.dungeonmasterai.domain.game.RejectionReason

@Composable
fun GameScreen(viewModel: GameViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val game = uiState.game
    when {
        !uiState.ready -> Unit
        game == null -> CharacterCreation(onStart = viewModel::start)
        else -> Encounter(
            game = game,
            onChoose = viewModel::choose,
            onSubmitText = viewModel::submitText,
        )
    }
}

@Composable
private fun CharacterCreation(onStart: (String, CharacterClass) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var selectedName by rememberSaveable { mutableStateOf(CharacterClass.WARRIOR.name) }
    val selected = CharacterClass.entries.firstOrNull { it.name == selectedName }
        ?: CharacterClass.WARRIOR
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(PlayerCopy.BRIDGE_ONGOING, style = MaterialTheme.typography.headlineMedium)
        Text(PlayerCopy.CREATE_CHARACTER, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(PlayerCopy.NAME) },
            singleLine = true,
        )
        CharacterClass.entries.forEach { characterClass ->
            val label = PlayerCopy.classCard(characterClass)
            val click = { selectedName = characterClass.name }
            if (characterClass == selected) {
                Button(onClick = click, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(label)
                }
            } else {
                OutlinedButton(onClick = click, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(label)
                }
            }
        }
        Button(
            onClick = { onStart(name, selected) },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(PlayerCopy.BEGIN)
        }
    }
}

@Composable
private fun Encounter(
    game: GameState,
    onChoose: (PlayerIntent) -> Unit,
    onSubmitText: (String) -> Unit,
) {
    var freeText by rememberSaveable { mutableStateOf("") }
    val ongoing = game.outcome == Outcome.ONGOING
    // The bridge scene accepts a written action for as long as it stays open.
    val writtenAction = ongoing
    var writing by rememberSaveable { mutableStateOf(false) }
    val character = game.character
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(headline(game.outcome), style = MaterialTheme.typography.titleMedium)
            Text(
                "${character.name}, ${PlayerCopy.className(
                    character.characterClass,
                    feminine = character.presentation == Presentation.FEMININE,
                )}  " +
                    "${PlayerCopy.LIFE} ${character.health}/${character.maxHealth}",
            )
            Text(
                "${PlayerCopy.STRENGTH} ${character.attributes.strength}   " +
                    "${PlayerCopy.AGILITY} ${character.attributes.agility}   " +
                    "${PlayerCopy.INTELLIGENCE} ${character.attributes.intelligence}",
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(latestLine(game), style = MaterialTheme.typography.bodyLarge)
            Text(PlayerCopy.INVENTORY, style = MaterialTheme.typography.titleMedium)
            val labels = character.inventory.map { itemLabel(it.id, it.name) }
            if (labels.isEmpty()) {
                Text(PlayerCopy.carrying(0))
            } else {
                Text(PlayerCopy.carrying(labels.size))
                labels.forEach { Text(it) }
            }
            Text(PlayerCopy.LOG, style = MaterialTheme.typography.titleMedium)
            game.history.forEach { event ->
                Text(event.summary(), style = MaterialTheme.typography.bodySmall)
            }
            if (ongoing) {
                ChoiceButton(PlayerCopy.PERSUADE) { onChoose(PlayerIntent.Persuade) }
                ChoiceButton(PlayerCopy.PAY) { onChoose(PlayerIntent.Pay) }
                ChoiceButton(PlayerCopy.ATTACK) { onChoose(PlayerIntent.Attack) }
                if (writtenAction && !writing) {
                    ChoiceButton(PlayerCopy.TRY_SOMETHING_ELSE) { writing = true }
                }
            }
        }
        if (writtenAction && writing) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = freeText,
                    onValueChange = { if (it.length <= PlayerCopy.TEXT_LIMIT) freeText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(PlayerCopy.BRIDGE_WRITE) },
                    minLines = 1,
                    maxLines = 4,
                )
                Button(
                    onClick = {
                        onSubmitText(freeText)
                        freeText = ""
                    },
                    enabled = freeText.isNotBlank(),
                    modifier = Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(PlayerCopy.SEND)
                }
            }
        }
    }
}

@Composable
private fun ChoiceButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label)
    }
}

private fun headline(outcome: Outcome): String = when (outcome) {
    Outcome.ONGOING -> PlayerCopy.BRIDGE_ONGOING
    Outcome.CROSSED -> PlayerCopy.BRIDGE_CROSSED
    Outcome.DEFEATED -> PlayerCopy.BRIDGE_DEFEATED
}

private fun latestLine(game: GameState): String = when (val last = game.history.last()) {
    is GameEvent.Narration -> last.text
    is GameEvent.ActionRejected -> rejectionText(last.reason)
    else -> game.history.filterIsInstance<GameEvent.Narration>().lastOrNull()?.text.orEmpty()
}

internal fun itemRemovedLine(itemId: String): String {
    val name = if (itemId == Character.COIN_PURSE_ID) PlayerCopy.COIN_PURSE else PlayerCopy.LOST_OBJECT
    return if (itemId == Character.COIN_PURSE_ID) PlayerCopy.lostItem(name) else PlayerCopy.LOST_OBJECT
}

private fun rejectionText(reason: RejectionReason): String = when (reason) {
    RejectionReason.TERMINAL -> PlayerCopy.SCENE_OVER
    RejectionReason.UNRECOGNIZED -> PlayerCopy.GUARDIAN_CONFUSED
    RejectionReason.AMBIGUOUS -> PlayerCopy.AMBIGUOUS
    RejectionReason.MISSING_ITEM -> PlayerCopy.NOTHING_TO_PAY
}

private fun GameEvent.summary(): String = when (this) {
    is GameEvent.Narration -> text
    is GameEvent.CheckResolved ->
        "${PlayerCopy.DIE} $roll · ${attributeLabel(attribute.name)} · " +
            "${PlayerCopy.MODIFIER} $modifier · ${PlayerCopy.TOTAL} $total · " +
            "${PlayerCopy.DIFFICULTY} $difficulty · " +
            if (success) PlayerCopy.CHECK_SUCCESS else PlayerCopy.CHECK_FAILURE
    is GameEvent.HealthChanged -> PlayerCopy.lifeChange(previous, current)
    is GameEvent.ItemRemoved -> itemRemovedLine(itemId)
    is GameEvent.ActionRejected -> rejectionText(reason)
}
