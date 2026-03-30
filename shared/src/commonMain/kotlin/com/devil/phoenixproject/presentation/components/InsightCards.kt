@file:Suppress("unused")

package com.devil.phoenixproject.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.devil.phoenixproject.data.repository.BadgeWithProgress
import com.devil.phoenixproject.data.repository.ExerciseRepository
import com.devil.phoenixproject.domain.model.PersonalRecord
import com.devil.phoenixproject.domain.model.WeightUnit
import com.devil.phoenixproject.domain.model.WorkoutSession
import com.devil.phoenixproject.domain.model.effectiveHeaviestKgPerCable
import com.devil.phoenixproject.domain.model.effectiveTotalVolumeKg
import com.devil.phoenixproject.presentation.components.charts.*
import com.devil.phoenixproject.util.KmpLocalDate
import com.devil.phoenixproject.util.KmpUtils
import kotlinx.datetime.*
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Insight card components for workout analytics
 * These components display various analytics and insights about workout data
 */

/**
 * Count real workouts from a list of sessions.
 * Sets sharing the same routineSessionId count as 1 workout.
 * Sets without a routineSessionId count as 1 workout each.
 */
internal fun countRealWorkouts(sessions: List<WorkoutSession>): Int {
    val (withRoutine, withoutRoutine) = sessions.partition { it.routineSessionId != null }
    val routineWorkouts = withRoutine.mapNotNull { it.routineSessionId }.distinct().size
    return routineWorkouts + withoutRoutine.size
}

/**
 * Week-over-week summary data
 */
private data class WeekSummary(
    val workouts: Int,
    val totalVolume: Float,
    val totalReps: Int,
    val prsHit: Int
)

/**
 * Comparison result for week-over-week metrics
 */
private sealed class Comparison {
    data class Increase(val value: String) : Comparison()
    data class Decrease(val value: String) : Comparison()
    object NoChange : Comparison()
    object NoData : Comparison()
}

/**
 * This Week Summary Card - shows week-over-week comparison metrics
 */
@Composable
fun ThisWeekSummaryCard(
    workoutSessions: List<WorkoutSession>,
    personalRecords: List<PersonalRecord>,
    weightUnit: WeightUnit,
    modifier: Modifier = Modifier
) {
    val now = remember { KmpUtils.currentTimeMillis() }
    val oneDayMs = 24L * 60 * 60 * 1000
    val sevenDaysMs = 7 * oneDayMs

    // Calculate week boundaries
    val thisWeekStart = now - sevenDaysMs
    val lastWeekStart = now - (2 * sevenDaysMs)
    val lastWeekEnd = thisWeekStart

    // Calculate summaries for each week
    val (thisWeek, lastWeek) = remember(workoutSessions, personalRecords, now) {
        val thisWeekSessions = workoutSessions.filter { it.timestamp >= thisWeekStart }
        val lastWeekSessions = workoutSessions.filter {
            it.timestamp >= lastWeekStart && it.timestamp < lastWeekEnd
        }

        val thisWeekPRs = personalRecords.filter { it.timestamp >= thisWeekStart }
        val lastWeekPRs = personalRecords.filter {
            it.timestamp >= lastWeekStart && it.timestamp < lastWeekEnd
        }

        val thisWeekSummary = WeekSummary(
            workouts = countRealWorkouts(thisWeekSessions),
            totalVolume = thisWeekSessions.sumOf {
                it.effectiveTotalVolumeKg().toDouble()
            }.toFloat(),
            totalReps = thisWeekSessions.sumOf { it.totalReps },
            prsHit = thisWeekPRs.size
        )

        val lastWeekSummary = WeekSummary(
            workouts = countRealWorkouts(lastWeekSessions),
            totalVolume = lastWeekSessions.sumOf {
                it.effectiveTotalVolumeKg().toDouble()
            }.toFloat(),
            totalReps = lastWeekSessions.sumOf { it.totalReps },
            prsHit = lastWeekPRs.size
        )

        Pair(thisWeekSummary, lastWeekSummary)
    }

    // Helper function to calculate comparison
    fun calculateComparison(current: Int, previous: Int): Comparison {
        return when {
            previous == 0 && current == 0 -> Comparison.NoChange
            previous == 0 -> Comparison.Increase("+$current")
            else -> {
                val diff = current - previous
                when {
                    diff > 0 -> Comparison.Increase("+$diff")
                    diff < 0 -> Comparison.Decrease("$diff")
                    else -> Comparison.NoChange
                }
            }
        }
    }

    fun calculatePercentageComparison(current: Float, previous: Float): Comparison {
        return when {
            previous == 0f && current == 0f -> Comparison.NoChange
            previous == 0f && current > 0f -> Comparison.Increase("+100%")
            previous == 0f -> Comparison.NoData
            else -> {
                val percentChange = ((current - previous) / previous * 100).roundToInt()
                when {
                    percentChange > 0 -> Comparison.Increase("+$percentChange%")
                    percentChange < 0 -> Comparison.Decrease("$percentChange%")
                    else -> Comparison.NoChange
                }
            }
        }
    }

    // Calculate comparisons
    val workoutsComparison = calculateComparison(thisWeek.workouts, lastWeek.workouts)
    val volumeComparison = calculatePercentageComparison(thisWeek.totalVolume, lastWeek.totalVolume)
    val repsComparison = calculateComparison(thisWeek.totalReps, lastWeek.totalReps)
    val prsComparison = calculateComparison(thisWeek.prsHit, lastWeek.prsHit)

    // Format volume for display
    val displayVolume = if (weightUnit == WeightUnit.LB) {
        thisWeek.totalVolume * 2.20462f
    } else {
        thisWeek.totalVolume
    }
    val volumeText = when {
        displayVolume >= 1000 -> "${(displayVolume / 1000).roundToInt()}k ${weightUnit.name.lowercase()}"
        else -> "${displayVolume.roundToInt()} ${weightUnit.name.lowercase()}"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "This week summary: ${thisWeek.workouts} workouts, $volumeText volume, ${thisWeek.totalReps} reps, ${thisWeek.prsHit} PRs"
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "This Week",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Compared to last 7 days",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Stat rows
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WeekStatRow(
                    label = "Workouts",
                    value = "${thisWeek.workouts}",
                    comparison = workoutsComparison
                )

                WeekStatRow(
                    label = "Total Volume",
                    value = volumeText,
                    comparison = volumeComparison
                )

                WeekStatRow(
                    label = "Total Reps",
                    value = "${thisWeek.totalReps}",
                    comparison = repsComparison
                )

                WeekStatRow(
                    label = "PRs Hit",
                    value = "${thisWeek.prsHit}",
                    comparison = prsComparison
                )
            }
        }
    }
}

