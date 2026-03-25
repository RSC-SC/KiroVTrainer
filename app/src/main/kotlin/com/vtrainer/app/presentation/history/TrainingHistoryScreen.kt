package com.vtrainer.app.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vtrainer.app.domain.models.ExerciseLog
import com.vtrainer.app.domain.models.TrainingLog
import com.vtrainer.app.presentation.common.NetworkErrorDialog
import com.vtrainer.app.presentation.common.OfflineBanner
import com.vtrainer.app.presentation.common.SyncStatusBanner
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Screen displaying the user's training history.
 *
 * Shows:
 * - List of completed workouts ordered by date descending (Req 7.1)
 * - Exercise details for each session (Req 7.2)
 * - Volume data per session (Req 7.3)
 * - Weekly and monthly progress charts (Req 7.4)
 * - Personal record highlights with trophy icon (Req 7.5)
 *
 * Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5
 *
 * @param viewModel [TrainingHistoryViewModel] providing [TrainingHistoryState].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingHistoryScreen(
    viewModel: TrainingHistoryViewModel
) {
    val state by viewModel.state.collectAsState()

    // Error dialog for network failures (Req 12.3)
    if (state.error != null) {
        NetworkErrorDialog(
            message = state.error!!,
            onDismiss = { viewModel.dismissError() },
            onRetry = { viewModel.refresh() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Histórico de Treinos", fontWeight = FontWeight.Bold)
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Req 12.3 — offline mode indicator
            OfflineBanner(isOffline = state.isOffline)

            // Req 12.5 — pending sync status with retry action
            SyncStatusBanner(
                pendingCount = state.pendingSyncCount,
                isSyncing = state.isSyncing,
                onRetrySync = { viewModel.retrySyncPendingLogs() }
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }

                    state.logs.isEmpty() -> {
                        Text(
                            text = "Nenhum treino registrado ainda.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp)
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item { Spacer(modifier = Modifier.height(8.dp)) }

                            // Req 7.4 — Weekly progress chart
                            if (state.weeklyProgress.isNotEmpty()) {
                                item {
                                    ProgressChartCard(
                                        title = "Progresso Semanal",
                                        bars = state.weeklyProgress.map { it.weekLabel to it.workoutCount }
                                    )
                                }
                            }

                            // Req 7.4 — Monthly progress chart
                            if (state.monthlyProgress.isNotEmpty()) {
                                item {
                                    ProgressChartCard(
                                        title = "Progresso Mensal",
                                        bars = state.monthlyProgress.map { it.monthLabel to it.workoutCount }
                                    )
                                }
                            }

                            item {
                                Text(
                                    text = "Sessões",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Req 7.1 — Logs sorted by date descending
                            items(state.logs) { log ->
                                TrainingLogCard(log = log)
                            }

                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Sub-composables
// ---------------------------------------------------------------------------

/**
 * Card showing a single training session with exercise details, volume, and PR badge.
 *
 * Validates: Requirements 7.2, 7.3, 7.5
 */
@Composable
private fun TrainingLogCard(log: TrainingLog) {
    val isPR = log.hasPersonalRecord()
    val tz = TimeZone.currentSystemDefault()
    val localDate = log.timestamp.toLocalDateTime(tz).date

    val cardDescription = buildString {
        append(log.workoutDayName.ifBlank { "Treino" })
        append(", $localDate")
        append(", volume ${log.totalVolume} kg")
        if (isPR) append(", contém recorde pessoal")
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = cardDescription },
        colors = CardDefaults.cardColors(
            containerColor = if (isPR)
                MaterialTheme.colorScheme.tertiaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = log.workoutDayName.ifBlank { "Treino" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$localDate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Req 7.5 — Personal record badge
                if (isPR) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = "Recorde Pessoal",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "PR",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Req 7.3 — Volume data
            Text(
                text = "Volume total: ${log.totalVolume} kg",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (log.duration > 0) {
                Text(
                    text = "Duração: ${log.duration / 60} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Req 7.2 — Exercise details
            if (log.exercises.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                log.exercises.forEach { exerciseLog ->
                    ExerciseLogRow(exerciseLog = exerciseLog)
                }
            }
        }
    }
}

/**
 * Row showing a single exercise's summary within a training log card.
 *
 * Validates: Requirement 7.2
 */
@Composable
private fun ExerciseLogRow(exerciseLog: ExerciseLog) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (exerciseLog.isPersonalRecord) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = exerciseLog.exerciseId,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = "${exerciseLog.sets.size} séries · ${exerciseLog.totalVolume} kg",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Simple bar chart card for weekly/monthly progress.
 *
 * Validates: Requirement 7.4
 */
@Composable
private fun ProgressChartCard(
    title: String,
    bars: List<Pair<String, Int>>
) {
    val maxValue = bars.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                bars.forEach { (label, value) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        val barHeightFraction = value.toFloat() / maxValue.toFloat()
                        val barHeightDp = (barHeightFraction * 60).coerceAtLeast(2f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(barHeightDp.dp)
                                .background(
                                    color = if (value > 0)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.extraSmall
                                )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = label.take(3),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
