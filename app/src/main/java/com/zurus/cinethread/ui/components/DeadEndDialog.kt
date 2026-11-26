package com.zurus.cinethread.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.zurus.cinethread.R
import com.zurus.cinethread.domain.usecase.DeadEndCause
import com.zurus.cinethread.domain.usecase.DeadEndStatus
import com.zurus.cinethread.domain.usecase.DeadEndStatus.DeadEnd

/**
 * Диалог тупика (docs/04_algorithms.md §4.3; docs/01_concept.md §6.8):
 * тупик — валидное состояние партии, а не исключение; игроку предлагаются
 * «Отменить шаг» / «Сдаться» / «Новая партия».
 *
 * @param canUndo отмена доступна, если в цепочке есть хотя бы один шаг.
 */
@Composable
fun DeadEndDialog(
    status: DeadEndStatus,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onSurrender: () -> Unit,
    onNewGame: () -> Unit,
    onDismiss: () -> Unit,
) {
    val message = when (status) {
        is DeadEnd -> when (status.cause) {
            DeadEndCause.NO_MOVIES -> stringResource(R.string.game_deadend_no_movies)
            DeadEndCause.NO_OTHER_ACTORS -> stringResource(R.string.game_deadend_no_other_actors)
            DeadEndCause.NO_PATH -> stringResource(R.string.game_deadend_no_path)
        }
        else -> stringResource(R.string.game_deadend_no_path)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_deadend_title)) },
        text = {
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (canUndo) {
                    TextButton(onClick = onUndo) {
                        Text(stringResource(R.string.game_deadend_undo))
                    }
                }
                TextButton(onClick = onSurrender) {
                    Text(stringResource(R.string.game_deadend_surrender))
                }
                TextButton(onClick = onNewGame) {
                    Text(stringResource(R.string.game_deadend_new_game))
                }
            }
        },
    )
}
