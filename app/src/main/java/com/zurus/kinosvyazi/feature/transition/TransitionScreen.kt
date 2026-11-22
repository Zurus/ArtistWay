package com.zurus.kinosvyazi.feature.transition

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import com.zurus.kinosvyazi.domain.util.ChainInvalidReason
import com.zurus.kinosvyazi.ui.components.ActorPhoto
import com.zurus.kinosvyazi.ui.components.ChainView
import com.zurus.kinosvyazi.ui.components.UiStateView
import com.zurus.kinosvyazi.ui.navigation.NavRoutes
import kotlinx.coroutines.launch

/**
 * Экран подтверждения перехода (docs/01_concept.md, §2.7):
 * превью `A → M → B`, новая цепочка, «Подтвердить»/«Отменить».
 * Отмена не фиксирует шаг; победа проверяется после фиксации.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransitionScreen(
    navController: NavHostController,
    sessionId: String,
    movieId: Int,
    toActorId: Int,
    viewModel: TransitionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(sessionId, movieId, toActorId) { viewModel.load(sessionId, movieId, toActorId) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.transition_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
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
            onRetry = { viewModel.load(sessionId, movieId, toActorId) },
        ) { content ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TransitionPreview(content)

                Text(
                    stringResource(R.string.transition_new_chain),
                    style = MaterialTheme.typography.titleMedium,
                )
                val newChain = content.session.chain + content.candidateStep
                val chainActors = content.chain.actors + (content.toActor.tmdbId to content.toActor)
                val chainMovies = content.movie?.let {
                    content.chain.movies + (content.candidateStep.movieId to it)
                } ?: content.chain.movies
                ChainView(
                    chain = newChain,
                    actors = chainActors,
                    movies = chainMovies,
                    startActorId = content.session.startActor.tmdbId,
                    targetActorId = content.session.targetActor.tmdbId,
                    currentActorId = content.toActor.tmdbId,
                )

                if (content.validation is ResultValidation.Invalid) {
                    ValidationCard((content.validation as ResultValidation.Invalid).reason)
                }

                Spacer(Modifier.height(8.dp))
                if (content.validation is ResultValidation.Valid) {
                    Button(
                        onClick = {
                            scope.launch {
                                when (viewModel.confirm()) {
                                    ConfirmOutcome.Win ->
                                        navController.navigate(NavRoutes.resultWon(sessionId)) {
                                            popUpTo(NavRoutes.HOME) { inclusive = true }
                                        }
                                    ConfirmOutcome.Ok -> navController.popBackStack()
                                    ConfirmOutcome.Failure -> Unit
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.transition_confirm))
                    }
                }
                OutlinedButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(stringResource(R.string.transition_cancel))
                }
            }
        }
    }
}

@Composable
private fun TransitionPreview(content: TransitionUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                PreviewActorNode(content.fromActor, isTarget = false)
                ArrowText()
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
                    content.movie?.let { movie ->
                        com.zurus.kinosvyazi.ui.components.MoviePoster(
                            movie.posterUrl,
                            Modifier.size(width = 54.dp, height = 72.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            movie.title,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    }
                }
                ArrowText()
                PreviewActorNode(
                    content.toActor,
                    isTarget = content.toActor.tmdbId == content.session.targetActor.tmdbId,
                )
            }
        }
    }
}

@Composable
private fun PreviewActorNode(actor: Actor, isTarget: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ActorPhoto(actor.photoUrl, Modifier.size(56.dp), sizeDp = 56)
        Spacer(Modifier.height(4.dp))
        Text(
            actor.name,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        if (isTarget) {
            Text(
                stringResource(R.string.actor_picker_target_badge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ArrowText() {
    Text(
        "→",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun ValidationCard(reason: ChainInvalidReason) {
    val text = when (reason) {
        ChainInvalidReason.CHAIN_BREAK -> stringResource(R.string.chain_break)
        ChainInvalidReason.ACTOR_REPEAT -> stringResource(R.string.chain_actor_repeat)
        ChainInvalidReason.MOVIE_REPEAT -> stringResource(R.string.chain_movie_repeat)
        ChainInvalidReason.ACTOR_NOT_IN_CAST -> stringResource(R.string.chain_actor_not_in_cast)
        ChainInvalidReason.INVALID_IDS -> stringResource(R.string.chain_invalid_ids)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(
            text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
