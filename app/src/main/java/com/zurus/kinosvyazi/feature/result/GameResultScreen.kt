package com.zurus.kinosvyazi.feature.result

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
import com.zurus.kinosvyazi.R
import com.zurus.kinosvyazi.core.ui.UiState
import com.zurus.kinosvyazi.domain.model.Actor
import com.zurus.kinosvyazi.ui.components.ActorPhoto
import com.zurus.kinosvyazi.ui.components.ChainView
import com.zurus.kinosvyazi.ui.components.UiStateView
import com.zurus.kinosvyazi.ui.navigation.NavRoutes

/**
 * Экран результата — победа (docs/01_concept.md, §2.8):
 * «Цель достигнута!», стартовый/целевой актёр, полная цепочка, метрики;
 * «Новая партия» / «Посмотреть цепочку» / «В меню».
 */
@Composable
fun GameResultScreen(
    navController: NavHostController,
    sessionId: String,
    viewModel: GameResultViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(sessionId) { viewModel.load(sessionId) }

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
                stringResource(R.string.result_won_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ResultActorCard(
                    label = stringResource(R.string.game_start_actor),
                    actor = content.session.startActor,
                    modifier = Modifier.weight(1f),
                )
                ResultActorCard(
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
            Text(
                stringResource(R.string.game_chain),
                style = MaterialTheme.typography.titleMedium,
            )
            ChainView(
                chain = content.session.chain,
                actors = content.chain.actors,
                movies = content.chain.movies,
                startActorId = content.session.startActor.tmdbId,
                targetActorId = content.session.targetActor.tmdbId,
            )
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
                onClick = { navController.navigate(NavRoutes.chain(sessionId)) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(stringResource(R.string.result_view_chain))
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

@Composable
private fun ResultActorCard(label: String, actor: Actor, modifier: Modifier) {
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
