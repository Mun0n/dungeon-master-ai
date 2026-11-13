package com.dungeonmasterai.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dungeonmasterai.BuildConfig
import com.dungeonmasterai.R
import com.dungeonmasterai.presentation.dice.DiceLaboratory
import java.util.Locale

@Composable
internal fun UnwrittenMenu(
    titles: Map<String, String>,
    loadResume: suspend () -> MenuResume?,
    discardSave: suspend () -> Unit,
    adventure: @Composable (sessionKey: Int, onLeave: () -> Unit) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(MODE_MENU) }
    var sessionKey by rememberSaveable { mutableIntStateOf(0) }
    val shown = if (mode == MODE_LAB && !BuildConfig.DEBUG) MODE_MENU else mode
    when (shown) {
        MODE_ADVENTURE -> adventure(sessionKey) { mode = MODE_MENU }
        MODE_GUIDE -> HowToPlay(onBack = { mode = MODE_MENU })
        MODE_LAB -> DiceLaboratory(onBack = { mode = MODE_MENU })
        else -> MenuHome(
            titles = titles,
            loadResume = loadResume,
            discardSave = discardSave,
            onContinue = { mode = MODE_ADVENTURE },
            onNewGame = {
                sessionKey += 1
                mode = MODE_ADVENTURE
            },
            onGuide = { mode = MODE_GUIDE },
            onLaboratory = if (BuildConfig.DEBUG) {
                { mode = MODE_LAB }
            } else {
                null
            },
        )
    }
}

@Composable
private fun MenuHome(
    titles: Map<String, String>,
    loadResume: suspend () -> MenuResume?,
    discardSave: suspend () -> Unit,
    onContinue: () -> Unit,
    onNewGame: () -> Unit,
    onGuide: () -> Unit,
    onLaboratory: (() -> Unit)?,
) {
    var resume by remember { mutableStateOf<MenuResume?>(null) }
    var ready by remember { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        resume = loadResume()
        ready = true
    }
    val scheme = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxSize().background(scheme.background)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.menu_village),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    alignment = Alignment.TopCenter,
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.38f to scheme.background.copy(alpha = 0.15f),
                                0.70f to scheme.background.copy(alpha = 0.78f),
                                0.88f to scheme.background,
                                1f to scheme.background,
                            ),
                        ),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pullUp(188.dp)
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.menu_helmet),
                    contentDescription = null,
                    modifier = Modifier.size(136.dp),
                    contentScale = ContentScale.Fit,
                )
                Text(
                    text = PlayerCopy.WORDMARK,
                    style = MaterialTheme.typography.displayLarge.copy(letterSpacing = 0.1.em),
                    color = MenuGold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = PlayerCopy.MENU_TAGLINE.uppercase(SPANISH),
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.1.em),
                    color = scheme.primary,
                    textAlign = TextAlign.Center,
                )
                publishedAdventures().forEach { published ->
                    val title = adventureTitle(published.id, titles)
                    if (title.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(scheme.surfaceContainerHigh)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = title.uppercase(SPANISH),
                                style = MaterialTheme.typography.labelMedium,
                                color = MenuGold,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    Text(
                        text = "“${PlayerCopy.MENU_QUOTE}”",
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
                    )
                    if (ready) {
                        val primary = menuPrimary(published.id, resume)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (primary is MenuPrimary.Continue) {
                                MenuAction(
                                    label = PlayerCopy.CONTINUE_GAME,
                                    detail = primary.detail,
                                    icon = ICON_CARDS,
                                    trailing = ICON_CHEVRON,
                                    background = Brush.horizontalGradient(
                                        listOf(
                                            scheme.secondaryContainer,
                                            scheme.surfaceContainerHigh,
                                            scheme.surfaceContainerHigh,
                                        ),
                                    ),
                                    well = scheme.surfaceContainerLowest,
                                    iconTint = scheme.primary,
                                    labelColor = MenuGold,
                                    trailingTint = scheme.primary,
                                    onClick = onContinue,
                                )
                            }
                            MenuAction(
                                label = PlayerCopy.NEW_GAME,
                                detail = PlayerCopy.NEW_GAME_DETAIL,
                                icon = ICON_CODEX,
                                trailing = ICON_ARROW,
                                background = Brush.horizontalGradient(
                                    listOf(scheme.surfaceContainer, scheme.surfaceContainer),
                                ),
                                well = scheme.surfaceContainerLow,
                                iconTint = scheme.onSurfaceVariant,
                                labelColor = scheme.onSurface,
                                trailingTint = MenuOutline,
                                onClick = {
                                    if (newGameErasesSave(published.id, resume)) {
                                        confirming = true
                                    } else {
                                        onNewGame()
                                    }
                                },
                            )
                            MenuAction(
                                label = PlayerCopy.HOW_TO_PLAY,
                                detail = PlayerCopy.HOW_TO_PLAY_DETAIL,
                                icon = ICON_STORIES,
                                trailing = ICON_BOOK,
                                background = Brush.horizontalGradient(
                                    listOf(scheme.surfaceContainerLow, scheme.surfaceContainerLow),
                                ),
                                well = scheme.surfaceContainer,
                                iconTint = MenuOutline,
                                labelColor = scheme.onSurface,
                                trailingTint = MenuOutline,
                                onClick = onGuide,
                            )
                            if (onLaboratory != null) {
                                MenuAction(
                                    label = PlayerCopy.LABORATORY,
                                    detail = PlayerCopy.LABORATORY_DETAIL,
                                    icon = ICON_LAB,
                                    trailing = ICON_CHEVRON,
                                    background = Brush.horizontalGradient(
                                        listOf(scheme.surfaceContainerLow, scheme.surfaceContainerLow),
                                    ),
                                    well = scheme.surfaceContainer,
                                    iconTint = MenuOutline,
                                    labelColor = scheme.onSurface,
                                    trailingTint = MenuOutline,
                                    onClick = onLaboratory,
                                )
                            }
                        }
                    }
                }
            }
            notice?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(scheme.primary.copy(alpha = 0.6f)),
                    )
                    Text(
                        text = PlayerCopy.MENU_CHIME,
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = MenuOutline,
                        textAlign = TextAlign.Center,
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(scheme.primary.copy(alpha = 0.6f)),
                    )
                }
                Text(
                    text = PlayerCopy.MENU_MARK.uppercase(SPANISH),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.1.em),
                    color = MenuOutline,
                    textAlign = TextAlign.Center,
                )
            }
        }
        }
        if (confirming) {
            AlertDialog(
                onDismissRequest = { confirming = false },
                title = { Text(PlayerCopy.CONFIRM_ERASE_TITLE) },
                text = { Text(PlayerCopy.CONFIRM_ERASE_BODY) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirming = false
                            scope.launch {
                                try {
                                    discardSave()
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    notice = PlayerCopy.DISCARD_FAILED
                                    return@launch
                                }
                                notice = null
                                onNewGame()
                            }
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(PlayerCopy.CONFIRM_ERASE)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { confirming = false },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(PlayerCopy.CANCEL)
                    }
                },
            )
        }
    }
}

