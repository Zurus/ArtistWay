package com.zurus.cinethread.feature.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.zurus.cinethread.R
import com.zurus.cinethread.core.ui.UiState
import com.zurus.cinethread.domain.model.Actor
import com.zurus.cinethread.domain.model.GameSessionStatus
import com.zurus.cinethread.feature.error.ErrorType
import com.zurus.cinethread.ui.components.ActorPhoto
import com.zurus.cinethread.ui.components.ChainView
import com.zurus.cinethread.ui.components.UiStateView
import com.zurus.cinethread.ui.navigation.NavRoutes

/**
 * Экран результата — не-победа (docs/01_concept.md, §2.9):
 * «Партия завершена» (сдача или тупик), частичная цепочка, метрики,
 * «Новая партия» / «В меню».
 */
@Composable
fun GameResultNonWinScreen(
    navController: NavHostController,
    sessionId: String,
    viewModel: GameResultNonWinViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(sessionId) {
        viewModel.load(sessionId) {
            navController.navigate(NavRoutes.error(ErrorType.NOT_FOUND))
        }
    }

    UiStateView(
        state = state,
        modifier = Modifier.fillMaxSize(),
        onRetry = { viewModel.load(sessionId) },
    ) { content ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.result_nonwin_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(nonWinReasonTextId(content.session.status)),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NonWinActorCard(
                    label = stringResource(R.string.game_start_actor),
                    actor = content.session.startActor,
                    modifier = Modifier.weight(1f),
                )
                NonWinActorCard(
                    label = stringResource(R.string.game_target_actor),
                    actor = content.session.targetActor,
                    modifier = Modifier.weight(1f),
                )
            }
            ResultMetrics(
                stepCount = content.session.stepCount,
                startedAtMillis = content.session.startedAtMillis,
                finishedAtMillis = content.session.finishedAtMillis,
            )
            if (content.session.chain.isEmpty()) {
                Text(
                    stringResource(R.string.result_nonwin_empty_chain),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    stringResource(R.string.result_nonwin_partial_chain),
                    style = MaterialTheme.typography.titleMedium,
                )
                ChainView(
                    chain = content.session.chain,
                    actors = content.chain.actors,
                    movies = content.chain.movies,
                    startActorId = content.session.startActor.tmdbId,
                    targetActorId = content.session.targetActor.tmdbId,
                )
            }
            Button(
                onClick = {
                    navController.navigate(NavRoutes.GAME_SETUP) {
                        popUpTo(NavRoutes.HOME) { inclusive = true }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.result_new_game))
            }
            OutlinedButton(
                onClick = {
                    navController.navigate(NavRoutes.HOME) {
                        popUpTo(NavRoutes.HOME) { inclusive = true }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(stringResource(R.string.result_menu))
            }
        }
    }
}

private fun nonWinReasonTextId(status: GameSessionStatus): Int = when (status) {
    GameSessionStatus.SURRENDERED -> R.string.result_nonwin_surrender
    GameSessionStatus.DEAD_END -> R.string.result_nonwin_dead_end
    GameSessionStatus.ERROR -> R.string.result_nonwin_error
    else -> R.string.result_nonwin_unknown
}

@Composable
private fun NonWinActorCard(label: String, actor: Actor, modifier: Modifier) {
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
