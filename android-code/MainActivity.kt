package com.venkat.tesladashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.venkat.tesladashboard.ui.theme.TeslaDashboardTheme
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.RowScope

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TeslaDashboardTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppRoot(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

fun batteryColor(level: Int?): Color {
    if (level == null) return Color.Gray
    return when {
        level >= 50 -> Color(0xFF4CAF50)
        level >= 20 -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }
}

fun milesToKm(miles: Double?): Double? = miles?.let { it * 1.60934 }
fun fmt(value: Double?): String = value?.let { "%.1f".format(it) } ?: "--"

fun formatSessionTime(iso: String?): String {
    if (iso == null) return "--"
    return try {
        val dt = OffsetDateTime.parse(iso)
        dt.format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
    } catch (e: Exception) {
        iso
    }
}

fun formatTimeOnly(iso: String?): String {
    if (iso == null) return "--"
    return try {
        val dt = OffsetDateTime.parse(iso)
        dt.format(DateTimeFormatter.ofPattern("h:mm a"))
    } catch (e: Exception) {
        iso
    }
}

fun sessionDetail(session: ChargingSession): String {
    return try {
        val start = OffsetDateTime.parse(session.start_time)
        val end = OffsetDateTime.parse(session.end_time)
        val dur = Duration.between(start, end)
        val hours = dur.toMinutes() / 60.0
        val avgKw = if (hours > 0) (session.kwh_added ?: 0.0) / hours else 0.0
        val h = dur.toHours()
        val m = dur.toMinutes() % 60
        "Duration ${h}h ${m}m · Avg ${"%.1f".format(avgKw)} kW"
    } catch (e: Exception) {
        ""
    }
}

@Composable
fun AppRoot(modifier: Modifier = Modifier, viewModel: DashboardViewModel = viewModel()) {
    var selectedTab by remember { mutableStateOf("today") }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tesla Dashboard", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = { viewModel.refresh() }) {
                Text(if (viewModel.isLoading) "..." else "Refresh")
            }
        }

        viewModel.errorMessage?.let { error ->
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("Error: $error", modifier = Modifier.padding(12.dp))
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                "today" -> TodayTab(viewModel)
                "week" -> WeekTab(viewModel)
                "month" -> MonthTab(viewModel)
                "charge" -> ChargeTab(viewModel)
            }
        }

        BottomNav(selectedTab) { selectedTab = it }
    }
}

@Composable
fun Box(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(modifier = modifier) { content() }
}

@Composable
fun BottomNav(selected: String, onSelect: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        NavItem("today", "Today", Icons.Filled.Dashboard, selected, onSelect)
        NavItem("week", "Week", Icons.Filled.CalendarViewWeek, selected, onSelect)
        NavItem("month", "Month", Icons.Filled.CalendarMonth, selected, onSelect)
        NavItem("charge", "Charging", Icons.Filled.Bolt, selected, onSelect)
    }
}

@Composable
fun RowScope_placeholder() {}

@Composable
fun NavItem(key: String, label: String, icon: ImageVector, selected: String, onSelect: (String) -> Unit) {
    val isSelected = key == selected
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable { onSelect(key) }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TodayTab(viewModel: DashboardViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { StatusCard(viewModel) }
        item { TodayMetrics(viewModel) }
    }
}

@Composable
fun StatusCard(viewModel: DashboardViewModel) {
    val reading = viewModel.latestReading
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Current Status", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            if (reading == null) {
                Text("No data yet")
            } else {
                val isCharging = reading.charging_state == "Charging"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isCharging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryStd,
                        contentDescription = null,
                        tint = batteryColor(reading.battery_level)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${reading.battery_level ?: "--"}%", style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (reading.battery_level ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = batteryColor(reading.battery_level)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.Bolt, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Charging: ${reading.charging_state ?: "Unknown"}")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.DirectionsCar, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Odometer: ${fmt(milesToKm(reading.odometer))} km")
                }
            }
        }
    }
}

@Composable
fun TodayMetrics(viewModel: DashboardViewModel) {
    val summary = viewModel.todaySummary
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricTile("Driven today", "${fmt(summary?.km_driven)} km", Modifier.weight(1f))
        MetricTile("Energy added", "${fmt(summary?.kwh_added)} kWh", Modifier.weight(1f))
    }
}

@Composable
fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
fun WeekTab(viewModel: DashboardViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { PeriodCard("Battery this week", viewModel.weekSummary) }
        item { HistoryChart(viewModel.history) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("Total driven", "${fmt(viewModel.weekSummary?.total_km)} km", Modifier.weight(1f))
                MetricTile("Total energy", "${fmt(viewModel.weekSummary?.total_kwh)} kWh", Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun MonthTab(viewModel: DashboardViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { PeriodCard("Battery this month", viewModel.monthSummary) }
        item { MonthChart(viewModel.monthHistory) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("Total driven", "${fmt(viewModel.monthSummary?.total_km)} km", Modifier.weight(1f))
                MetricTile("Total energy", "${fmt(viewModel.monthSummary?.total_kwh)} kWh", Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun PeriodCard(title: String, summary: PeriodSummary?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            if (summary == null || summary.days_count == 0) {
                Text("No data yet")
            } else {
                val startB = summary.start_battery
                val endB = summary.end_battery
                if (startB != null && endB != null) {
                    Text("$startB% \u2192 $endB%", style = MaterialTheme.typography.headlineSmall)
                }
                Text("${summary.days_count} days of data", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun HistoryChart(history: List<DailySummary>) {
    if (history.isEmpty()) return
    val maxKm = (history.maxOfOrNull { it.km_driven ?: 0.0 } ?: 1.0).coerceAtLeast(1.0)

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            history.forEach { day ->
                val km = day.km_driven ?: 0.0
                val barHeightDp = (80 * (km / maxKm)).dp.coerceAtLeast(2.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(fmt(km), style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(
                        modifier = Modifier.width(28.dp).height(80.dp),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.fillMaxWidth().height(barHeightDp).background(Color(0xFF2196F3))
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(day.date?.takeLast(5) ?: "", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun MonthChart(history: List<DailySummary>) {
    if (history.isEmpty()) return
    val maxKm = (history.maxOfOrNull { it.km_driven ?: 0.0 } ?: 1.0).coerceAtLeast(1.0)
    val scrollState = rememberScrollState()

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            history.forEach { day ->
                val km = day.km_driven ?: 0.0
                val barHeightDp = (60 * (km / maxKm)).dp.coerceAtLeast(2.dp)
                Column(
                    modifier = Modifier.width(8.dp).height(60.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxWidth().height(barHeightDp).background(Color(0xFF2196F3))
                    )
                }
            }
        }
    }
}

@Composable
fun ChargeTab(viewModel: DashboardViewModel) {
    var expandedDetail by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (viewModel.chargingSessions.isEmpty()) {
            item { Text("No charging sessions yet") }
        }
        items(viewModel.chargingSessions.reversed()) { session ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    expandedDetail = sessionDetail(session)
                }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Filled.Bolt, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${formatSessionTime(session.start_time)} to ${formatTimeOnly(session.end_time)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "${session.start_battery}% \u2192 ${session.end_battery}%",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Text("${fmt(session.kwh_added)} kWh", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        expandedDetail?.let { detail ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(detail, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
