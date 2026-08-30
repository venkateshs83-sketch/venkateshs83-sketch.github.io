package com.venkat.tesladashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarViewWeek
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.venkat.tesladashboard.ui.theme.TeslaDashboardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TeslaDashboardTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DashboardScreen(modifier = Modifier.padding(innerPadding))
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

@Composable
fun DashboardScreen(modifier: Modifier = Modifier, viewModel: DashboardViewModel = viewModel()) {
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Tesla Dashboard", style = MaterialTheme.typography.headlineMedium)
        }

        item {
            Button(onClick = { viewModel.refresh() }) {
                Text(if (viewModel.isLoading) "Refreshing..." else "Refresh")
            }
        }

        viewModel.errorMessage?.let { error ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("Error: $error", modifier = Modifier.padding(12.dp))
                }
            }
        }

        item { StatusCard(viewModel) }
        item { TodayCard(viewModel) }
        item { PeriodCard(title = "This Week", icon = Icons.Filled.CalendarViewWeek, summary = viewModel.weekSummary) }
        item { PeriodCard(title = "This Month", icon = Icons.Filled.CalendarMonth, summary = viewModel.monthSummary) }

        item {
            Text("Last 7 Days", style = MaterialTheme.typography.titleMedium)
        }
        item {
            HistoryChart(viewModel.history)
        }
        items(viewModel.history) { day ->
            HistoryRow(day)
        }
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
fun TodayCard(viewModel: DashboardViewModel) {
    val summary = viewModel.todaySummary
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Today", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            val startB = summary?.start_battery
            val endB = summary?.end_battery
            if (startB != null && endB != null) {
                Text("Battery: $startB% → $endB%  (${if (startB > endB) "${startB - endB}% used" else "net charged"})")
            }
            val startOd = milesToKm(summary?.start_odometer)
            val endOd = milesToKm(summary?.end_odometer)
            if (startOd != null && endOd != null) {
                Text("Odometer: ${fmt(startOd)} km → ${fmt(endOd)} km")
            }
            Text("Distance driven: ${fmt(summary?.km_driven)} km")
            Text("Energy added: ${fmt(summary?.kwh_added)} kWh")
            Text("Charging time: ${summary?.charging_minutes ?: 0} min")
        }
    }
}

@Composable
fun PeriodCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, summary: PeriodSummary?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (summary == null || summary.days_count == 0) {
                Text("No data yet")
            } else {
                val startB = summary.start_battery
                val endB = summary.end_battery
                if (startB != null && endB != null) {
                    Text("Battery: $startB% → $endB%")
                }
                Text("Distance driven: ${fmt(summary.total_km)} km")
                Text("Energy added: ${fmt(summary.total_kwh)} kWh")
                Text("Days of data: ${summary.days_count}")
            }
        }
    }
}

@Composable
fun HistoryChart(history: List<DailySummary>) {
    if (history.isEmpty()) return
    val maxKm = (history.maxOfOrNull { it.km_driven ?: 0.0 } ?: 1.0).coerceAtLeast(1.0)

    Card(modifier = Modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp).padding(12.dp)) {
            val barWidth = size.width / (history.size * 1.5f)
            val gap = barWidth * 0.5f
            history.forEachIndexed { index, day ->
                val km = day.km_driven ?: 0.0
                val barHeight = (km / maxKm).toFloat() * size.height
                val x = index * (barWidth + gap)
                drawRect(
                    color = Color(0xFF2196F3),
                    topLeft = Offset(x, size.height - barHeight),
                    size = Size(barWidth, barHeight)
                )
            }
        }
    }
}

@Composable
fun HistoryRow(day: DailySummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(day.date ?: "Unknown date", style = MaterialTheme.typography.bodyLarge)
            val startB = day.start_battery
            val endB = day.end_battery
            if (startB != null && endB != null) {
                Text("Battery: $startB% → $endB%")
            }
            Text("${fmt(day.kwh_added)} kWh · ${fmt(day.km_driven)} km")
        }
    }
}
