package com.zurus.kinosvyazi.feature.home

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.zurus.kinosvyazi.ui.navigation.NavRoutes

/**
 * Экран запуска (docs/01_concept.md, §2.1):
 * название, краткое описание, «Начать игру», «Как играть»,
 * индикатор доступности TMDB (при ошибке — «Повторить подключение»).
 */
@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_tagline),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        when (val currentState = state) {
            is UiState.Loading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.home_checking),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            is UiState.Content -> AvailabilityIndicator(
                available = currentState.value.tmdbAvailable,
                onRetry = viewModel::retryAvailability,
            )

            is UiState.Empty -> Unit
            is UiState.Error -> AvailabilityIndicator(
                available = false,
                onRetry = viewModel::retryAvailability,
            )
        }

        Spacer(Modifier.height(32.dp))
        when (val currentState = state) {
            is UiState.Loading -> Unit
            is UiState.Content -> {
                if (currentState.value.activeSessionId != null) {
                    Button(
                        onClick = {
                            navController.navigate(
                                NavRoutes.game(currentState.value.activeSessionId),
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text(stringResource(R.string.home_continue))
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            is UiState.Empty -> Unit
            is UiState.Error -> Unit
        }

        Button(
            onClick = {
                navController.navigate(NavRoutes.GAME_SETUP) {
                    popUpTo(NavRoutes.HOME) { inclusive = true }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(stringResource(R.string.home_start_game))
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { navController.navigate(NavRoutes.RULES) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(stringResource(R.string.home_how_to_play))
        }
    }
}

@Composable
private fun AvailabilityIndicator(available: Boolean, onRetry: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Surface(
            shape = CircleShape,
            color = if (available) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier.size(10.dp),
        ) {
            // Точка-индикатор.
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(
                if (available) R.string.home_tmdb_available else R.string.home_tmdb_unavailable,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = if (available) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier.weight(1f),
        )
        if (!available) {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.home_retry_connection))
            }
        }
    }
}
