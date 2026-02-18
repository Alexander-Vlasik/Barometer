package com.barometer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barometer.data.DayKpi
import com.barometer.data.PressureSample
import android.content.Intent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val GoodSampleResults = setOf("OK")

private enum class DaySheetMode {
    FULL_DETAILS,
    KPI,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
) {
    val context = LocalContext.current
    val useFgs by viewModel.useFgs.collectAsStateWithLifecycle()
    val dayPages by viewModel.dayPages.collectAsStateWithLifecycle()
    val dayData by viewModel.dayData.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { dayPages.size },
    )

    LaunchedEffect(dayPages, dayData.date) {
        val selectedIndex = dayPages.indexOf(dayData.date)
        if (selectedIndex >= 0 && selectedIndex != pagerState.currentPage) {
            pagerState.scrollToPage(selectedIndex)
        }
    }

    LaunchedEffect(pagerState.currentPage, dayPages) {
        dayPages.getOrNull(pagerState.currentPage)?.let(viewModel::onDaySelected)
    }

    LaunchedEffect(Unit) {
        viewModel.snackMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HeaderBar(useFgs = useFgs, onUseFgsChanged = viewModel::onUseFgsChanged)

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refreshCurrentDay,
                modifier = Modifier.weight(1f),
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val pageDay = dayPages[page]
                    val isCurrent = pageDay == dayData.date
                    DayPage(
                        date = pageDay,
                        samples = if (isCurrent) dayData.samples else emptyList(),
                        kpi = if (isCurrent) dayData.kpi else null,
                        onShare = { shareDayReport(context, pageDay, dayData.samples, dayData.kpi) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderBar(
    useFgs: Boolean,
    onUseFgsChanged: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Barometer",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "FGS",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Switch(checked = useFgs, onCheckedChange = onUseFgsChanged)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPage(
    date: LocalDate,
    samples: List<PressureSample>,
    kpi: DayKpi?,
    onShare: () -> Unit,
) {
    val dateFormat = remember { DateTimeFormatter.ofPattern("EEE, dd MMM yyyy") }
    var sheetMode by remember { mutableStateOf<DaySheetMode?>(null) }

    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = date.format(dateFormat),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onShare) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share day report",
                        )
                    }
                    IconButton(onClick = { sheetMode = DaySheetMode.FULL_DETAILS }) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "Full details",
                        )
                    }
                    IconButton(onClick = { sheetMode = DaySheetMode.KPI }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "KPI",
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (samples.isEmpty()) {
                Text(
                    text = "No samples for this day yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(samples, key = { it.id }) { sample ->
                        CompactSampleRow(sample)
                    }
                }
            }
        }
    }

    when (sheetMode) {
        DaySheetMode.FULL_DETAILS -> ModalBottomSheet(onDismissRequest = { sheetMode = null }) {
            FullDetailsSheet(samples = samples)
        }
        DaySheetMode.KPI -> ModalBottomSheet(onDismissRequest = { sheetMode = null }) {
            KpiSheet(kpi = kpi)
        }
        null -> Unit
    }
}

@Composable
private fun CompactSampleRow(sample: PressureSample) {
    val pressureText = sample.pressureHpa?.let { String.format("%.1f", it) } ?: "-"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatTime(sample.timestampUtcMillis),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(92.dp),
        )
        Text(
            text = sample.result.name,
            style = MaterialTheme.typography.bodyMedium,
            color = resultColor(sample.result.name),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(94.dp),
        )
        Text(
            text = pressureText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(58.dp),
        )
    }
}

