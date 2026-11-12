package com.dungeonmasterai.presentation

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dungeonmasterai.R
import com.dungeonmasterai.domain.character.Attributes
import com.dungeonmasterai.domain.character.Character
import com.dungeonmasterai.domain.character.CharacterClass
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

private const val PAGE_SCENE: String = "scene"
private const val PAGE_INVENTORY: String = "inventory"
private const val PAGE_JOURNAL: String = "journal"
private const val PAGE_STATUS: String = "status"

private val Brass = Color(0xFFD4A656)
private val Night = Color(0xFF14110E)
private val Dusk = Color(0xFF3A2A22)
private val Sage = Color(0xFF8C9A86)

@Composable
fun ValdumbriaScreen(
    onLeave: () -> Unit,
    viewModel: ValdumbriaViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) viewModel.reload()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val scroll = rememberScrollState()
    var page by rememberSaveable { mutableStateOf(PAGE_SCENE) }
    var consumedAlign by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = page != PAGE_SCENE) { page = PAGE_SCENE }
    when {
        !uiState.ready -> Unit
        uiState.health == null -> ValdumbriaCreation(
            notice = uiState.notice,
            onStart = viewModel::start,
            onLeave = onLeave,
        )
        else -> ValdumbriaPlay(
            uiState = uiState,
            page = page,
            onPage = { next -> page = next },
            scroll = scroll,
            consumedAlign = consumedAlign,
            onConsumedAlign = { consumedAlign = it },
            onLeave = onLeave,
            onChoose = viewModel::choose,
            onContinue = viewModel::continuePrologue,
            onSubmitText = viewModel::submitText,
            onRetryNarration = viewModel::retryNarration,
            onNoteHypothesis = viewModel::noteHypothesis,
        )
    }
}

@Composable
private fun ValdumbriaCreation(
    notice: String?,
    onStart: (String, CharacterClass, Boolean) -> Unit,
    onLeave: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var selectedName by rememberSaveable { mutableStateOf(CharacterClass.WARRIOR.name) }
    var feminine by rememberSaveable { mutableStateOf(false) }
    val selected = CharacterClass.entries.firstOrNull { it.name == selectedName }
        ?: CharacterClass.WARRIOR
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(
                    onClick = onLeave,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("←", color = scheme.primary, style = MaterialTheme.typography.headlineSmall)
                }
                Column {
                    Text(
                        text = PlayerCopy.WORDMARK,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.primary,
                    )
                    Text(
                        text = "Las campanas de Valdumbría",
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface,
                    )
                }
            }
            Text(
                text = PlayerCopy.CREATE_HEADING,
                style = MaterialTheme.typography.headlineLarge,
                color = scheme.primary,
            )
            Text(
                text = "“${PlayerCopy.CREATE_QUESTION}”",
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(PlayerCopy.ADVENTURER_NAME) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = scheme.surfaceContainer,
                    unfocusedContainerColor = scheme.surfaceContainer,
                    focusedBorderColor = scheme.primary,
                    unfocusedBorderColor = scheme.outline,
                    focusedLabelColor = scheme.primary,
                    unfocusedLabelColor = scheme.onSurfaceVariant,
                    cursorColor = scheme.primary,
                ),
            )
            notice?.let {
                Text(it, color = scheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            PortraitChoice(feminine = feminine, onChange = { feminine = it })
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = PlayerCopy.CHOOSE_CLASS.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.primary,
                )
                Text(
                    text = "${CharacterClass.entries.size} arquetipos",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            CharacterClass.entries.forEach { characterClass ->
                ClassCard(
                    characterClass = characterClass,
                    feminine = feminine,
                    selected = characterClass == selected,
                    onClick = { selectedName = characterClass.name },
                )
            }
            Text(
                text = PlayerCopy.CREATE_CHARACTER,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = { onStart(name, selected, feminine) },
            enabled = name.trim().isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .heightIn(min = 52.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = scheme.secondaryContainer,
                contentColor = scheme.onSurface,
                disabledContainerColor = scheme.secondaryContainer.copy(alpha = 0.38f),
                disabledContentColor = scheme.onSurface.copy(alpha = 0.5f),
            ),
        ) {
            Text(PlayerCopy.BEGIN_ADVENTURE.uppercase(), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PortraitChoice(feminine: Boolean, onChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = PlayerCopy.PORTRAIT.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.primary,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(scheme.surfaceContainerLow),
        ) {
            PortraitOption(PlayerCopy.PORTRAIT_MASCULINE, selected = !feminine) { onChange(false) }
            PortraitOption(PlayerCopy.PORTRAIT_FEMININE, selected = feminine) { onChange(true) }
        }
    }
}

@Composable
private fun RowScope.PortraitOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 44.dp)
            .background(if (selected) scheme.primary else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) scheme.onPrimary else scheme.onSurface,
        )
    }
}

