package com.zurus.kinosvyazi.feature.chain

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.zurus.kinosvyazi.R
import com.zurus.kinosvyazi.core.ui.UiState
import com.zurus.kinosvyazi.feature.result.ResultMetrics
import com.zurus.kinosvyazi.feature.result.formatGameDuration
import com.zurus.kinosvyazi.ui.components.ChainView
import com.zurus.kinosvyazi.ui.components.UiStateView

/**
 * Экран просмотра цепочки (docs/01_concept.md, §2.11):
 * полный список шагов с именами, метрики партии (шаги, время).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChainScreen(
    navController: NavHostController,
    sessionId: String,
    viewModel: ChainViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(sessionId) { viewModel.load(sessionId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chain_title)) },
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
        UiStateView(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            onRetry = { viewModel.load(sessionId) },
        ) { content ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                ChainView(
                    chain = content.session.chain,
                    actors = content.chain.actors,
                    movies = content.chain.movies,
                    startActorId = content.session.startActor.tmdbId,
                    targetActorId = content.session.targetActor.tmdbId,
                    currentActorId = content.session.currentActor.tmdbId,
                )
                ResultMetrics(
                    stepCount = content.session.stepCount,
                    startedAtMillis = content.session.startedAtMillis,
                    finishedAtMillis = content.session.finishedAtMillis,
                )
                Text(
                    stringResource(R.string.result_time, formatGameDuration(
                        content.session.startedAtMillis,
                        content.session.finishedAtMillis,
                    )),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