@Composable
private fun FullDetailsSheet(samples: List<PressureSample>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = "Full details",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (samples.isEmpty()) {
            Text(
                text = "No samples.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
            return
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(samples, key = { it.id }) { sample ->
                val badSample = sample.result.name !in GoodSampleResults
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (badSample) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    ),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = formatTime(sample.timestampUtcMillis),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = sample.result.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = resultColor(sample.result.name),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "P ${sample.pressureHpa?.let { String.format("%.1f hPa", it) } ?: "-"}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "M ${sample.mode.name}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        sample.diagnostics?.let { d ->
                            Spacer(modifier = Modifier.height(8.dp))
                            KeyValueRow("Sensor start", "${d.durationMs} ms")
                            KeyValueRow("Standby bucket", standbyBucketLabel(d.appStandbyBucket))
                            KeyValueRow("Doze / PowerSave", "${d.isDozeMode} / ${d.isPowerSaveMode}")
                            KeyValueRow("Battery", d.batteryPercent?.let { "$it%" } ?: "n/a")
                            d.stopReason?.let { KeyValueRow("Stop reason", it.toString()) }
                            d.failureClass?.let { klass ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Failure: $klass",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (!d.failureMessage.isNullOrBlank()) {
                                    Text(
                                        text = d.failureMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Run #${d.runAttemptCount}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } ?: run {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "No diagnostics for this sample.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun KpiSheet(kpi: DayKpi?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "KPI",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        if (kpi == null) {
            Text(
                text = "KPI not available for this page yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(20.dp))
            return
        }

        KpiLine("Samples", "${kpi.samplesCount} / ${kpi.expectedSamples}")
        KpiLine("Coverage", "${(kpi.coverage * 100).toInt()}%")
        KpiLine("Median gap", "${kpi.medianGapMinutes.toInt()} min")
        KpiLine("P90 gap", "${kpi.p90GapMinutes.toInt()} min")
        KpiLine("Max gap", "${kpi.maxGapMinutes.toInt()} min")
        KpiLine("Timeouts", kpi.timeoutsCount.toString())
        KpiLine("Errors", kpi.errorsCount.toString())
        KpiLine("Cancelled", kpi.cancelledCount.toString())
        KpiLine("Doze samples", kpi.dozeCount.toString())
        KpiLine("FGS count", kpi.fgsCount.toString())
        KpiLine("NO_FGS count", kpi.noFgsCount.toString())
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Summary",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        KpiLine("App starts", kpi.appStartsCount.toString())
        KpiLine("Process restarts by worker", kpi.workerColdStartsCount.toString())
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun KpiLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatTime(timestampUtcMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    return Instant.ofEpochMilli(timestampUtcMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(formatter)
}

@Composable
private fun resultColor(result: String) = when (result) {
    "OK" -> MaterialTheme.colorScheme.primary
    "TIMEOUT" -> MaterialTheme.colorScheme.tertiary
    "CANCELLED" -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.error
}

private fun standbyBucketLabel(bucket: Int?): String {
    return when (bucket) {
        null -> "n/a"
        5 -> "active"
        10 -> "working_set"
        20 -> "frequent"
        30 -> "rare"
        40 -> "restricted"
        else -> "bucket_$bucket"
    }
}

private fun shareDayReport(
    context: android.content.Context,
    date: LocalDate,
    samples: List<PressureSample>,
    kpi: DayKpi?,
) {
    val report = buildDayReport(date = date, samples = samples, kpi = kpi)
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Barometer report $date")
        putExtra(Intent.EXTRA_TEXT, report)
    }
    context.startActivity(Intent.createChooser(sendIntent, "Share barometer report"))
}

private fun buildDayReport(
    date: LocalDate,
    samples: List<PressureSample>,
    kpi: DayKpi?,
): String {
    val sb = StringBuilder()
    sb.appendLine("Barometer daily report")
    sb.appendLine("Date: $date")
    sb.appendLine("Generated: ${Instant.now()}")
    sb.appendLine()

    if (kpi == null) {
        sb.appendLine("KPI: not available")
    } else {
        sb.appendLine("KPI")
        sb.appendLine("- Samples: ${kpi.samplesCount} / ${kpi.expectedSamples}")
        sb.appendLine("- Coverage: ${(kpi.coverage * 100).toInt()}%")
        sb.appendLine("- Median gap: ${kpi.medianGapMinutes.toInt()} min")
        sb.appendLine("- P90 gap: ${kpi.p90GapMinutes.toInt()} min")
        sb.appendLine("- Max gap: ${kpi.maxGapMinutes.toInt()} min")
        sb.appendLine("- Timeouts: ${kpi.timeoutsCount}")
        sb.appendLine("- Errors: ${kpi.errorsCount}")
        sb.appendLine("- Cancelled: ${kpi.cancelledCount}")
        sb.appendLine("- Doze samples: ${kpi.dozeCount}")
        sb.appendLine("- FGS count: ${kpi.fgsCount}")
        sb.appendLine("- NO_FGS count: ${kpi.noFgsCount}")
        sb.appendLine("- App starts: ${kpi.appStartsCount}")
        sb.appendLine("- Process restarts by worker: ${kpi.workerColdStartsCount}")
    }

    sb.appendLine()
    sb.appendLine("Samples (${samples.size})")
    if (samples.isEmpty()) {
        sb.appendLine("- No samples")
        return sb.toString()
    }

    samples.forEach { sample ->
        val pressure = sample.pressureHpa?.let { String.format("%.1f", it) } ?: "-"
        sb.appendLine(
            "- ${formatTime(sample.timestampUtcMillis)} | ${sample.result.name} | " +
                "p=$pressure"
        )
        sample.diagnostics?.let { d ->
            val battery = d.batteryPercent?.toString() ?: "n/a"
            val stopReason = d.stopReason?.toString() ?: "n/a"
            sb.appendLine(
                "  sensorStartMs=${d.durationMs}, doze=${d.isDozeMode}, powerSave=${d.isPowerSaveMode}, " +
                    "battery=$battery%, bucket=${standbyBucketLabel(d.appStandbyBucket)}, " +
                    "stopReason=$stopReason, runAttempt=${d.runAttemptCount}"
            )
            if (!d.failureClass.isNullOrBlank() || !d.failureMessage.isNullOrBlank()) {
                sb.appendLine("  failure=${d.failureClass.orEmpty()} ${d.failureMessage.orEmpty()}".trim())
            }
        }
    }
    return sb.toString()
}