@Composable
private fun MenuAction(
    label: String,
    detail: String,
    icon: String,
    trailing: String,
    background: Brush,
    well: Color,
    iconTint: Color,
    labelColor: Color,
    trailingTint: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .shadow(4.dp, shape)
            .clip(shape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(well),
            contentAlignment = Alignment.Center,
        ) {
            MenuGlyph(icon, iconTint, 24.sp)
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label.uppercase(SPANISH),
                style = MaterialTheme.typography.labelLarge,
                color = labelColor,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MenuGlyph(trailing, trailingTint, 22.sp)
    }
}

@Composable
private fun MenuGlyph(glyph: String, color: Color, size: TextUnit) {
    Text(
        text = glyph,
        color = color,
        fontSize = size,
        fontFamily = MenuIcons,
        textAlign = TextAlign.Center,
        modifier = Modifier.clearAndSetSemantics {},
    )
}

@Composable
private fun HowToPlay(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(PlayerCopy.BACK)
        }
        Text(
            text = PlayerCopy.HOW_TO_PLAY,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(text = PlayerCopy.HOW_DECISIONS, style = MaterialTheme.typography.bodyLarge)
        Text(text = PlayerCopy.HOW_DICE, style = MaterialTheme.typography.bodyLarge)
        Text(text = PlayerCopy.HOW_FATE, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun Modifier.pullUp(by: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = by.roundToPx()
    layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) {
        placeable.place(0, -shift)
    }
}

private val SPANISH: Locale = Locale.forLanguageTag("es")
private val MenuIcons: FontFamily = FontFamily(Font(R.font.material_symbols_outlined))
private val MenuGold: Color = Color(0xFFFFDEA5)
private val MenuOutline: Color = Color(0xFF9A8F80)

private const val ICON_CARDS: String = "\uF5DC"
private const val ICON_CHEVRON: String = "\uE5CC"
private const val ICON_CODEX: String = "\uEA3E"
private const val ICON_ARROW: String = "\uE5C8"
private const val ICON_STORIES: String = "\uE666"
private const val ICON_BOOK: String = "\uEA19"
private const val ICON_LAB: String = "\uEA4B"

private const val MODE_MENU: String = "menu"
private const val MODE_ADVENTURE: String = "valdumbria"
private const val MODE_GUIDE: String = "guide"
private const val MODE_LAB: String = "lab"