@Composable
private fun ClassCard(
    characterClass: CharacterClass,
    feminine: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) scheme.surfaceContainer else scheme.surfaceContainerLow)
            .border(1.dp, if (selected) scheme.primary else scheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(14.dp)
            .semantics { contentDescription = PlayerCopy.classCard(characterClass, feminine) },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(classPortrait(characterClass, feminine)),
            contentDescription = null,
            modifier = Modifier
                .width(88.dp)
                .height(124.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = PlayerCopy.classRole(characterClass).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.primary,
                    )
                    Text(
                        text = PlayerCopy.className(characterClass, feminine),
                        style = MaterialTheme.typography.headlineSmall,
                        color = scheme.onSurface,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .border(1.5.dp, if (selected) scheme.primary else scheme.outline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(scheme.primary),
                        )
                    }
                }
            }
            AttributeLine(PlayerCopy.STRENGTH, characterClass.strength)
            AttributeLine(PlayerCopy.AGILITY, characterClass.agility)
            AttributeLine(PlayerCopy.INTELLIGENCE, characterClass.intelligence)
        }
    }
}

private fun classPortrait(characterClass: CharacterClass, feminine: Boolean): Int = when (characterClass) {
    CharacterClass.WARRIOR -> if (feminine) R.drawable.class_warrior_b else R.drawable.class_warrior
    CharacterClass.SCOUT -> if (feminine) R.drawable.class_scout_b else R.drawable.class_scout
    CharacterClass.SCHOLAR -> if (feminine) R.drawable.class_scholar_b else R.drawable.class_scholar
}

@Composable
private fun AttributeLine(label: String, value: Int) {
    val scheme = MaterialTheme.colorScheme
    val fraction = value.toFloat() / Attributes.RANGE.last.toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            Text(value.toString(), style = MaterialTheme.typography.labelSmall, color = scheme.primary)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(scheme.surfaceContainerHighest)
                .semantics { contentDescription = "$label $value" },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(scheme.primary),
            )
        }
    }
}

