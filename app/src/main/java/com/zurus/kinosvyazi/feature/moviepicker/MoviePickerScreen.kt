package com.zurus.kinosvyazi.feature.moviepicker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.zurus.kinosvyazi.R
import com.zurus.kinosvyazi.core.ui.UiState
import com.zurus.kinosvyazi.feature.error.ErrorType
import com.zurus.kinosvyazi.ui.components.MovieCard
import com.zurus.kinosvyazi.ui.components.OfflineBanner
import com.zurus.kinosvyazi.ui.components.UiStateView
import com.zurus.kinosvyazi.ui.navigation.NavRoutes

/**
 * Экран выбора фильма (docs/01_concept.md, §2.5):
 * «Выберите фильм с участием X», список фильмов, выбор → выбор актёра.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoviePickerScreen(
    navController: NavHostController,
    sessionId: String,
    viewModel: MoviePickerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(sessionId) {
        viewModel.load(sessionId) {
            navController.navigate(NavRoutes.error(ErrorType.NOT_FOUND))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.movie_picker_title)) },
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
                onRetry = { viewModel.load(sessionId) },
                // docs/01_concept.md §6.5: конкретный текст + действие «Назад».
                emptyText = stringResource(R.string.movie_picker_empty),
                emptyHint = stringResource(R.string.movie_picker_empty_hint),
                emptyActionLabel = stringResource(R.string.common_back),
                onEmptyAction = { navController.popBackStack() },
                // docs/01_concept.md §6.12: заголовок над текстом ошибки.
                errorTitle = stringResource(R.string.movie_picker_load_error),
            ) { content ->
                LazyColumn(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = 12.dp,
                        bottom = 16.dp,
                    ),
                ) {
                    item {
                        Text(
                            stringResource(R.string.movie_picker_subtitle, content.currentActor.name),
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    items(content.movies, key = { it.tmdbId }) { movie ->
                        MovieCard(
                            movie = movie,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                navController.navigate(NavRoutes.actorPicker(sessionId, movie.tmdbId))
                            },
                        )
                    }
                }
            }
        }
    }
}