/**
 * Individual stat row with comparison indicator
 */
@Composable
private fun WeekStatRow(
    label: String,
    value: String,
    comparison: Comparison,
    modifier: Modifier = Modifier
) {
    val (icon, color, comparisonText) = when (comparison) {
        is Comparison.Increase -> Triple(
            Icons.Default.ArrowUpward,
            Color(0xFF4CAF50), // Green
            comparison.value
        )
        is Comparison.Decrease -> Triple(
            Icons.Default.ArrowDownward,
            Color(0xFFF44336), // Red
            comparison.value
        )
        is Comparison.NoChange -> Triple(
            Icons.Default.Remove,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "same"
        )
        is Comparison.NoData -> Triple(
            Icons.Default.Remove,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "-"
        )
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Comparison indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = comparisonText,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun WorkoutModeDistributionCard(
    workoutSessions: List<WorkoutSession>,
    modifier: Modifier = Modifier
) {
    val modeData = remember(workoutSessions) {
        workoutSessions
            .groupingBy { it.mode }
            .eachCount()
            .map { it.key to it.value.toFloat() }
            .sortedByDescending { it.second }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Mode Distribution",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Based on Workout Sessions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (modeData.isNotEmpty()) {
                // Using MuscleGroupCircleChart as a donut chart
                // Chart is now responsive and self-sizing for tablet support
                MuscleGroupCircleChart(
                    data = modeData,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    "No mode data available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp)
                )
            }
        }
    }
}

/**
 * Volume comparison data class for fun volume representations
 */
private data class VolumeComparison(
    val funValue: String,
    val funLabel: String,
    val actualKg: Float
)

/**
 * Format a float to one decimal place (KMP-compatible)
 */
private fun formatOneDecimal(value: Float): String {
    val rounded = kotlin.math.round(value * 10) / 10
    return if (rounded == rounded.toLong().toFloat()) {
        "${rounded.toLong()}.0"
    } else {
        "$rounded"
    }
}

/**
 * Get fun volume comparison based on total volume in kg
 */
private fun getVolumeComparison(totalVolumeKg: Float): VolumeComparison {
    return when {
        totalVolumeKg >= 1_000_000 -> VolumeComparison(
            funValue = formatOneDecimal(totalVolumeKg / 52_000_000f),
            funLabel = "Titanics",
            actualKg = totalVolumeKg
        )
        totalVolumeKg >= 200_000 -> VolumeComparison(
            funValue = formatOneDecimal(totalVolumeKg / 150_000f),
            funLabel = "Blue Whales",
            actualKg = totalVolumeKg
        )
        totalVolumeKg >= 100_000 -> VolumeComparison(
            funValue = formatOneDecimal(totalVolumeKg / 100_000f),
            funLabel = "Jumbo Jets",
            actualKg = totalVolumeKg
        )
        totalVolumeKg >= 50_000 -> VolumeComparison(
            funValue = formatOneDecimal(totalVolumeKg / 5_000f),
            funLabel = "Elephants Moved",
            actualKg = totalVolumeKg
        )
        totalVolumeKg >= 10_000 -> VolumeComparison(
            funValue = formatOneDecimal(totalVolumeKg / 1_500f),
            funLabel = "Cars Crushed",
            actualKg = totalVolumeKg
        )
        else -> VolumeComparison(
            funValue = "${totalVolumeKg.toInt()}",
            funLabel = "kg lifted",
            actualKg = totalVolumeKg
        )
    }
}

/**
 * Lifetime stats data class
 */
data class LifetimeStats(
    val totalWorkouts: Int,
    val totalVolumeKg: Float,
    val totalReps: Int,
    val daysSinceFirst: Long,
    val favoriteExercise: String?,
    val favoriteExerciseCount: Int,
    val favoriteMode: String?,
    val favoriteModeCount: Int
)

/**
 * Calculate lifetime stats from workout sessions
 */
private fun calculateLifetimeStats(
    workoutSessions: List<WorkoutSession>,
    exerciseNames: Map<String, String>
): LifetimeStats {
    if (workoutSessions.isEmpty()) {
        return LifetimeStats(
            totalWorkouts = 0,
            totalVolumeKg = 0f,
            totalReps = 0,
            daysSinceFirst = 0,
            favoriteExercise = null,
            favoriteExerciseCount = 0,
            favoriteMode = null,
            favoriteModeCount = 0
        )
    }

    val totalWorkouts = workoutSessions.size

    val totalVolumeKg = workoutSessions.sumOf {
        it.effectiveTotalVolumeKg().toDouble()
    }.toFloat()

    val totalReps = workoutSessions.sumOf { it.totalReps }

    // Days since first workout
    val firstWorkoutTimestamp = workoutSessions.minOf { it.timestamp }
    val now = KmpUtils.currentTimeMillis()
    val daysSinceFirst = (now - firstWorkoutTimestamp) / (24L * 60 * 60 * 1000)

    // Favorite exercise (by workout count)
    val exerciseCounts = workoutSessions
        .mapNotNull { it.exerciseId }
        .groupingBy { it }
        .eachCount()
    val favoriteExerciseId = exerciseCounts.maxByOrNull { it.value }?.key
    val favoriteExercise = favoriteExerciseId?.let { exerciseNames[it] }
    val favoriteExerciseCount = favoriteExerciseId?.let { exerciseCounts[it] } ?: 0

    // Favorite mode (by workout count)
    val modeCounts = workoutSessions
        .groupingBy { it.mode }
        .eachCount()
    val favoriteMode = modeCounts.maxByOrNull { it.value }?.key
    val favoriteModeCount = favoriteMode?.let { modeCounts[it] } ?: 0

    return LifetimeStats(
        totalWorkouts = totalWorkouts,
        totalVolumeKg = totalVolumeKg,
        totalReps = totalReps,
        daysSinceFirst = daysSinceFirst,
        favoriteExercise = favoriteExercise,
        favoriteExerciseCount = favoriteExerciseCount,
        favoriteMode = favoriteMode,
        favoriteModeCount = favoriteModeCount
    )
}

/**
 * Lifetime Stats Card - shows all-time statistics with fun volume comparisons
 */
