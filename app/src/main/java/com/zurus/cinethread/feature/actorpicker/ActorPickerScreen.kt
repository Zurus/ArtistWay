package com.zurus.cinethread.feature.actorpicker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.zurus.cinethread.R
import com.zurus.cinethread.core.ui.UiState
import com.zurus.cinethread.feature.error.ErrorType
import com.zurus.cinethread.ui.components.ActorCard
import com.zurus.cinethread.ui.components.OfflineBanner
import com.zurus.cinethread.ui.components.UiStateView
import com.zurus.cinethread.ui.navigation.NavRoutes

/**
 * Экран выбора актёра (docs/01_concept.md, §2.6):
 * «Выберите актёра из фильма X», каст без текущего актёра,
 * у целевого — бейдж «Цель». Выбор → подтверждение перехода.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActorPickerScreen(
    navController: NavHostController,
    sessionId: String,
    movieId: Int,
    viewModel: ActorPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(sessionId, movieId) {
        viewModel.load(sessionId, movieId) {
            navController.navigate(NavRoutes.error(ErrorType.NOT_FOUND))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.actor_picker_title)) },
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
        Column(Modifier.fillMaxSize()) {
            OfflineBanner()
            UiStateView(
                state = state,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onRetry = { viewModel.load(sessionId, movieId) },
                // docs/01_concept.md §6.6: конкретный текст + действие «Назад».
                emptyText = stringResource(R.string.actor_picker_empty),
                emptyHint = stringResource(R.string.actor_picker_empty_hint),
                emptyActionLabel = stringResource(R.string.common_back),
                onEmptyAction = { navController.popBackStack() },
                // docs/01_concept.md §6.11: заголовок над текстом ошибки.
                errorTitle = stringResource(R.string.actor_picker_load_error),
            ) { content ->
                val targetInCast = content.actors.any { it.tmdbId == content.targetActor.tmdbId }
                LazyColumn(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
                ) {
                    item {
                        content.movie?.let { movie ->
                            Text(
                                stringResource(R.string.actor_picker_subtitle, movie.title),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }
                    // docs/01_concept.md §6.7: цель может отсутствовать в касте —
                    // показываем сообщение, игрок выбирает другого актёра/фильм.
                    if (!targetInCast) {
                        item {
                            Text(
                                stringResource(R.string.actor_picker_target_not_found),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }
                    items(content.actors, key = { it.tmdbId }) { actor ->
                        ActorCard(
                            actor = actor,
                            modifier = Modifier.fillMaxWidth(),
                            badge = if (actor.tmdbId == content.targetActor.tmdbId) {
                                stringResource(R.string.actor_picker_target_badge)
                            } else {
                                null
                            },
                            onClick = {
                                navController.navigate(NavRoutes.transition(sessionId, movieId, actor.tmdbId))
                            },
                        )
                    }
                }
            }
        }
    }
}