@Composable
private fun ValdumbriaPlay(
    uiState: ValdumbriaUiState,
    page: String,
    onPage: (String) -> Unit,
    scroll: androidx.compose.foundation.ScrollState,
    consumedAlign: Int,
    onConsumedAlign: (Int) -> Unit,
    onLeave: () -> Unit,
    onChoose: (String) -> Unit,
    onContinue: () -> Unit,
    onSubmitText: (String) -> Unit,
    onRetryNarration: () -> Unit,
    onNoteHypothesis: (String) -> Unit,
) {
    val playing = uiState.endingId == null && uiState.phase == AdventurePhase.IDLE
    val controlsEnabled = playing && !uiState.resolving
    var writing by rememberSaveable(uiState.locationId, uiState.freeAction) { mutableStateOf(false) }
    val reduceMotion = reducedMotion()
    var narrationY by remember { mutableIntStateOf(0) }
    var laidOutNarration by remember { mutableStateOf<String?>(null) }
    var prologueWasOpen by remember { mutableStateOf(uiState.prologue) }
    LaunchedEffect(uiState.prologue) {
        if (prologueWasOpen && !uiState.prologue) scroll.scrollTo(0)
        prologueWasOpen = uiState.prologue
    }
    LaunchedEffect(uiState.alignToken, uiState.narration) {
        val narration = uiState.narration
        if (uiState.alignToken <= consumedAlign || narration.isNullOrBlank()) return@LaunchedEffect
        snapshotFlow { laidOutNarration to narrationY }.first { it.first == narration }
        scroll.scrollTo(narrationY.coerceAtLeast(0))
        onConsumedAlign(uiState.alignToken)
    }
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        SceneHeader(uiState = uiState, onLeave = onLeave)
        if (page == PAGE_SCENE) Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LocationPicture(
                locationId = uiState.locationId,
                locationName = uiState.locationName,
                actTitle = uiState.actTitle,
                dimmed = endingPlate(uiState.endingId) == EndingPlate.DEATH,
            )
            uiState.narration?.let { narration ->
                ChronicleCard(
                    text = narration,
                    reveal = uiState.revealNarration && !reduceMotion,
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        narrationY = coordinates.positionInParent().y.roundToInt()
                        laidOutNarration = narration
                    },
                )
            }
            uiState.lifeChange?.let { Text(it) }
            uiState.inesLine?.let { line ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (uiState.inesStrong) Color(0xFF2A241C) else Color.Transparent)
                        .padding(if (uiState.inesStrong) 12.dp else 0.dp),
                ) {
                    Text(PlayerCopy.FROM_THE_RULES, style = MaterialTheme.typography.labelLarge)
                    Text(line, color = if (uiState.inesStrong) MaterialTheme.colorScheme.onSurface else Sage)
                }
            }
            if (!uiState.resolving) {
                uiState.notice?.let { Text(it) }
            }
            if (uiState.resolving) {
                ResolvingPlate(reduceMotion = reduceMotion)
            } else if (uiState.phase == AdventurePhase.NARRATION_PENDING && uiState.endingId == null) {
                Text(PlayerCopy.NARRATION_PENDING)
            } else if (uiState.phase == AdventurePhase.SCENE_PAUSED && uiState.notice == null) {
                Text(PlayerCopy.SCENE_STILL_WAITING)
            }
            if (uiState.phase == AdventurePhase.NARRATION_PENDING) {
                Button(
                    onClick = onRetryNarration,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(PlayerCopy.RETRY_NARRATION)
                }
            }
            uiState.check?.let { reading ->
                CheckStrip(reading = reading, animate = uiState.animateCheck, reduceMotion = reduceMotion)
            }
            EndingBlock(endingId = uiState.endingId, plate = endingPlate(uiState.endingId))
            if (playing && uiState.prologue) {
                DecisionRow(
                    label = PlayerCopy.CONTINUE,
                    enabled = controlsEnabled,
                    onClick = onContinue,
                )
            } else if (playing && (uiState.actionIds.isNotEmpty() || uiState.freeAction)) {
                Text(
                    text = PlayerCopy.whatDoYouDo(uiState.characterName),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.primary,
                )
                uiState.actionIds.forEach { actionId ->
                    DecisionRow(
                        label = choiceLabel(actionId),
                        enabled = controlsEnabled,
                        onClick = { onChoose(actionId) },
                    )
                }
                if (uiState.freeAction && !writing) {
                    DecisionRow(
                        label = PlayerCopy.TRY_SOMETHING_ELSE,
                        enabled = controlsEnabled,
                        onClick = { writing = true },
                    )
                }
            }
        }
        if (page == PAGE_SCENE && playing && uiState.freeAction && writing) {
            ActionComposer(enabled = controlsEnabled, onSubmit = onSubmitText)
        }
        if (page != PAGE_SCENE) {
            SectionPage(page = page, uiState = uiState, modifier = Modifier.weight(1f))
        }
        if (
            page == PAGE_JOURNAL &&
            uiState.endingId == null &&
            uiState.phase == AdventurePhase.IDLE
        ) {
            if (!uiState.resolving) {
                uiState.notice?.let { notice ->
                    Text(
                        text = notice,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HypothesisComposer(
                enabled = !uiState.resolving,
                storedCount = uiState.hypotheses.size,
                onSubmit = onNoteHypothesis,
            )
        }
        if (!keyboardOpen) {
            PlayBar(page = page, onPage = onPage)
        }
    }
}

@Composable
private fun SceneHeader(uiState: ValdumbriaUiState, onLeave: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(
                onClick = onLeave,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = PlayerCopy.OTHER_GAME },
            ) {
                Text("←", color = scheme.primary, style = MaterialTheme.typography.headlineSmall)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = PlayerCopy.WORDMARK,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.primary,
                )
                Text(
                    text = uiState.adventureTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = "${PlayerCopy.HOUR} ${uiState.clock}"
                    },
                ) {
                    AnalogClock(
                        hour = uiState.clockHour,
                        minute = uiState.clockMinute,
                        modifier = Modifier.size(60.dp),
                        boldHands = true,
                    )
                }
                if (uiState.announceMidnight) {
                    Text(
                        text = PlayerCopy.MIDNIGHT,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.primary,
                    )
                }
            }
        }
        LifePlate(health = uiState.health ?: 0, empty = uiState.endingId == "death")
    }
}

@Composable
private fun LifePlate(health: Int, empty: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val stored = health.coerceIn(0, Character.MAX_HEALTH)
    val shown = if (empty) 0 else stored
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { contentDescription = PlayerCopy.lifeAnnouncement(stored, Character.MAX_HEALTH) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = PlayerCopy.LIFE.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics {},
        )
        LifeMarks(filled = shown, modifier = Modifier.weight(1f))
        Text(
            text = "$shown/${Character.MAX_HEALTH}",
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurface,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
private fun LifeMarks(filled: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(Character.MAX_HEALTH) { index ->
            val on = index < filled
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (on) scheme.secondaryContainer else Color.Transparent)
                    .border(
                        1.dp,
                        if (on) scheme.secondaryContainer else scheme.outline,
                        RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}

@Composable
private fun LocationPicture(
    locationId: String,
    locationName: String,
    actTitle: String,
    dimmed: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val art = locationArtRes(locationId)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Night),
    ) {
        if (art != null) {
            Image(
                painter = painterResource(art),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (dimmed) 0.45f else 1f),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE60D0D16)))),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (actTitle.isNotBlank()) {
                Text(
                    text = actTitle.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.primary,
                )
            }
            if (locationName.isNotBlank()) {
                Text(
                    text = locationName,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ChronicleCard(text: String, reveal: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.surfaceContainerLow),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(scheme.primary),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = PlayerCopy.CHRONICLE.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.primary,
            )
            RevealingText(text = text, reveal = reveal)
        }
    }
}

