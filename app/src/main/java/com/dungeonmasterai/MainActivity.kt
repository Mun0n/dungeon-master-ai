package com.dungeonmasterai

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dungeonmasterai.domain.adventure.load.loadAdventure
import com.dungeonmasterai.presentation.MenuResume
import com.dungeonmasterai.presentation.UnwrittenMenu
import com.dungeonmasterai.presentation.ValdumbriaScreen
import com.dungeonmasterai.presentation.ValdumbriaViewModelFactory
import com.dungeonmasterai.presentation.clockLabel
import com.dungeonmasterai.presentation.theme.DungeonMasterTheme
import com.dungeonmasterai.presentation.theme.GothicColors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(GothicColors.page.toArgb()))
        setContent {
            DungeonMasterTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val app = application as DungeonMasterApp
                    val context = LocalContext.current
                    val adventureText = remember {
                        context.assets.open(VALDUMBRIA_ASSET).bufferedReader().use { it.readText() }
                    }
                    val document = remember(adventureText) { loadAdventure(adventureText) }
                    UnwrittenMenu(
                        titles = mapOf(document.id to document.title),
                        loadResume = {
                            val record = app.adventureGameStore.load() ?: return@UnwrittenMenu null
                            val place = document.locations
                                .firstOrNull { it.id == record.state.locationId }
                                ?.name
                                .orEmpty()
                            MenuResume(
                                adventureId = record.state.adventureId,
                                place = place,
                                clock = clockLabel(record.state),
                            )
                        },
                        discardSave = { app.adventureGameStore.discard() },
                        adventure = { sessionKey, onLeave ->
                            ValdumbriaScreen(
                                onLeave = onLeave,
                                viewModel = viewModel(
                                    key = "valdumbria-$sessionKey",
                                    factory = ValdumbriaViewModelFactory(
                                        adventureText = adventureText,
                                        store = app.adventureGameStore,
                                        backend = app.adventureBackend,
                                    ),
                                ),
                            )
                        },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as DungeonMasterApp).backgroundMusic.start()
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            (application as DungeonMasterApp).backgroundMusic.pause()
        }
        super.onStop()
    }
}

private const val VALDUMBRIA_ASSET: String = "adventures/valdumbria.json"
