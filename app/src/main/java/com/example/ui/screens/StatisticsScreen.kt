package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.EventCategory
import com.example.ui.components.CategorySplitDonutCard
import com.example.ui.components.HorizontalBarChartCard
import com.example.ui.components.KpiStatCard
import com.example.ui.theme.CrestGold
import com.example.ui.theme.CrestGoldDeep
import com.example.ui.theme.CrestGoldWash
import com.example.ui.theme.EmeraldSoft
import com.example.ui.theme.ForestDeep
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.DashboardSummary
import com.example.ui.viewmodel.EventStatisticRow
import java.util.Locale

@Composable
fun StatisticsScreen(
    summary: DashboardSummary,
    eventStats: List<EventStatisticRow>
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var categoryFilter by rememberSaveable { mutableStateOf<EventCategory?>(null) }

    val filteredStats = remember(eventStats, searchQuery, categoryFilter) {
        val q = searchQuery.trim().lowercase()
        eventStats.filter { row ->
            (categoryFilter == null || row.event.categoryEnum == categoryFilter) &&
                (q.isEmpty() || row.event.name.lowercase().contains(q))
        }.sortedByDescending { it.participantCount }
    }

    val sportsRows = remember(filteredStats) {
        filteredStats.filter { it.event.categoryEnum == EventCategory.SPORTS }
    }
    val culturalRows = remember(filteredStats) {
        filteredStats.filter { it.event.categoryEnum == EventCategory.CULTURAL }
    }

    val avgEvents = if (summary.totalParticipants == 0) {
        "0.0"
    } else {
        String.format(
            Locale.US,
            "%.1f",
            (summary.totalSportsEntries + summary.totalCulturalEntries).toDouble() / summary.totalParticipants.toDouble()
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 880.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "stats_kpis") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        KpiStatCard(
                            label = "Unique Students",
                            value = "${summary.totalParticipants}",
                            subtitle = "Registered participants",
                            icon = Icons.Filled.Groups,
                            accentColor = ForestPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        KpiStatCard(
                            label = "Avg / Student",
                            value = avgEvents,
                            subtitle = "Events per student",
                            icon = Icons.Filled.TrendingUp,
                            accentColor = CrestGoldDeep,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        KpiStatCard(
                            label = "Sports Entries",
                            value = "${summary.totalSportsEntries}",
                            subtitle = "Total sports selections",
                            icon = Icons.Filled.DirectionsRun,
                            accentColor = ForestPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        KpiStatCard(
                            label = "Cultural Entries",
                            value = "${summary.totalCulturalEntries}",
                            subtitle = "Total cultural selections",
                            icon = Icons.Filled.MusicNote,
                            accentColor = CrestGoldDeep,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item(key = "donut_chart") {
                CategorySplitDonutCard(
                    sportsEntries = summary.totalSportsEntries,
                    culturalEntries = summary.totalCulturalEntries
                )
            }

            item(key = "stats_filters") {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search event statistics...") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("stats_search_input")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = categoryFilter == null,
                                onClick = { categoryFilter = null },
                                label = { Text("All Events") }
                            )
                            FilterChip(
                                selected = categoryFilter == EventCategory.SPORTS,
                                onClick = { categoryFilter = EventCategory.SPORTS },
                                label = { Text("Sports") }
                            )
                            FilterChip(
                                selected = categoryFilter == EventCategory.CULTURAL,
                                onClick = { categoryFilter = EventCategory.CULTURAL },
                                label = { Text("Cultural") }
                            )
                        }
                    }
                }
            }

            if (categoryFilter == null || categoryFilter == EventCategory.SPORTS) {
                item(key = "sports_bar_chart") {
                    HorizontalBarChartCard(
                        title = "Sports Participation Chart",
                        subtitle = "Live participant count per sports event",
                        rows = sportsRows,
                        barColor = ForestPrimary,
                        trackColor = EmeraldSoft
                    )
                }
            }

            if (categoryFilter == null || categoryFilter == EventCategory.CULTURAL) {
                item(key = "cultural_bar_chart") {
                    HorizontalBarChartCard(
                        title = "Cultural Participation Chart",
                        subtitle = "Live participant count per cultural event",
                        rows = culturalRows,
                        barColor = CrestGold,
                        trackColor = CrestGoldWash
                    )
                }
            }

            // Event-wise Participation Table
            item(key = "event_wise_table") {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Event-Wise Participation Table",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Calculated in real time from Firestore participant records",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "EVENT",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1.3f)
                            )
                            Text(
                                text = "CATEGORY",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(0.8f)
                            )
                            Text(
                                text = "COUNT",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        filteredStats.forEach { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = row.event.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1.3f)
                                )
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = if (row.event.categoryEnum == EventCategory.SPORTS) EmeraldSoft else CrestGoldWash,
                                    modifier = Modifier.weight(0.8f)
                                ) {
                                    Text(
                                        text = row.event.category,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (row.event.categoryEnum == EventCategory.SPORTS) ForestDeep else CrestGoldDeep,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = "${row.participantCount}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    fontWeight = FontWeight.Bold,
                                    color = ForestPrimary
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}