@Composable
private fun DecisionRow(label: String, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.surfaceContainer)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurface,
        )
        Text(
            text = ICON_CHEVRON,
            color = scheme.onSurfaceVariant,
            fontSize = 20.sp,
            fontFamily = SceneIcons,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
private fun PlayBar(page: String, onPage: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
    ) {
        PlayDestination(ICON_CASTLE, PlayerCopy.SCENE, page == PAGE_SCENE) { onPage(PAGE_SCENE) }
        PlayDestination(ICON_BOOK, PlayerCopy.JOURNAL, page == PAGE_JOURNAL) { onPage(PAGE_JOURNAL) }
        PlayDestination(ICON_SHIELD, PlayerCopy.INVENTORY, page == PAGE_INVENTORY) { onPage(PAGE_INVENTORY) }
        PlayDestination(ICON_PERSON, PlayerCopy.STATUS, page == PAGE_STATUS) { onPage(PAGE_STATUS) }
    }
}

@Composable
private fun RowScope.PlayDestination(
    glyph: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val color = if (selected) scheme.primary else scheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) scheme.primary else Color.Transparent),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = glyph,
                color = color,
                fontSize = 22.sp,
                fontFamily = SceneIcons,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun SectionPage(page: String, uiState: ValdumbriaUiState, modifier: Modifier = Modifier) {
    val scroll = remember(page) { ScrollState(0) }
    Column(
        modifier = modifier
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (page) {
            PAGE_INVENTORY -> InventorySheet(uiState.items)
            PAGE_JOURNAL -> {
                Text(
                    text = PlayerCopy.JOURNAL,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                ClueSection(uiState.clues)
                EvidenceSection(uiState.evidence)
                HypothesisSection(uiState.hypotheses)
                ConclusionSection(uiState.conclusions)
                PlaceSection(uiState.places)
                RecentSection(uiState.recentLines)
            }
            else -> StatusSheet(uiState)
        }
    }
}

@Composable
private fun InventorySheet(items: List<CarriedItem>) {
    val scheme = MaterialTheme.colorScheme
    val slots = inventorySlots(items)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = PlayerCopy.INVENTORY,
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.primary,
            )
            Text(
                text = PlayerCopy.INVENTORY_LINE,
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                color = scheme.onSurfaceVariant,
            )
        }
        Text(
            text = PlayerCopy.carryingMark(items.size),
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(scheme.surfaceContainerHigh)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .semantics { contentDescription = PlayerCopy.carrying(items.size) },
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurface,
        )
    }
    slots.chunked(2).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            row.forEach { slot ->
                InventoryCard(slot, Modifier.weight(1f))
            }
            if (row.size == 1) {
                Box(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun InventoryCard(slot: InventorySlot, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val filled = slot.name != null
    val spoken = listOfNotNull(slot.name, slot.gloss).joinToString(". ")
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.surfaceContainerLowest)
            .border(1.dp, scheme.outline, RoundedCornerShape(8.dp))
            .then(
                if (filled) {
                    Modifier.semantics { contentDescription = spoken }
                } else {
                    Modifier.clearAndSetSemantics {}
                },
            ),
    ) {
        Text(
            text = slot.numeral,
            modifier = Modifier
                .padding(start = 10.dp, top = 8.dp, bottom = 6.dp)
                .clearAndSetSemantics {},
            style = MaterialTheme.typography.labelSmall,
            color = scheme.primary,
        )
        val art = slot.itemId?.let(::itemArtRes)
        Box(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (art != null) {
                Image(
                    painter = painterResource(art),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else if (!filled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, scheme.outline, RoundedCornerShape(4.dp)),
                )
            }
        }
        Text(
            text = slot.name.orEmpty(),
            modifier = Modifier
                .padding(start = 10.dp, end = 10.dp, top = 8.dp)
                .clearAndSetSemantics {},
            style = MaterialTheme.typography.bodyLarge,
            color = if (filled) scheme.onSurface else Color.Transparent,
            minLines = 1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = slot.gloss.orEmpty(),
            modifier = Modifier
                .padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 10.dp)
                .clearAndSetSemantics {},
            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
            color = if (filled && slot.gloss != null) scheme.onSurfaceVariant else Color.Transparent,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun itemArtRes(itemId: String): Int? = when (itemId) {
    Character.COIN_PURSE_ID -> R.drawable.item_coin_purse
    "iron_pin" -> R.drawable.item_iron_pin
    "linen_bandage" -> R.drawable.item_linen_bandage
    else -> null
}

@Composable
private fun StatusSheet(uiState: ValdumbriaUiState) {
    val stored = (uiState.health ?: 0).coerceIn(0, Character.MAX_HEALTH)
    val shown = if (uiState.endingId == "death") 0 else stored
    StatusIdentity(uiState)
    StatusVital(
        clock = uiState.clock,
        hour = uiState.clockHour,
        minute = uiState.clockMinute,
        shown = shown,
        stored = stored,
    )
    StatusAttributes(uiState.strength, uiState.agility, uiState.intelligence)
}

@Composable
private fun StatusIdentity(uiState: ValdumbriaUiState) {
    val scheme = MaterialTheme.colorScheme
    val characterClass = uiState.characterClass
    Row(
        modifier = Modifier.sheetCard(scheme.primary),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (characterClass != null) {
            Image(
                painter = painterResource(classPortrait(characterClass, uiState.feminine)),
                contentDescription = null,
                modifier = Modifier
                    .width(84.dp)
                    .height(108.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (characterClass != null) {
                Text(
                    text = PlayerCopy.classRole(characterClass).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.primary,
                )
            }
            if (uiState.characterName.isNotBlank()) {
                Text(
                    text = uiState.characterName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = scheme.onSurface,
                )
            }
            if (characterClass != null) {
                Text(
                    text = PlayerCopy.className(characterClass, uiState.feminine),
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = scheme.onSurfaceVariant,
                )
            }
            if (uiState.locationName.isNotBlank()) {
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = PlayerCopy.PLACE.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                    GoldChip(uiState.locationName)
                }
            }
        }
    }
}

@Composable
private fun StatusVital(clock: String, hour: Int, minute: Int, shown: Int, stored: Int) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.sheetCard(scheme.outline),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(scheme.secondaryContainer),
                )
                Text(
                    text = PlayerCopy.VITAL_STATE.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurface,
                )
            }
            Row(
                modifier = Modifier.semantics {
                    contentDescription = "${PlayerCopy.HOUR} $clock"
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = PlayerCopy.HOUR.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                GoldChip(clock, modifier = Modifier.clearAndSetSemantics {})
            }
        }
        AnalogClock(
            hour = hour,
            minute = minute,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(200.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(scheme.surfaceContainerLow)
                .border(1.dp, scheme.outline, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .semantics { contentDescription = PlayerCopy.lifeAnnouncement(stored, Character.MAX_HEALTH) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = PlayerCopy.LIFE.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.clearAndSetSemantics {},
            )
            LifeMarks(filled = shown, modifier = Modifier.weight(1f))
            Text(
                text = "$shown/${Character.MAX_HEALTH}",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurface,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

@Composable
private fun StatusAttributes(strength: Int, agility: Int, intelligence: Int) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.sheetCard(scheme.outline),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = PlayerCopy.ATTRIBUTES_HEADING.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurface,
            )
            Text(
                text = PlayerCopy.ATTRIBUTE_SCALE,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
        StatusAttribute(PlayerCopy.STRENGTH, strength)
        StatusAttribute(PlayerCopy.AGILITY, agility)
        StatusAttribute(PlayerCopy.INTELLIGENCE, intelligence)
    }
}

@Composable
private fun StatusAttribute(label: String, value: Int) {
    val scheme = MaterialTheme.colorScheme
    val fraction = value.toFloat() / Attributes.RANGE.last.toFloat()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(scheme.surfaceContainerHighest)
                .clearAndSetSemantics {},
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(scheme.primary),
            )
        }
    }
}