@Composable
fun LifetimeStatsCard(
    workoutSessions: List<WorkoutSession>,
    exerciseRepository: ExerciseRepository,
    weightUnit: WeightUnit,
    modifier: Modifier = Modifier
) {
    // Build exercise names map
    var exerciseNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(workoutSessions) {
        val exerciseIds = workoutSessions.mapNotNull { it.exerciseId }.distinct()
        val names = mutableMapOf<String, String>()
        exerciseIds.forEach { id ->
            val exercise = exerciseRepository.getExerciseById(id)
            names[id] = exercise?.name ?: "Unknown Exercise"
        }
        exerciseNames = names
    }

    val stats = remember(workoutSessions, exerciseNames) {
        calculateLifetimeStats(workoutSessions, exerciseNames)
    }

    val volumeComparison = remember(stats.totalVolumeKg) {
        getVolumeComparison(stats.totalVolumeKg)
    }

    // Format actual volume for display
    val actualVolumeDisplay = remember(stats.totalVolumeKg, weightUnit) {
        val displayVolume = if (weightUnit == WeightUnit.LB) {
            stats.totalVolumeKg * 2.20462f
        } else {
            stats.totalVolumeKg
        }
        val unitLabel = weightUnit.name.lowercase()
        when {
            displayVolume >= 1_000_000 -> "${formatOneDecimal(displayVolume / 1_000_000f)}M $unitLabel"
            displayVolume >= 1_000 -> "${formatOneDecimal(displayVolume / 1_000f)}k $unitLabel"
            else -> "${displayVolume.toInt()} $unitLabel"
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Lifetime Stats",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Your all-time achievements",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (workoutSessions.isEmpty()) {
                Text(
                    "Complete workouts to see your lifetime stats!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Total Workouts
                    LifetimeStatRow(
                        label = "Total Workouts",
                        value = "${stats.totalWorkouts}",
                        subtext = null
                    )

                    // Total Volume with fun comparison
                    if (stats.totalVolumeKg >= 10_000) {
                        // Show fun comparison prominently
                        LifetimeStatRow(
                            label = "Total Volume",
                            value = "${volumeComparison.funValue} ${volumeComparison.funLabel}",
                            subtext = actualVolumeDisplay,
                            isFunStat = true
                        )
                    } else {
                        LifetimeStatRow(
                            label = "Total Volume",
                            value = actualVolumeDisplay,
                            subtext = null
                        )
                    }

                    // Total Reps
                    LifetimeStatRow(
                        label = "Total Reps",
                        value = when {
                            stats.totalReps >= 1000 -> "${formatOneDecimal(stats.totalReps / 1000f)}k"
                            else -> "${stats.totalReps}"
                        },
                        subtext = null
                    )

                    // Days Since First Workout
                    if (stats.daysSinceFirst > 0) {
                        LifetimeStatRow(
                            label = "Days Since First",
                            value = "${stats.daysSinceFirst}",
                            subtext = "days of gains"
                        )
                    }

                    // Favorite Exercise
                    stats.favoriteExercise?.let { exercise ->
                        LifetimeStatRow(
                            label = "Favorite Exercise",
                            value = exercise,
                            subtext = "${stats.favoriteExerciseCount} workouts"
                        )
                    }

                    // Favorite Mode
                    stats.favoriteMode?.let { mode ->
                        LifetimeStatRow(
                            label = "Favorite Mode",
                            value = mode,
                            subtext = "${stats.favoriteModeCount} workouts"
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual stat row for Lifetime Stats Card
 */
@Composable
private fun LifetimeStatRow(
    label: String,
    value: String,
    subtext: String?,
    isFunStat: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isFunStat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            subtext?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Next Badge Progress Card - shows the user's closest badges to being earned
 *
 * @param badgesWithProgress List of all badges with their progress
 * @param onBadgeClick Callback when a badge is tapped (navigates to Badges screen)
 * @param modifier Optional modifier
 */
@Composable
fun NextBadgeProgressCard(
    badgesWithProgress: List<BadgeWithProgress>,
    onBadgeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Filter and sort badges:
    // 1. Exclude earned badges
    // 2. Exclude secret badges (isSecret = true)
    // 3. Sort by progress percentage descending
    // 4. Take top 3
    val nextBadges = remember(badgesWithProgress) {
        badgesWithProgress
            .filter { !it.isEarned && !it.badge.isSecret }
            .sortedByDescending { it.progressPercent }
            .take(3)
    }

    // Don't show if no badges to display
    if (nextBadges.isEmpty()) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onBadgeClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Next Badges",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Your closest achievements",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "View all badges",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                nextBadges.forEach { badgeWithProgress ->
                    NextBadgeProgressItem(badgeWithProgress = badgeWithProgress)
                }
            }
        }
    }
}

/**
 * Individual badge progress item within the NextBadgeProgressCard
 */
@Composable
private fun NextBadgeProgressItem(
    badgeWithProgress: BadgeWithProgress,
    modifier: Modifier = Modifier
) {
    val badge = badgeWithProgress.badge
    val tierColor = Color(badge.tier.colorHex)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Badge icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(tierColor.copy(alpha = 0.8f), tierColor.copy(alpha = 0.3f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = getBadgeIconForProgress(badge.iconResource),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // Badge info and progress
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = badge.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = badge.getProgressDescription(badgeWithProgress.currentProgress),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Progress bar with tier color
            LinearProgressIndicator(
                progress = { badgeWithProgress.progressPercent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = tierColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

/**
 * Helper function to map badge icon resource to ImageVector
 */
private fun getBadgeIconForProgress(iconResource: String): ImageVector {
    return when (iconResource) {
        "fire" -> Icons.Default.LocalFireDepartment
        "trophy" -> Icons.Default.EmojiEvents
        "dumbbell" -> Icons.Default.FitnessCenter
        "repeat" -> Icons.Default.Repeat
        "compass" -> Icons.Default.Explore
        "calendar" -> Icons.Default.CalendarMonth
        "sun" -> Icons.Default.WbSunny
        "moon" -> Icons.Default.NightsStay
        "weight" -> Icons.Default.FitnessCenter
        "lightning" -> Icons.Default.Bolt
        "body" -> Icons.Default.Accessibility
        "phoenix" -> Icons.Default.LocalFireDepartment
        "shield" -> Icons.Default.Shield
        "list" -> Icons.Default.Checklist
        else -> Icons.Default.Star
    }
}

/**
 * Data class representing a single day's workout activity
 */
private data class DayActivity(
    val date: KmpLocalDate,
    val volume: Float,
    val workoutCount: Int
)

/**
 * Calendar Heatmap Card - GitHub-style contribution graph showing workout activity
 *
 * Displays last 13 weeks of workout activity with color intensity based on volume lifted.
 * Rows represent days of week (Mon-Sun), columns represent weeks.
 *
 * @param workoutSessions List of all workout sessions
 * @param weightUnit Weight unit for display
 * @param modifier Optional modifier
 */
@Composable
fun CalendarHeatmapCard(
    workoutSessions: List<WorkoutSession>,
    weightUnit: WeightUnit,
    modifier: Modifier = Modifier
) {
    // Calculate daily volumes for last 13 weeks (91 days)
    val (dailyActivities, maxVolume, monthLabels) = remember(workoutSessions) {
        val today = KmpLocalDate.today()
        val daysToShow = 91 // 13 weeks
        val startDate = today.minusDays(daysToShow - 1)

        // Group sessions by date and calculate volume
        val activityMap = mutableMapOf<String, DayActivity>()

        workoutSessions.forEach { session ->
                val sessionDate = KmpLocalDate.fromTimestamp(session.timestamp)
                // Only include sessions within our date range
                if (!sessionDate.isBefore(startDate) && !sessionDate.isAfter(today)) {
                    val key = sessionDate.toKey()
                    val volume = session.effectiveTotalVolumeKg()
                    val existing = activityMap[key]
                    if (existing != null) {
                        activityMap[key] = existing.copy(
                            volume = existing.volume + volume,
                            workoutCount = existing.workoutCount + 1
                    )
                } else {
                    activityMap[key] = DayActivity(
                        date = sessionDate,
                        volume = volume,
                        workoutCount = 1
                    )
                }
            }
        }

        // Build list of all days
        val dailyList = mutableListOf<DayActivity>()
        var currentDate = startDate
        while (!currentDate.isAfter(today)) {
            val key = currentDate.toKey()
            dailyList.add(
                activityMap[key] ?: DayActivity(
                    date = currentDate,
                    volume = 0f,
                    workoutCount = 0
                )
            )
            currentDate = currentDate.plusDays(1)
        }

        // Find max volume for intensity calculation
        val maxVol = dailyList.maxOfOrNull { it.volume } ?: 0f

        // Build month labels (find first day of each month in range)
        val months = mutableListOf<Pair<String, Int>>() // Month name, column index
        var prevMonth = -1
        dailyList.forEachIndexed { index, activity ->
            if (activity.date.month != prevMonth) {
                val monthName = getMonthShortName(activity.date.month)
                // Calculate which column this falls into (index / 7 for week column)
                val weekIndex = index / 7
                months.add(monthName to weekIndex)
                prevMonth = activity.date.month
            }
        }

        Triple(dailyList, maxVol, months)
    }

    // Organize into grid: 7 rows (days) x N columns (weeks)
    // Row 0 = Monday, Row 6 = Sunday (ISO week)
    val gridData = remember(dailyActivities) {
        // Find the starting day of week (1=Monday, 7=Sunday)
        val firstDayOfWeek = if (dailyActivities.isNotEmpty()) {
            val firstDate = dailyActivities.first().date
            getDayOfWeekIso(firstDate)
        } else 1

        // Calculate total weeks needed
        val totalDays = dailyActivities.size + (firstDayOfWeek - 1)
        val numWeeks = (totalDays + 6) / 7

        // Create grid: Array of 7 rows, each containing week columns
        val grid = Array(7) { arrayOfNulls<DayActivity?>(numWeeks) }

        // Fill in the grid
        dailyActivities.forEachIndexed { index, activity ->
            val adjustedIndex = index + (firstDayOfWeek - 1)
            val weekCol = adjustedIndex / 7
            val dayRow = adjustedIndex % 7
            if (weekCol < numWeeks && dayRow < 7) {
                grid[dayRow][weekCol] = activity
            }
        }

        grid
    }

    val cellSize = 14.dp
    val cellGap = 2.dp
    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")

    // Count workout days for accessibility
    val workoutDays = remember(dailyActivities) {
        dailyActivities.count { it.workoutCount > 0 }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Activity heatmap: $workoutDays workout days in the last 13 weeks"
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Activity",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Last 13 weeks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (gridData.isNotEmpty() && gridData[0].isNotEmpty()) {
                // Month labels row - simplified positioning
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp), // Align with grid (day labels width)
                    horizontalArrangement = Arrangement.Start
                ) {
                    // Filter labels to avoid overlapping (keep labels at least 3 weeks apart)
                    val filteredLabels = monthLabels.filterIndexed { idx, (_, weekIndex) ->
                        if (idx == 0) true
                        else {
                            val prevWeek = monthLabels.getOrNull(idx - 1)?.second ?: -10
                            weekIndex - prevWeek >= 3
                        }
                    }

                    var currentWeek = 0
                    filteredLabels.forEach { (monthName, weekIndex) ->
                        if (weekIndex > currentWeek) {
                            // Add spacer for weeks between labels
                            val spacerWeeks = weekIndex - currentWeek
                            Spacer(modifier = Modifier.width((spacerWeeks * 16).dp))
                        }
                        Text(
                            text = monthName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        currentWeek = weekIndex + 2 // Account for label width
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Heatmap grid with day labels
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Day of week labels column
                    Column(
                        modifier = Modifier.width(20.dp),
                        verticalArrangement = Arrangement.spacedBy(cellGap)
                    ) {
                        dayLabels.forEachIndexed { index, label ->
                            // Only show labels for Mon, Wed, Fri (indices 0, 2, 4)
                            Box(
                                modifier = Modifier.size(cellSize),
                                contentAlignment = Alignment.Center
                            ) {
                                if (index % 2 == 0) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Grid of cells
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(cellGap)
                    ) {
                        // Each column is a week
                        for (weekIndex in gridData[0].indices) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(cellGap)
                            ) {
                                // Each row is a day of week
                                for (dayIndex in 0 until 7) {
                                    val activity = gridData[dayIndex][weekIndex]
                                    HeatmapCell(
                                        activity = activity,
                                        maxVolume = maxVolume,
                                        cellSize = cellSize
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Legend
                HeatmapLegend()
            } else {
                Text(
                    "Complete workouts to see your activity heatmap.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp)
                )
            }
        }
    }
}

/**
 * Individual heatmap cell with color based on volume intensity
 */
@Composable
private fun HeatmapCell(
    activity: DayActivity?,
    maxVolume: Float,
    cellSize: Dp,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    val cellColor = remember(activity, maxVolume, primaryColor, surfaceVariant) {
        when {
            activity == null -> Color.Transparent
            activity.volume <= 0f -> surfaceVariant.copy(alpha = 0.5f)
            maxVolume <= 0f -> surfaceVariant.copy(alpha = 0.5f)
            else -> {
                // Calculate intensity level (0-4)
                val ratio = activity.volume / maxVolume
                val level = when {
                    ratio >= 0.75f -> 4
                    ratio >= 0.50f -> 3
                    ratio >= 0.25f -> 2
                    ratio > 0f -> 1
                    else -> 0
                }
                // Apply intensity to primary color
                when (level) {
                    0 -> surfaceVariant.copy(alpha = 0.5f)
                    1 -> primaryColor.copy(alpha = 0.3f)
                    2 -> primaryColor.copy(alpha = 0.5f)
                    3 -> primaryColor.copy(alpha = 0.75f)
                    else -> primaryColor
                }
            }
        }
    }

    Box(
        modifier = modifier
            .size(cellSize)
            .clip(RoundedCornerShape(2.dp))
            .background(cellColor)
    )
}

/**
 * Legend showing intensity levels
 */
@Composable
private fun HeatmapLegend(
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Less",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))

        // Level 0 - no workout
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(surfaceVariant.copy(alpha = 0.5f))
        )
        Spacer(modifier = Modifier.width(2.dp))

        // Level 1
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(primaryColor.copy(alpha = 0.3f))
        )
        Spacer(modifier = Modifier.width(2.dp))

        // Level 2
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(primaryColor.copy(alpha = 0.5f))
        )
        Spacer(modifier = Modifier.width(2.dp))

        // Level 3
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(primaryColor.copy(alpha = 0.75f))
        )
        Spacer(modifier = Modifier.width(2.dp))

        // Level 4
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(primaryColor)
        )

        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "More",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Get short month name from month number (1-12)
 */
private fun getMonthShortName(month: Int): String {
    return when (month) {
        1 -> "Jan"
        2 -> "Feb"
        3 -> "Mar"
        4 -> "Apr"
        5 -> "May"
        6 -> "Jun"
        7 -> "Jul"
        8 -> "Aug"
        9 -> "Sep"
        10 -> "Oct"
        11 -> "Nov"
        12 -> "Dec"
        else -> ""
    }
}

/**
 * Get ISO day of week (1=Monday, 7=Sunday) from KmpLocalDate
 */
private fun getDayOfWeekIso(date: KmpLocalDate): Int {
    val localDate = kotlinx.datetime.LocalDate(date.year, date.month, date.dayOfMonth)
    return localDate.dayOfWeek.ordinal + 1 // DayOfWeek.MONDAY.ordinal is 0
}

// ---------------------------------------------------------------------------
// Progressive Overload Card
// ---------------------------------------------------------------------------

/**
 * Progressive Overload Card -- shows the heaviest working-set weight per
 * session date for a user-selected exercise, rendered as a line chart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressiveOverloadCard(
    workoutSessions: List<WorkoutSession>,
    weightUnit: WeightUnit,
    modifier: Modifier = Modifier
) {
    // Collect distinct exercises that have an exerciseId
    val exercises = remember(workoutSessions) {
        workoutSessions
            .filter { it.exerciseId != null }
            .groupBy { it.exerciseId!! }
            .map { (id, sessions) ->
                val name = sessions.firstNotNullOfOrNull { it.exerciseName } ?: id
                id to name
            }
            .sortedBy { it.second.lowercase() }
    }

    var selectedExerciseId by remember(exercises) {
        mutableStateOf(exercises.firstOrNull()?.first)
    }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val selectedName = exercises.firstOrNull { it.first == selectedExerciseId }?.second ?: ""

    // Build chart data: heaviest weight per session date for selected exercise
    val chartData = remember(workoutSessions, selectedExerciseId, weightUnit) {
        if (selectedExerciseId == null) return@remember emptyList<Pair<String, Float>>()
        val filtered = workoutSessions.filter { it.exerciseId == selectedExerciseId }
        // Group by date string, pick heaviest weight per cable in each date bucket
        filtered
            .groupBy { KmpUtils.formatTimestamp(it.timestamp, "MMM d") }
            .map { (dateLabel, sessions) ->
                val heaviest = sessions.maxOf { it.effectiveHeaviestKgPerCable() * 2f }
                val adjusted = if (weightUnit == WeightUnit.LB) heaviest * 2.20462f else heaviest
                dateLabel to adjusted
            }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Progressive Overload",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Heaviest working set per session",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (exercises.isEmpty()) {
                Text(
                    "No exercise data available yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp)
                )
            } else {
                // Exercise dropdown
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Exercise") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                        },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        exercises.forEach { (id, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    selectedExerciseId = id
                                    dropdownExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (chartData.size >= 2) {
                    OverloadLineChart(
                        data = chartData,
                        weightUnit = weightUnit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                } else {
                    Text(
                        "Need at least 2 sessions to chart progression.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
        }
    }
}

/**
 * Canvas-based line chart for progressive overload data.
 * Uses Compose Multiplatform text drawing (TextMeasurer) -- no Android-only APIs.
 */
@Composable
fun OverloadLineChart(
    data: List<Pair<String, Float>>, // label to weight
    weightUnit: WeightUnit,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
) {
    if (data.size < 2) return

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    val unitSuffix = if (weightUnit == WeightUnit.LB) "lb" else "kg"

    val values = data.map { it.second }
    val minVal = values.min()
    val maxVal = values.max()
    val range = (maxVal - minVal).coerceAtLeast(1f)
    val yPad = range * 0.1f
    val yMin = (minVal - yPad).coerceAtLeast(0f)
    val yMax = maxVal + yPad
    val yRange = yMax - yMin

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val bottomPad = 22.dp.toPx()
        val chartH = h - bottomPad

        val xStep = w / (data.size - 1).coerceAtLeast(1)

        // Grid lines (3 horizontal)
        for (i in 0..2) {
            val y = chartH * i / 2f
            drawLine(gridColor, Offset(0f, y), Offset(w, y))
        }

        // Build paths
        val linePath = Path()
        val fillPath = Path()

        data.forEachIndexed { idx, (_, value) ->
            val x = idx * xStep
            val normY = (value - yMin) / yRange
            val y = chartH - (normY * chartH)

            if (idx == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, chartH)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }

            // Data point
            drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(x, y))
            drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(x, y))
        }

        fillPath.lineTo(w, chartH)
        fillPath.close()

        // Draw fill gradient
        drawPath(
            fillPath,
            brush = Brush.verticalGradient(
                listOf(fillColor, fillColor.copy(alpha = 0f)),
                startY = 0f,
                endY = chartH
            )
        )

        // Draw line
        drawPath(linePath, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))

        // Y-axis labels (min / max)
        drawText(
            textMeasurer = textMeasurer,
            text = "${maxVal.roundToInt()} $unitSuffix",
            style = labelStyle.merge(TextStyle(color = labelColor)),
            topLeft = Offset(4f, 4f)
        )
        drawText(
            textMeasurer = textMeasurer,
            text = "${minVal.roundToInt()} $unitSuffix",
            style = labelStyle.merge(TextStyle(color = labelColor)),
            topLeft = Offset(4f, chartH - 16.dp.toPx())
        )

        // X-axis labels (first and last)
        drawText(
            textMeasurer = textMeasurer,
            text = data.first().first,
            style = labelStyle.merge(TextStyle(color = labelColor)),
            topLeft = Offset(0f, chartH + 4.dp.toPx())
        )
        val lastLayout = textMeasurer.measure(data.last().first, labelStyle)
        drawText(
            textMeasurer = textMeasurer,
            text = data.last().first,
            style = labelStyle.merge(TextStyle(color = labelColor)),
            topLeft = Offset(w - lastLayout.size.width, chartH + 4.dp.toPx())
        )
    }
}

// ---------------------------------------------------------------------------
// Workout Frequency Card
// ---------------------------------------------------------------------------

/**
 * Workout Frequency Card -- bar chart of real workouts per week for the last
 * 8 weeks with a dashed target line at 3 workouts/week.
 */
@Composable
fun WorkoutFrequencyCard(
    workoutSessions: List<WorkoutSession>,
    modifier: Modifier = Modifier,
    weeklyTarget: Int = 3
) {
    val now = remember { KmpUtils.currentTimeMillis() }
    val oneDayMs = 24L * 60 * 60 * 1000
    val oneWeekMs = 7 * oneDayMs

    // Build per-week workout counts for the last 8 weeks
    val weeklyData = remember(workoutSessions, now) {
        (0 until 8).reversed().map { weeksAgo ->
            val weekEnd = now - (weeksAgo * oneWeekMs)
            val weekStart = weekEnd - oneWeekMs
            val sessionsInWeek = workoutSessions.filter {
                it.timestamp in weekStart until weekEnd
            }
            val label = if (weeksAgo == 0) "This" else "${weeksAgo}w"
            label to countRealWorkouts(sessionsInWeek)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Workout Frequency",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Real workouts per week (last 8 weeks)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (weeklyData.any { it.second > 0 }) {
                FrequencyBarChart(
                    data = weeklyData,
                    target = weeklyTarget,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            } else {
                Text(
                    "Complete workouts to see your weekly frequency.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp)
                )
            }
        }
    }
}

/**
 * Canvas-based bar chart with a dashed target line.
 * Uses TextMeasurer for KMP-compatible text rendering.
 */
@Composable
fun FrequencyBarChart(
    data: List<Pair<String, Int>>,
    target: Int,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    targetColor: Color = MaterialTheme.colorScheme.error
) {
    if (data.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)

    val maxValue = (data.maxOf { it.second }).coerceAtLeast(target + 1)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val bottomPad = 22.dp.toPx()
        val chartH = h - bottomPad
        val barCount = data.size
        val barTotalWidth = w / barCount
        val barWidth = barTotalWidth * 0.6f
        val gap = barTotalWidth * 0.2f

        // Horizontal grid lines
        for (i in 0..maxValue step (maxValue / 3).coerceAtLeast(1)) {
            val y = chartH - (i.toFloat() / maxValue * chartH)
            drawLine(gridColor, Offset(0f, y), Offset(w, y))
        }

        // Bars
        data.forEachIndexed { idx, (label, count) ->
            val barH = (count.toFloat() / maxValue) * chartH
            val x = idx * barTotalWidth + gap
            val y = chartH - barH

            drawRoundRect(
                color = if (count >= target) barColor else barColor.copy(alpha = 0.5f),
                topLeft = Offset(x, y),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            // X-axis label
            val textLayout = textMeasurer.measure(label, labelStyle)
            val textX = x + barWidth / 2f - textLayout.size.width / 2f
            drawText(
                textMeasurer = textMeasurer,
                text = label,
                style = labelStyle.merge(TextStyle(color = labelColor)),
                topLeft = Offset(textX, chartH + 4.dp.toPx())
            )

            // Count label above bar
            if (count > 0) {
                val countText = "$count"
                val countLayout = textMeasurer.measure(countText, labelStyle)
                val countX = x + barWidth / 2f - countLayout.size.width / 2f
                drawText(
                    textMeasurer = textMeasurer,
                    text = countText,
                    style = labelStyle.merge(TextStyle(color = labelColor)),
                    topLeft = Offset(countX, y - countLayout.size.height - 2.dp.toPx())
                )
            }
        }

        // Dashed target line
        val targetY = chartH - (target.toFloat() / maxValue * chartH)
        drawLine(
            color = targetColor,
            start = Offset(0f, targetY),
            end = Offset(w, targetY),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
        )

        // Target label
        val targetLabel = "Target: $target"
        drawText(
            textMeasurer = textMeasurer,
            text = targetLabel,
            style = labelStyle.merge(TextStyle(color = targetColor)),
            topLeft = Offset(w - textMeasurer.measure(targetLabel, labelStyle).size.width - 4f, targetY - 16.dp.toPx())
        )
    }
}

// ---------------------------------------------------------------------------
// Volume By Exercise Card
// ---------------------------------------------------------------------------

private enum class VolumePeriod(val label: String) {
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    ALL_TIME("All Time")
}

/**
 * Volume By Exercise Card -- shows horizontal progress bars per exercise
 * for a selectable time period (This Week / This Month / All Time).
 */
@Composable
fun VolumeByExerciseCard(
    workoutSessions: List<WorkoutSession>,
    weightUnit: WeightUnit,
    modifier: Modifier = Modifier
) {
    var selectedPeriod by remember { mutableStateOf(VolumePeriod.THIS_WEEK) }

    val now = remember { KmpUtils.currentTimeMillis() }
    val oneDayMs = 24L * 60 * 60 * 1000

    val volumeByExercise = remember(workoutSessions, selectedPeriod, weightUnit) {
        val cutoff = when (selectedPeriod) {
            VolumePeriod.THIS_WEEK -> now - 7 * oneDayMs
            VolumePeriod.THIS_MONTH -> now - 30 * oneDayMs
            VolumePeriod.ALL_TIME -> 0L
        }

        workoutSessions
            .filter { it.timestamp >= cutoff && it.exerciseId != null }
            .groupBy { it.exerciseId!! }
            .map { (_, sessions) ->
                val name = sessions.firstNotNullOfOrNull { it.exerciseName } ?: "Unknown"
                val volumeKg = sessions.sumOf { it.effectiveTotalVolumeKg().toDouble() }.toFloat()
                val displayVolume = if (weightUnit == WeightUnit.LB) volumeKg * 2.20462f else volumeKg
                name to displayVolume
            }
            .sortedByDescending { it.second }
            .take(10) // Top 10 exercises
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Volume by Exercise",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Total volume lifted per exercise",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Period selector chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                VolumePeriod.entries.forEach { period ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { selectedPeriod = period },
                        label = {
                            Text(
                                period.label,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (volumeByExercise.isEmpty()) {
                Text(
                    "No exercise data for this period.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                val maxVolume = volumeByExercise.maxOf { it.second }
                val unitLabel = if (weightUnit == WeightUnit.LB) "lb" else "kg"

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    volumeByExercise.forEach { (name, volume) ->
                        ExerciseVolumeRow(
                            exerciseName = name,
                            volume = volume,
                            maxVolume = maxVolume,
                            unitLabel = unitLabel
                        )
                    }
                }
            }
        }
    }
}

/**
 * A single row showing exercise name, horizontal progress bar, and volume.
 */
@Composable
private fun ExerciseVolumeRow(
    exerciseName: String,
    volume: Float,
    maxVolume: Float,
    unitLabel: String,
    modifier: Modifier = Modifier
) {
    val fraction = if (maxVolume > 0f) (volume / maxVolume).coerceIn(0f, 1f) else 0f
    val barColor = MaterialTheme.colorScheme.primary

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = exerciseName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatVolumeCompact(volume, unitLabel),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

/**
 * Format volume into a compact human-readable string.
 */
private fun formatVolumeCompact(volume: Float, unitLabel: String): String {
    return when {
        volume >= 1_000_000 -> "${formatOneDecimal(volume / 1_000_000f)}M $unitLabel"
        volume >= 1_000 -> "${formatOneDecimal(volume / 1_000f)}k $unitLabel"
        else -> "${volume.roundToInt()} $unitLabel"
    }
}

// ---------------------------------------------------------------------------
// Muscle Volume Tracking Card
// ---------------------------------------------------------------------------

/**
 * Normalise raw muscle-group strings into granular display groups.
 * Handles both new granular values (FRONT_DELTS, LATS, etc.) and legacy
 * broad values (ARMS, BACK, LEGS, SHOULDERS) for unmapped exercises.
 */
private fun normalizeGroup(raw: String): String = when (raw.trim().uppercase()) {
    "CHEST" -> "Chest"
    "LATS" -> "Lats"
    "UPPER_BACK", "BACK" -> "Upper Back"
    "FRONT_DELTS" -> "Front Delts"
    "SIDE_DELTS" -> "Side Delts"
    "REAR_DELTS" -> "Rear Delts"
    "BICEPS" -> "Biceps"
    "TRICEPS" -> "Triceps"
    "ARMS" -> "Arms" // legacy fallback
    "SHOULDERS" -> "Shoulders" // legacy fallback
    "QUADS" -> "Quads"
    "HAMSTRINGS" -> "Hamstrings"
    "GLUTES" -> "Glutes"
    "CALVES" -> "Calves"
    "LEGS" -> "Legs" // legacy fallback
    "CORE", "ABS" -> "Core"
    else -> raw.trim().replaceFirstChar { it.uppercase() }
}

/** Filter categories for the muscle volume card. */
private val MUSCLE_FILTER_CATEGORIES = listOf(
    "All", "Push", "Pull", "Legs", "Core",
    "Chest", "Back", "Shoulders", "Arms"
)

/** Which granular groups belong to each filter category. */
private fun groupsForFilter(filter: String): Set<String>? = when (filter) {
    "All" -> null // show all
    "Push" -> setOf("Chest", "Front Delts", "Side Delts", "Triceps", "Shoulders")
    "Pull" -> setOf("Lats", "Upper Back", "Rear Delts", "Biceps", "Arms")
    "Legs" -> setOf("Quads", "Hamstrings", "Glutes", "Calves", "Legs")
    "Core" -> setOf("Core")
    "Chest" -> setOf("Chest")
    "Back" -> setOf("Lats", "Upper Back", "Rear Delts")
    "Shoulders" -> setOf("Front Delts", "Side Delts", "Rear Delts", "Shoulders")
    "Arms" -> setOf("Biceps", "Triceps", "Arms")
    else -> null
}

/**
 * Data holder for per-muscle-group weekly set counts and zone classification.
 */
private data class MuscleGroupVolume(
    val name: String,
    val sets: Int
) {
    val zone: String get() = when {
        sets >= 10 -> "Focus"
        sets >= 5 -> "Growth"
        sets >= 1 -> "Maintaining"
        else -> "Not trained"
    }

    val zoneColor: Color get() = when {
        sets >= 10 -> Color(0xFFD4A017)  // gold
        sets >= 5 -> Color(0xFF4CAF50)   // green
        sets >= 1 -> Color(0xFF42A5F5)   // blue
        else -> Color(0xFF9E9E9E)        // grey
    }
}

/**
 * Calculate the period time window (start and end epoch millis) based on
 * [periodMode] and [periodOffset].
 *
 * - **Week**: ISO Monday-to-Sunday. offset 0 = week containing today.
 * - **Month**: Calendar month. offset 0 = this month.
 * - **Year**: Calendar year. offset 0 = this year.
 *
 * Returns a Triple of (startMillis, endMillis, displayLabel).
 */
private fun calculatePeriodWindow(
    periodMode: String,
    periodOffset: Int
): Triple<Long, Long, String> {
    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(tz).date

    when (periodMode) {
        "Week" -> {
            // Find Monday of the current week
            val daysSinceMonday = today.dayOfWeek.isoDayNumber - 1
            val thisMonday = today.minus(daysSinceMonday, DateTimeUnit.DAY)
            // Apply offset (each offset step = 7 days)
            val targetMonday = thisMonday.plus(periodOffset * 7, DateTimeUnit.DAY)
            val targetSunday = targetMonday.plus(6, DateTimeUnit.DAY)

            val startMs = targetMonday.atStartOfDayIn(tz).toEpochMilliseconds()
            val endMs = targetSunday.plus(1, DateTimeUnit.DAY)
                .atStartOfDayIn(tz).toEpochMilliseconds()

            val startLabel = "${targetMonday.dayOfMonth} ${getMonthShortName(targetMonday.monthNumber)}"
            val endLabel = "${targetSunday.dayOfMonth} ${getMonthShortName(targetSunday.monthNumber)}"
            return Triple(startMs, endMs, "$startLabel - $endLabel")
        }
        "Month" -> {
            // Start of this month, then apply offset
            val targetMonth = today.month.number + periodOffset
            // Handle wrap-around
            var adjustedYear = today.year
            var adjustedMonth = targetMonth
            while (adjustedMonth < 1) { adjustedMonth += 12; adjustedYear-- }
            while (adjustedMonth > 12) { adjustedMonth -= 12; adjustedYear++ }

            val firstDay = LocalDate(adjustedYear, adjustedMonth, 1)
            val nextMonth = firstDay.plus(1, DateTimeUnit.MONTH)

            val startMs = firstDay.atStartOfDayIn(tz).toEpochMilliseconds()
            val endMs = nextMonth.atStartOfDayIn(tz).toEpochMilliseconds()

            val monthName = firstDay.month.name.lowercase().replaceFirstChar { it.uppercase() }
            return Triple(startMs, endMs, "$monthName $adjustedYear")
        }
        "Year" -> {
            val targetYear = today.year + periodOffset
            val firstDay = LocalDate(targetYear, 1, 1)
            val nextYear = LocalDate(targetYear + 1, 1, 1)

            val startMs = firstDay.atStartOfDayIn(tz).toEpochMilliseconds()
            val endMs = nextYear.atStartOfDayIn(tz).toEpochMilliseconds()

            return Triple(startMs, endMs, "$targetYear")
        }
        else -> {
            // Fallback: last 7 days
            val startMs = Clock.System.now().toEpochMilliseconds() - 7L * 24 * 60 * 60 * 1000
            val endMs = Clock.System.now().toEpochMilliseconds()
            return Triple(startMs, endMs, "Last 7 days")
        }
    }
}

/**
 * Muscle Volume Card -- per-muscle-group set counts with
 * training-zone colour indicators, period navigation, and filter chips.
 *
 * Uses [ExerciseRepository.getExerciseById] to resolve each session's
 * exerciseId to its muscleGroups field, then counts sets with workingReps > 0
 * in the selected time period. Each set credits ALL of the exercise's muscle groups.
 *
 * @param workoutSessions All available workout sessions
 * @param exerciseRepository Repository for exercise lookups
 * @param modifier Optional modifier
 */
@Composable
fun MuscleVolumeCard(
    workoutSessions: List<WorkoutSession>,
    exerciseRepository: ExerciseRepository,
    modifier: Modifier = Modifier
) {
    // Period navigation state
    var periodMode by remember { mutableStateOf("Week") }
    var periodOffset by remember { mutableStateOf(0) }
    val periodModes = listOf("Week", "Month", "Year")

    // Calculate time window based on current period mode and offset
    val (periodStartMs, periodEndMs, periodLabel) = remember(periodMode, periodOffset) {
        calculatePeriodWindow(periodMode, periodOffset)
    }

    // Filter sessions to the selected period with at least one working rep and a linked exercise
    val periodSessions = remember(workoutSessions, periodStartMs, periodEndMs) {
        workoutSessions.filter { session ->
            session.timestamp >= periodStartMs &&
                session.timestamp < periodEndMs &&
                session.workingReps > 0 &&
                session.exerciseId != null
        }
    }

    // Resolve exercise IDs -> muscle-group lists asynchronously
    val muscleGroupMap by produceState(
        initialValue = emptyMap<String, List<String>>(),
        key1 = periodSessions
    ) {
        val ids = periodSessions.mapNotNull { it.exerciseId }.distinct()
        val result = mutableMapOf<String, List<String>>()
        ids.forEach { id ->
            val exercise = exerciseRepository.getExerciseById(id)
            if (exercise != null) {
                result[id] = exercise.muscleGroups
                    .split(",")
                    .map { normalizeGroup(it) }
                    .distinct()
            }
        }
        value = result
    }

    // Aggregate sets per canonical group
    val groupVolumes = remember(periodSessions, muscleGroupMap) {
        val counts = mutableMapOf<String, Int>()
        periodSessions.forEach { session ->
            val groups = muscleGroupMap[session.exerciseId] ?: return@forEach
            groups.forEach { group ->
                counts[group] = (counts[group] ?: 0) + 1
            }
        }
        counts.entries
            .map { (group, count) -> MuscleGroupVolume(name = group, sets = count) }
            .sortedByDescending { it.sets }
    }

    // Filter state
    var selectedFilter by remember { mutableStateOf("All") }
    val filters = MUSCLE_FILTER_CATEGORIES

    val displayedGroups = remember(groupVolumes, selectedFilter) {
        val allowedGroups = groupsForFilter(selectedFilter)
        if (allowedGroups == null) groupVolumes
        else groupVolumes.filter { it.name in allowedGroups }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Muscle volume: sets per muscle group for $periodLabel"
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Muscle Volume",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                periodLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Period mode chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                periodModes.forEach { mode ->
                    FilterChip(
                        selected = periodMode == mode,
                        onClick = {
                            periodMode = mode
                            periodOffset = 0
                        },
                        label = {
                            Text(
                                text = mode,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Back / Forward navigation row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { periodOffset-- },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous period",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = periodLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(
                    onClick = { periodOffset++ },
                    enabled = periodOffset < 0,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next period",
                        tint = if (periodOffset < 0) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Muscle group filter chip row (horizontally scrollable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                filters.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = {
                            Text(
                                text = filter,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (periodSessions.isEmpty()) {
                Text(
                    "No workouts in this period.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    displayedGroups.forEach { group ->
                        MuscleGroupRow(group = group)
                    }
                }
            }
        }
    }
}

/**
 * Single row inside [MuscleVolumeCard] showing a muscle group's name,
 * weekly set count, zone label and a 10-segment colour bar.
 */
@Composable
private fun MuscleGroupRow(
    group: MuscleGroupVolume,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Top line: name + "X of 10 weekly sets" + zone label
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${group.sets} of 10 weekly sets",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = group.zone,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = group.zoneColor
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 10-segment bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val filledSegments = group.sets.coerceAtMost(10)
            for (i in 0 until 10) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (i < filledSegments) group.zoneColor
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
    }
}
