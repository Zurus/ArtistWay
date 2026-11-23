package com.zurus.kinosvyazi.feature.error

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.zurus.kinosvyazi.R
import com.zurus.kinosvyazi.core.ui.UiState
import com.zurus.kinosvyazi.ui.components.resolve
import com.zurus.kinosvyazi.ui.navigation.NavRoutes

/**
 * Экран ошибки (docs/01_concept.md, §2.10; docs/05_roadmap.md, Этап 5):
 * различает «нет сети», «TMDB недоступен», 429, пустой/некорректный ответ
 * и сбой генерации; для каждого — понятный текст без технических деталей
 * и кнопки «Повторить» (если повтор возможен) / «В меню».
 *
 * Тип ошибки задаётся аргументом маршрута `error/{type}` ([ErrorType]).
 */
@Composable
fun ErrorScreen(
    navController: NavHostController,
    viewModel: ErrorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (val currentState = state) {
            is UiState.Loading -> Unit
            is UiState.Empty -> Unit
            is UiState.Error -> Text(
                currentState.text.resolve(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            is UiState.Content -> {
                val content = currentState.value
                Text(
                    text = content.title.resolve(),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = content.text.resolve(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
                if (content.canRetry) {
                    Button(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp)
                            .height(52.dp),
                    ) {
                        Text(stringResource(R.string.common_retry))
                    }
                }
                OutlinedButton(
                    onClick = {
                        navController.navigate(NavRoutes.HOME) {
                            popUpTo(NavRoutes.HOME) { inclusive = true }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(48.dp),
                ) {
                    Text(stringResource(R.string.error_menu))
                }
            }
        }
    }
}