@Composable
private fun GoldChip(text: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = text,
        modifier = modifier
            .border(1.dp, scheme.primary, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = scheme.primary,
    )
}

@Composable
private fun Modifier.sheetCard(border: Color): Modifier {
    val shape = RoundedCornerShape(8.dp)
    return this
        .fillMaxWidth()
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .border(1.dp, border, shape)
        .padding(12.dp)
}

@Composable
private fun JournalHeading(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun JournalEmpty(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ClueSection(clues: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        JournalHeading(PlayerCopy.CLUES)
        if (clues.isEmpty()) {
            JournalEmpty(PlayerCopy.NO_CLUES)
        } else {
            clues.forEachIndexed { index, clue -> ClueRow(index + 1, clue) }
        }
    }
}

@Composable
private fun ClueRow(position: Int, clue: String) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = clueNumeral(position),
                modifier = Modifier
                    .widthIn(min = 28.dp)
                    .clearAndSetSemantics {},
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary,
            )
            Text(
                text = clue,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(scheme.outline),
        )
    }
}

@Composable
private fun HypothesisSection(hypotheses: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        JournalHeading(PlayerCopy.HYPOTHESES)
        JournalEmpty(PlayerCopy.HYPOTHESIS_NOTE)
        if (hypotheses.isEmpty()) {
            JournalEmpty(PlayerCopy.NO_HYPOTHESES)
        } else {
            hypotheses.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ConclusionSection(conclusions: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        JournalHeading(PlayerCopy.CONCLUSIONS)
        if (conclusions.isEmpty()) {
            JournalEmpty(PlayerCopy.NO_CONCLUSIONS)
        } else {
            conclusions.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun EvidenceSection(evidence: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        JournalHeading(PlayerCopy.EVIDENCE)
        if (evidence.isEmpty()) {
            JournalEmpty(PlayerCopy.NO_EVIDENCE)
        } else {
            evidence.forEach { line -> EvidencePlate(line) }
        }
    }
}

@Composable
private fun EvidencePlate(line: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, scheme.primary, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.surfaceContainerLow)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = line,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurface,
        )
        EvidenceSeal()
    }
}

