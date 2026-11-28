package com.zurus.cinethread.feature.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import com.zurus.cinethread.core.common.SetupDepthPrefs
import com.zurus.cinethread.core.ui.UiState
import com.zurus.cinethread.ui.components.EmptyState
import com.zurus.cinethread.ui.components.LoadingState
import com.zurus.cinethread.ui.components.resolve
import com.zurus.cinethread.ui.navigation.NavRoutes

/**
 * Экран подготовки к игре (docs/01_concept.md, §2.3; docs/debugging.md D5):
 * выбор глубины пары 2/3/4 (по умолчанию 2, сохраняется), кнопка
 * «Подобрать пару»; во время генерации — «Подбираем пару глубины N…»;
 * при ошибке — сообщение и «Повторить»; успех → переход к активной партии.
 */
@Composable
fun GameSetupScreen(
    navController: NavHostController,
    viewModel: GameSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val depth by viewModel.selectedDepth.collectAsState()

    // Успех генерации — сразу к игре (критерий Этапа 4).
    LaunchedEffect(state) {
        val currentState = state
        if (currentState is UiState.Content) {
            navController.navigate(NavRoutes.game(currentState.value.session.sessionId)) {
                popUpTo(NavRoutes.GAME_SETUP) { inclusive = true }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (val currentState = state) {
            is UiState.Empty -> {
                Text(
                    text = stringResource(R.string.setup_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.setup_depth_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.setup_depth_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (option in SetupDepthPrefs.MIN_DEPTH..SetupDepthPrefs.MAX_DEPTH) {
                        FilterChip(
                            selected = depth == option,
                            onClick = { viewModel.setDepth(option) },
                            label = { Text(option.toString()) },
                        )
                    }
                }
                if (depth > SetupDepthPrefs.MIN_DEPTH) {
                    Text(
                        text = stringResource(R.string.setup_depth_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = viewModel::start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(stringResource(R.string.setup_start))
                }
                OutlinedButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.setup_back))
                }
            }

            is UiState.Loading ->
                LoadingState(
                    text = stringResource(R.string.setup_generating_depth, depth),
                )

            is UiState.Error ->
                EmptyState(
                    text = currentState.text.resolve(),
                    actionLabel = stringResource(R.string.setup_retry),
                    onAction = viewModel::start,
                    modifier = Modifier.fillMaxWidth(),
                )

            // На миг между Content и pop-навигацией.
            is UiState.Content ->
                LoadingState(
                    text = stringResource(R.string.setup_generating_depth, depth),
                )
        }
    }
}
