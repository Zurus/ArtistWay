package com.zurus.kinosvyazi.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zurus.kinosvyazi.R
import com.zurus.kinosvyazi.core.ui.UiState
import com.zurus.kinosvyazi.core.ui.UiText

/**
 * Индикатор загрузки (docs/05_roadmap.md, пункт 4, компонент LoadingState).
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    text: String? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        if (text != null) {
            Text(
                text = text,
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/**
 * Отображает текстовое состояние: литерал или строковый ресурс.
 */
@Composable
fun UiText.resolve(): String =
    when (this) {
        is UiText.Literal -> value
        is UiText.Resource -> stringResource(id)
    }

/**
 * Единый рендер sealed-состояния экрана (docs/02_architecture.md, §4):
 * [UiState.Loading] → спиннер, [UiState.Error] → заголовок/текст + «Повторить»,
 * [UiState.Empty] → [EmptyState] с текстом и действием,
 * [UiState.Content] → [content].
 *
 * @param onRetry показывать кнопку «Повторить» при ошибке, если задана.
 * @param errorTitle заголовок над текстом ошибки (например, «Не удалось
 *   загрузить фильмы актёра», docs/01_concept.md §6.11/6.12).
 * @param emptyText текст пустого состояния (docs/01_concept.md §6.5/6.6).
 * @param emptyActionLabel кнопка действия в пустом состоянии («Назад»).
 */
@Composable
fun <T> UiStateView(
    state: UiState<T>,
    modifier: Modifier = Modifier,
    loadingText: String? = null,
    emptyText: String? = null,
    emptyHint: String? = null,
    emptyActionLabel: String? = null,
    onEmptyAction: (() -> Unit)? = null,
    errorTitle: String? = null,
    onRetry: (() -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    when (val currentState = state) {
        is UiState.Loading -> LoadingState(modifier, loadingText ?: stringResource(R.string.common_loading))

        is UiState.Empty -> {
            if (emptyText != null) {
                EmptyState(
                    text = emptyText,
                    hint = emptyHint,
                    actionLabel = emptyActionLabel,
                    onAction = onEmptyAction,
                    modifier = modifier,
                )
            } else {
                LoadingState(modifier, loadingText)
            }
        }

        is UiState.Error -> ErrorBox(
            modifier = modifier,
            title = errorTitle,
            text = currentState.text.resolve(),
            onRetry = onRetry,
        )

        is UiState.Content -> content(currentState.value)
    }
}

@Composable
private fun ErrorBox(
    modifier: Modifier,
    text: String,
    title: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
        if (onRetry != null) {
            Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.common_retry))
            }
        }
    }
}