@Composable
private fun EvidenceSeal() {
    val gold = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(36.dp)
            .clearAndSetSemantics {}
            .border(1.5.dp, gold, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .border(1.dp, gold.copy(alpha = 0.7f), CircleShape),
        )
    }
}

@Composable
private fun PlaceSection(places: List<DiscoveredPlace>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        JournalHeading(PlayerCopy.PLACES)
        if (places.isEmpty()) {
            JournalEmpty(PlayerCopy.NO_PLACES)
        } else {
            places.forEach { place -> PlaceRow(place) }
        }
    }
}

@Composable
private fun PlaceRow(place: DiscoveredPlace) {
    val scheme = MaterialTheme.colorScheme
    val art = locationArtRes(place.id)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.surfaceContainer)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (art != null) {
            Image(
                painter = painterResource(art),
                contentDescription = null,
                modifier = Modifier
                    .size(width = 84.dp, height = 64.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        Text(
            text = place.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
        )
    }
}

@Composable
private fun RecentSection(lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        JournalHeading(PlayerCopy.RECENT)
        if (lines.isEmpty()) {
            JournalEmpty(PlayerCopy.NO_RECENT)
        } else {
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun RevealingText(text: String, reveal: Boolean, modifier: Modifier = Modifier) {
    var shown by rememberSaveable(text) { mutableIntStateOf(if (reveal) 0 else text.length) }
    LaunchedEffect(text, reveal) {
        if (!reveal) {
            shown = text.length
            return@LaunchedEffect
        }
        while (shown < text.length) {
            shown += 1
            kotlinx.coroutines.delay(12)
        }
    }
    Text(
        text = text.take(shown),
        modifier = modifier.clickable { shown = text.length },
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun CheckStrip(reading: CheckReading, animate: Boolean, reduceMotion: Boolean) {
    var face by rememberSaveable(reading.roll, animate) {
        mutableIntStateOf(openingDieFace(reading.roll, animate, reduceMotion))
    }
    var settled by rememberSaveable(reading.roll, animate) { mutableStateOf(!animate || reduceMotion) }
    LaunchedEffect(reading.roll, animate, reduceMotion) {
        if (!animate || reduceMotion || settled) {
            face = reading.roll
            settled = true
            return@LaunchedEffect
        }
        listOf(1, 8, 15, reading.roll).distinct().forEach { step ->
            if (settled) return@forEach
            face = step
            kotlinx.coroutines.delay(180)
        }
        face = reading.roll
        settled = true
    }
    val scheme = MaterialTheme.colorScheme
    val shown = if (settled) reading.roll else face
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(scheme.surfaceContainer)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(scheme.primary)
                    .clickable {
                        face = reading.roll
                        settled = true
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = shown.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = scheme.onPrimary,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(reading.successLabel, style = MaterialTheme.typography.titleSmall, color = scheme.primary)
                Text(
                    "${PlayerCopy.DIE} $shown · ${PlayerCopy.ATTRIBUTE} ${reading.attributeLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    "${PlayerCopy.MODIFIER} ${reading.modifier} · ${PlayerCopy.TOTAL} ${reading.total} · ${PlayerCopy.DIFFICULTY} ${reading.difficulty}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        if (!settled) {
            TextButton(onClick = {
                face = reading.roll
                settled = true
            }) {
                Text(PlayerCopy.SKIP_ROLL)
            }
        }
    }
}

@Composable
private fun EndingBlock(endingId: String?, plate: EndingPlate) {
    if (endingId == null || plate == EndingPlate.QUIET) return
    val title = endingTitle(endingId)
    when (plate) {
        EndingPlate.JUSTICE -> Text(
            title,
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, Brass)
                .padding(12.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        EndingPlate.VENGEANCE -> Text(
            title,
            modifier = Modifier
                .fillMaxWidth()
                .background(Night)
                .border(1.dp, Brass)
                .padding(16.dp),
            style = MaterialTheme.typography.headlineSmall,
        )
        EndingPlate.SILENCE -> Text(
            title,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF243028))
                .padding(12.dp),
            style = MaterialTheme.typography.titleSmall,
            color = Sage,
        )
        EndingPlate.FAILURE -> Column(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().height(28.dp).background(Dusk)) {}
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        }
        EndingPlate.DEATH -> Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            LifeMarks(filled = 0)
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        }
        EndingPlate.QUIET -> Unit
    }
}

@Composable
private fun HypothesisComposer(enabled: Boolean, storedCount: Int, onSubmit: (String) -> Unit) {
    var theory by rememberSaveable { mutableStateOf("") }
    var baseline by rememberSaveable { mutableIntStateOf(storedCount) }
    LaunchedEffect(storedCount) {
        if (storedCount > baseline) {
            theory = ""
            baseline = storedCount
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = theory,
            onValueChange = { if (it.length <= PlayerCopy.TEXT_LIMIT) theory = it },
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = PlayerCopy.HYPOTHESIS_HINT },
            placeholder = { Text(PlayerCopy.HYPOTHESIS_HINT) },
            minLines = 1,
            maxLines = 4,
            enabled = enabled,
        )
        Button(
            onClick = { onSubmit(theory) },
            enabled = enabled && theory.isNotBlank(),
            modifier = Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(PlayerCopy.NOTE_HYPOTHESIS)
        }
    }
}

@Composable
private fun ActionComposer(enabled: Boolean, onSubmit: (String) -> Unit) {
    var freeText by rememberSaveable { mutableStateOf("") }
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
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = PlayerCopy.textLimitAnnouncement() },
            placeholder = { Text(PlayerCopy.WRITE_ACTION) },
            minLines = 1,
            maxLines = 4,
            enabled = enabled,
        )
        Button(
            onClick = {
                onSubmit(freeText)
                freeText = ""
            },
            enabled = enabled && freeText.isNotBlank(),
            modifier = Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(PlayerCopy.SEND)
        }
    }
}

