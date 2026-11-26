package com.zurus.cinethread.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zurus.cinethread.R
import com.zurus.cinethread.data.network.ConnectivityObserver
import dagger.hilt.EntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Баннер офлайн-режима (docs/05_roadmap.md, Этап 5; docs/01_concept.md §6.2):
 * виден, пока устройство без сети — в этом состоянии репозитории
 * показывают устаревший Room-кэш, а не «нет данных».
 */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val observer = EntryPointAccessors.fromApplication(
        context.applicationContext,
        ConnectivityEntryPoint::class.java,
    ).connectivityObserver
    val online by observer.online.collectAsStateWithLifecycle()
    if (!online) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.offline_banner),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ConnectivityEntryPoint {
    val connectivityObserver: ConnectivityObserver
}
