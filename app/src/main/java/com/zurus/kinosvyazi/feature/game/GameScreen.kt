package com.zurus.kinosvyazi.feature.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.zurus.kinosvyazi.R
import com.zurus.kinosvyazi.core.ui.UiState
import com.zurus.kinosvyazi.domain.model.Actor
import com.zurus.kinosvyazi.domain.model.GameSession
import com.zurus.kinosvyazi.domain.model.GameSessionStatus
import com.zurus.kinosvyazi.ui.components.ActorPhoto
import com.zurus.kinosvyazi.ui.components.ChainView
import com.zurus.kinosvyazi.ui.components.UiStateView
import com.zurus.kinosvyazi.ui.navigation.NavRoutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Экран активной партии (docs/01_concept.md, §2.4, docs/05_roadmap.md, пункт 4):
 * старт, цель, текущий актёр, цепочка, число шагов, таймер;
 * кнопки «Выбрать фильм» и «Сдаться».
 *
 * При каждом появлении экрана партия перечитывается из Room
 * (после process death и после возврата из Transition).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    navController: NavHostController,
    sessionId: String,
    viewModel: GameViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(sessionId) { viewModel.load(sessionId) }
    var showSurrenderDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = { showSurrenderDialog = true }) {
                        Text(stringResource(R.string.game_surrender))
                    }
                },
            )
        },
    ) { padding ->
        UiStateView(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            onRetry = { viewModel.load(sessionId) },
        ) { content ->
            GameContent(
                content = content,
                onPickMovie = { navController.navigate(NavRoutes.moviePicker(sessionId)) },
                onSurrender = { showSurrenderDialog = true },
            )
        }
    }

    if (showSurrenderDialog) {
        AlertDialog(
            onDismissRequest = { showSurrenderDialog = false },
            title = { Text(stringResource(R.string.game_surrender_confirm_title)) },
            text = { Text(stringResource(R.string.game_surrender_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        if (viewModel.surrender()) {
                            showSurrenderDialog = false
                            navController.navigate(NavRoutes.resultNonWin(sessionId)) {
                                popUpTo(NavRoutes.HOME) { inclusive = true }
                            }
                        }
                    }
                }) {
                    Text(stringResource(R.string.game_surrender_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSurrenderDialog = false }) {
                    Text(stringResource(R.string.game_surrender_confirm_no))
                }
            },
        )
    }
}

@Composable
private fun GameContent(
    content: GameUiState,
    onPickMovie: () -> Unit,
    onSurrender: () -> Unit,
) {
    val session = content.session
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActorHeader(
                label = stringResource(R.string.game_start_actor),
                actor = session.startActor,
                modifier = Modifier.weight(1f),
            )
            ActorHeader(
                label = stringResource(R.string.game_target_actor),
                actor = session.targetActor,
                modifier = Modifier.weight(1f),
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    stringResource(R.string.game_current_actor),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActorPhoto(session.currentActor.photoUrl, Modifier.size(48.dp), sizeDp = 48)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        session.currentActor.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.game_next_hint, session.stepCount + 1, session.currentActor.name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(R.string.game_steps_count, session.stepCount),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            GameTimer(startedAtMillis = session.startedAtMillis)
        }

        Text(
            stringResource(R.string.game_chain),
            style = MaterialTheme.typography.titleMedium,
        )
        if (session.chain.isEmpty()) {
            Text(
                stringResource(R.string.game_chain_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ChainView(
                chain = session.chain,
                actors = content.chain.actors,
                movies = content.chain.movies,
                startActorId = session.startActor.tmdbId,
                targetActorId = session.targetActor.tmdbId,
                currentActorId = session.currentActor.tmdbId,
            )
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onPickMovie,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = session.status == GameSessionStatus.IN_PROGRESS,
        ) {
            Text(stringResource(R.string.game_pick_movie))
        }
        OutlinedButton(
            onClick = onSurrender,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text(stringResource(R.string.game_surrender))
        }
    }
}

@Composable
private fun ActorHeader(label: String, actor: Actor, modifier: Modifier) {
    Card(modifier = modifier) {
        Column(
            Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            ActorPhoto(actor.photoUrl, Modifier.size(44.dp), sizeDp = 44)
            Spacer(Modifier.height(6.dp))
            Text(
                actor.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Таймер партии: минуты:секунды от [GameSession.startedAtMillis]. */
@Composable
private fun GameTimer(startedAtMillis: Long) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMillis) {
        while (true) {
            delay(1_000L)
            nowMillis = System.currentTimeMillis()
        }
    }
    val totalSeconds = ((nowMillis - startedAtMillis) / 1000L).coerceAtLeast(0L)
    Text(
        text = "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60),
        style = MaterialTheme.typography.bodyLarge,
        fontFamily = FontFamily.Monospace,
    )
}