@Composable
private fun ResolvingPlate(reduceMotion: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val cycle = if (reduceMotion) null else resolvingCycle()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, scheme.outline, shape)
            .background(scheme.surfaceContainerLow, shape)
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .clearAndSetSemantics {
                contentDescription = PlayerCopy.RESOLVING
                liveRegion = LiveRegionMode.Polite
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = ICON_QUILL,
                color = scheme.primary,
                fontSize = 20.sp,
                lineHeight = 20.sp,
                fontFamily = SceneIcons,
                modifier = Modifier.graphicsLayer { alpha = quillMarkAlpha(cycle) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RESOLVING_MARK_DELAYS.forEach { delay ->
                    PulsingMark(pulse = resolvingMark(cycle?.let { glyphCrest(it, delay) }))
                }
            }
        }
        Text(
            text = resolvingPlateLabel(PlayerCopy.RESOLVING),
            style = MaterialTheme.typography.labelMedium.copy(
                fontStyle = FontStyle.Italic,
                letterSpacing = 0.1.em,
            ),
            color = scheme.primary.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun resolvingCycle(): Float {
    val transition = rememberInfiniteTransition(label = "resolving")
    val cycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = RESOLVING_PERIOD_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cycle",
    )
    return cycle
}

@Composable
private fun PulsingMark(pulse: ResolvingPulse) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .size(22.dp)
            .graphicsLayer {
                scaleX = pulse.scale
                scaleY = pulse.scale
                alpha = pulse.alpha
            },
    ) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.7f), Color.Transparent),
                center = center,
                radius = size.minDimension * 0.48f,
            ),
            radius = size.minDimension * 0.48f,
        )
        drawPath(sparkPath(size), color)
    }
}

private fun sparkPath(size: Size): Path {
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val outer = size.minDimension * 0.32f
    val inner = outer * 0.36f
    return Path().apply {
        moveTo(centerX, centerY - outer)
        lineTo(centerX + inner, centerY - inner)
        lineTo(centerX + outer, centerY)
        lineTo(centerX + inner, centerY + inner)
        lineTo(centerX, centerY + outer)
        lineTo(centerX - inner, centerY + inner)
        lineTo(centerX - outer, centerY)
        lineTo(centerX - inner, centerY - inner)
        close()
    }
}

@Composable
internal fun reducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val scale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    return scale == 0f
}

internal data class ResolvingPulse(val alpha: Float, val scale: Float)

internal const val RESOLVING_PERIOD_MILLIS: Int = 1500

internal val RESOLVING_MARK_DELAYS: List<Float> = listOf(0f, 0.2f, 0.4f)

internal fun resolvingPlateLabel(sentence: String): String =
    sentence.removeSuffix(".").uppercase(Locale.ROOT) + "..."

internal fun glyphCrest(cycle: Float, delayFraction: Float): Float {
    val phase = ((cycle - delayFraction) % 1f + 1f) % 1f
    val half = if (phase <= 0.5f) phase / 0.5f else (1f - phase) / 0.5f
    return resolvingEase(half)
}

