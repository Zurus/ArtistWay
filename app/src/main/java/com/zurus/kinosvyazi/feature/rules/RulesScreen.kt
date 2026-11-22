package com.zurus.kinosvyazi.feature.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.zurus.kinosvyazi.R
import com.zurus.kinosvyazi.ui.navigation.NavRoutes

/**
 * Экран правил (docs/01_concept.md, §2.2):
 * цель, схема связи, пример, правила недопустимых переходов.
 * Статичный контент — ViewModel не требуется (docs/05_roadmap.md, пункт 4:
 * в файловой структуре этапа для RulesScreen нет ViewModel).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(navController: NavHostController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rules_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.rules_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RuleCard(stringResource(R.string.rules_goal))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.rules_scheme),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            RuleCard(stringResource(R.string.rules_example))
            RuleCard(stringResource(R.string.rules_finish))
            RuleCard(stringResource(R.string.rules_limit_1))
            RuleCard(stringResource(R.string.rules_limit_2))
            RuleCard(stringResource(R.string.rules_limit_3))
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    navController.navigate(NavRoutes.GAME_SETUP) {
                        popUpTo(NavRoutes.HOME) { inclusive = true }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(stringResource(R.string.rules_start_game))
            }
            OutlinedButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(stringResource(R.string.rules_back))
            }
        }
    }
}

@Composable
private fun RuleCard(text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
