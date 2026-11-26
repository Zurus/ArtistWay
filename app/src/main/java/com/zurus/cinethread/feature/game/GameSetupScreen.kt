package com.zurus.cinethread.feature.game

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
import com.zurus.cinethread.ui.components.LoadingState
import com.zurus.cinethread.ui.components.resolve
import com.zurus.cinethread.ui.navigation.NavRoutes

/**
 * Экран генерации партии (docs/01_concept.md, §2.3):
 * индикатор «Генерируем пару…», при ошибке — сообщение и «Повторить».
 * Успех → автоматический переход на экран активной партии.
 */
@Composable
fun GameSetupScreen(
    navController: NavHostController,
    viewModel: GameSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.start() }

    // Победа в генерации — сразу переходим в партию (критерий этапа 4).
    LaunchedEffect(state) {
        (state as? UiState.Content)?.let { content ->
            navController.navigate(NavRoutes.game(content.value.session.sessionId)) {
                popUpTo(NavRoutes.GAME_SETUP) { inclusive = true }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (val currentState = state) {
            is UiState.Loading -> LoadingState(
                text = stringResource(R.string.setup_generating),
            )

            is UiState.Empty -> Unit

            is UiState.Error -> {
                Text(
                    text = stringResource(R.string.setup_error_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = currentState.text.resolve(),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(
                    onClick = viewModel::start,
                    modifier = Modifier.padding(top = 24.dp).fillMaxWidth().height(48.dp),
                ) {
                    Text(stringResource(R.string.setup_retry))
                }
                OutlinedButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.padding(top = 12.dp).fillMaxWidth().height(48.dp),
                ) {
                    Text(stringResource(R.string.setup_back))
                }
            }

            is UiState.Content -> LoadingState(text = stringResource(R.string.setup_generating))
        }
    }
}