internal fun glyphPulse(crest: Float): ResolvingPulse {
    val amount = crest.coerceIn(0f, 1f)
    return ResolvingPulse(alpha = 0.25f + 0.75f * amount, scale = 0.92f + 0.16f * amount)
}

internal fun resolvingMark(crest: Float?): ResolvingPulse =
    if (crest == null) ResolvingPulse(alpha = 0.8f, scale = 1f) else glyphPulse(crest)

internal fun quillAlpha(cycle: Float): Float = 0.35f + 0.5f * glyphCrest(cycle, delayFraction = 0f)

internal fun quillMarkAlpha(cycle: Float?): Float = if (cycle == null) 0.8f else quillAlpha(cycle)

private fun resolvingEase(t: Float): Float {
    val amount = t.coerceIn(0f, 1f)
    return amount * amount * (3f - 2f * amount)
}

private const val ICON_QUILL: String = "\uEA3E"
private const val ICON_CHEVRON: String = "\uE5CC"
private const val ICON_CASTLE: String = "\uEAB1"
private const val ICON_BOOK: String = "\uEA19"
private const val ICON_SHIELD: String = "\uE9E0"
private const val ICON_PERSON: String = "\uF0D3"
private val SceneIcons: FontFamily = FontFamily(Font(R.font.material_symbols_outlined))

internal fun choiceLabel(actionId: String): String = when (actionId) {
    "read_elias_letter" -> "Leer la carta de Elia"
    "speak_with_ines" -> "Hablar con Inés"
    "look_over_the_square" -> "Observar la plaza"
    "go_to_tavern" -> "Ir a la taberna"
    "ask_mara" -> "Preguntar a Mara"
    "sit_with_the_regulars" -> "Sentarse con los habituales"
    "go_to_plaza" -> "Volver a la plaza"
    "warn_ines" -> "Avisar a Inés"
    "speak_with_beltran" -> "Hablar con Don Beltrán"
    "note_suspicion" -> "Anotar una sospecha"
    "accuse_beltran" -> "Acusar a Don Beltrán"
    "leave_valdumbria" -> "Abandonar Valdumbría"
    "study_the_dark_tower" -> "Estudiar la torre oscura"
    "follow_elias_directions" -> "Buscar el archivo del pueblo"
    "enter_the_church" -> "Entrar en la iglesia"
    "enter_the_archive" -> "Entrar en el archivo"
    "enter_the_cemetery" -> "Entrar en el cementerio"
    "follow_the_path_behind_the_inn" -> "Seguir el camino detrás de la taberna"
    "ask_mara_for_linen" -> "Pedir lino a Mara"
    "bind_a_wound" -> "Vendar una herida"
    "mara_kept_the_packet" -> "Preguntar por el paquete que guardaba Mara"
    "accuse_mara" -> "Acusar a Mara"
    "leave_the_tavern" -> "Salir de la taberna"
    "search_vestry" -> "Registrar la sacristía"
    "take_the_hidden_list" -> "Coger la lista oculta"
    "bar_the_vestry" -> "Cerrar el paso en la sacristía"
    "accuse_alden_early" -> "Acusar a Padre Aldren"
    "publish_the_truth" -> "Publicar la verdad"
    "take_vengeance" -> "Tomar venganza"
    "keep_the_secret" -> "Guardar el secreto"
    "leave_the_church" -> "Salir de la iglesia"
    "ask_tomas" -> "Preguntar a Tomás cómo murió Elia"
    "follow_the_sinking_ground" -> "Seguir el suelo hundido"
    "read_burial_list" -> "Leer la lista de enterramientos"
    "cut_alden_off" -> "Cortar el paso a Padre Aldren"
    "accuse_tomas" -> "Acusar a Tomás"
    "enter_the_crypt" -> "Entrar en la cripta"
    "leave_the_cemetery" -> "Salir del cementerio"
    "read_elias_notes" -> "Leer las notas de Elia"
    "compare_registers" -> "Comparar los registros"
    "leave_the_archive" -> "Salir del archivo"
    "examine_sealed_niche" -> "Examinar el nicho sellado"
    "trace_the_scratches" -> "Seguir las marcas"
    "break_the_seal" -> "Romper el sello"
    "descend_the_open_drop" -> "Bajar por el hueco abierto"
    "return_to_the_cemetery" -> "Volver al cementerio"
    else -> PlayerCopy.ACTION_FALLBACK
}

internal fun endingTitle(endingId: String): String = when (endingId) {
    "justice" -> "Final: la verdad queda publicada"
    "vengeance" -> "Final: la venganza cae sobre el culpable"
    "silence" -> "Final: el secreto permanece"
    "failure" -> "Final: Valdumbría queda atrás"
    "death" -> "Final: la caída acaba contigo"
    else -> "Final."
}
