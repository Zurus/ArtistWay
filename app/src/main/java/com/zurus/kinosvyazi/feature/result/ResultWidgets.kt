package com.zurus.kinosvyazi.feature.result

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zurus.kinosvyazi.R

/** Форматирование длительности партии: `мм:сс`. */
fun formatGameDuration(startedAtMillis: Long, finishedAtMillis: Long?): String {
    val end = finishedAtMillis ?: System.currentTimeMillis()
    val totalSeconds = ((end - startedAtMillis) / 1000L).coerceAtLeast(0L)
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

/** Метрики партии: шаги и время (docs/01_concept.md, §2.8). */
@Composable
fun ResultMetrics(stepCount: Int, startedAtMillis: Long, finishedAtMillis: Long?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        ResultMetricChip(
            value = stringResource(R.string.result_steps, stepCount),
            modifier = Modifier.weight(1f),
        )
        ResultMetricChip(
            value = stringResource(R.string.result_time, formatGameDuration(startedAtMillis, finishedAtMillis)),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ResultMetricChip(value: String, modifier: Modifier) {
    Column(
        modifier = modifier.padding(12.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
