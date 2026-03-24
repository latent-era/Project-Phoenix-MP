package com.devil.phoenixproject.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.devil.phoenixproject.data.repository.TrainingCycleRepository
import com.devil.phoenixproject.domain.model.CycleProgress
import com.devil.phoenixproject.domain.model.Routine
import com.devil.phoenixproject.domain.model.TrainingCycle
import com.devil.phoenixproject.domain.model.WeightUnit
import com.devil.phoenixproject.domain.model.WorkoutSession
import com.devil.phoenixproject.presentation.components.ConnectionErrorDialog
import com.devil.phoenixproject.presentation.navigation.NavigationRoutes
import com.devil.phoenixproject.presentation.viewmodel.MainViewModel
import com.devil.phoenixproject.ui.theme.*
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import kotlin.time.Clock

/**
 * Home Screen — Talos Fit Dashboard
 * Clean, tiered layout matching Health Studio design language.
 */
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: MainViewModel,
    themeMode: ThemeMode,
    isLandscape: Boolean = false
) {
    val connectionError by viewModel.connectionError.collectAsState()
    val routines by viewModel.routines.collectAsState()
    val workoutStreak by viewModel.workoutStreak.collectAsState()
    val recentSessions by viewModel.allWorkoutSessions.collectAsState()
    val weightUnit by viewModel.weightUnit.collectAsState()

    val cycleRepository: TrainingCycleRepository = koinInject()
    val activeCycle by cycleRepository.getActiveCycle().collectAsState(initial = null)
    var cycleProgress by remember { mutableStateOf<CycleProgress?>(null) }

    LaunchedEffect(activeCycle) {
        activeCycle?.let { cycle ->
            cycleProgress = cycleRepository.getCycleProgress(cycle.id)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.updateTopBarTitle("")
    }

    Scaffold(containerColor = Color.Transparent) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 1. Weekly Activity Strip
                item(key = "weekly-strip") {
                    WeeklyActivityStrip(
                        history = recentSessions,
                        workoutStreak = workoutStreak
                    )
                }

                // 2. Quick Stats (2×2 metric cards)
                item(key = "quick-stats") {
                    Text(
                        text = "Quick Stats",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    val weekSessions = recentSessions.filter { session ->
                        val sessionTime = Instant.fromEpochMilliseconds(session.timestamp)
                        val now = Clock.System.now()
                        (now - sessionTime).inWholeDays < 7
                    }
                    val weeklyVolume = weekSessions.sumOf { (it.totalVolumeKg ?: 0f).toDouble() }.toInt()
                    val lastExercise = recentSessions.firstOrNull()?.exerciseName ?: "None"

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickStatCard(
                            icon = Icons.Default.LocalFireDepartment,
                            iconColor = MetricForce,
                            label = "Streak",
                            value = "${workoutStreak ?: 0}",
                            unit = "days",
                            modifier = Modifier.weight(1f)
                        )
                        QuickStatCard(
                            icon = Icons.Default.FitnessCenter,
                            iconColor = MetricPower,
                            label = "Weekly Volume",
                            value = if (weightUnit == WeightUnit.LB) "${(weeklyVolume * 2.20462).toInt()}" else "$weeklyVolume",
                            unit = if (weightUnit == WeightUnit.LB) "lbs" else "kg",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickStatCard(
                            icon = Icons.Default.CalendarMonth,
                            iconColor = MetricVelocity,
                            label = "Sessions",
                            value = "${weekSessions.size}",
                            unit = "this week",
                            modifier = Modifier.weight(1f)
                        )
                        QuickStatCard(
                            icon = Icons.Outlined.FitnessCenter,
                            iconColor = MetricSleep,
                            label = "Last Workout",
                            value = lastExercise.take(12),
                            unit = null,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 3. Workout Modes (2×2 action grid — replaces floating FABs)
                item(key = "workout-modes") {
                    Text(
                        text = "Workout Modes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WorkoutModeCard(
                            icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                            iconColor = MetricForce,
                            label = "Routines",
                            onClick = { navController.navigate(NavigationRoutes.DailyRoutines.route) },
                            modifier = Modifier.weight(1f)
                        )
                        WorkoutModeCard(
                            icon = Icons.Default.Whatshot,
                            iconColor = MetricPower,
                            label = "Just Lift",
                            onClick = { navController.navigate(NavigationRoutes.JustLift.route) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WorkoutModeCard(
                            icon = Icons.Default.Loop,
                            iconColor = MetricVelocity,
                            label = "Cycles",
                            onClick = { navController.navigate(NavigationRoutes.TrainingCycles.route) },
                            modifier = Modifier.weight(1f)
                        )
                        WorkoutModeCard(
                            icon = Icons.Outlined.FitnessCenter,
                            iconColor = MetricHRV,
                            label = "Single Exercise",
                            onClick = { navController.navigate(NavigationRoutes.SingleExercise.route) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 4. Recent Workouts
                item(key = "recent-workouts") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Workouts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (recentSessions.size > 3) {
                            Text(
                                text = "View All →",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    navController.navigate(NavigationRoutes.Analytics.route)
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    RecentWorkoutsList(recentSessions.take(3), weightUnit)
                }

                // 5. Active Cycle (conditional)
                if (activeCycle != null) {
                    item(key = "active-cycle") {
                        Text(
                            text = "Active Cycle",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        ActiveCycleCard(
                            cycle = activeCycle!!,
                            progress = cycleProgress,
                            routines = routines,
                            onTap = { navController.navigate(NavigationRoutes.TrainingCycles.route) }
                        )
                    }
                }

                // Bottom padding
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            connectionError?.let { error ->
                ConnectionErrorDialog(
                    message = error,
                    onDismiss = { viewModel.clearConnectionError() }
                )
            }
        }
    }
}

// ============================================================================
// WEEKLY ACTIVITY STRIP
// ============================================================================

@Composable
private fun WeeklyActivityStrip(
    history: List<WorkoutSession>,
    workoutStreak: Int?
) {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val mondayOffset = today.dayOfWeek.ordinal
    val mondayEpochDays = today.toEpochDays() - mondayOffset
    val weekDays = (0..6).map { LocalDate.fromEpochDays(mondayEpochDays + it) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Days
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                weekDays.forEach { date ->
                    val hasWorkout = history.any { session ->
                        val sessionDate = Instant.fromEpochMilliseconds(session.timestamp)
                            .toLocalDateTime(TimeZone.currentSystemDefault()).date
                        sessionDate == date
                    }
                    val isToday = date == today

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = date.dayOfWeek.name.take(1),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isToday) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        hasWorkout -> MaterialTheme.colorScheme.primary
                                        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                        else -> MaterialTheme.colorScheme.outlineVariant
                                    }
                                )
                        )
                    }
                }
            }

            // Streak badge
            if (workoutStreak != null && workoutStreak > 0) {
                Spacer(Modifier.width(16.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "$workoutStreak",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// QUICK STAT CARD (metric card pattern)
// ============================================================================

@Composable
private fun QuickStatCard(
    icon: ImageVector,
    iconColor: Color,
    label: String,
    value: String,
    unit: String?,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TalosIconBadge(icon = icon, color = iconColor, size = 32.dp, iconSize = 16.dp)
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TalosTextTertiary,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (unit != null) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = TalosTextSecondary
                )
            }
        }
    }
}

// ============================================================================
// WORKOUT MODE CARD (quick action pattern)
// ============================================================================

@Composable
private fun WorkoutModeCard(
    icon: ImageVector,
    iconColor: Color,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TalosIconBadge(icon = icon, color = iconColor, size = 40.dp, iconSize = 20.dp)
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ============================================================================
// RECENT WORKOUTS LIST
// ============================================================================

@Composable
private fun RecentWorkoutsList(sessions: List<WorkoutSession>, weightUnit: WeightUnit) {
    if (sessions.isEmpty()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Complete a workout and it will appear here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp)
            )
        }
    } else {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column {
                sessions.forEachIndexed { index, session ->
                    WorkoutListItem(session, weightUnit)
                    if (index < sessions.lastIndex) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutListItem(session: WorkoutSession, weightUnit: WeightUnit) {
    val displayWeight = if (weightUnit == WeightUnit.LB)
        (session.weightPerCableKg * 2.20462f).toInt() else session.weightPerCableKg.toInt()
    val unitLabel = if (weightUnit == WeightUnit.LB) "lbs" else "kg"

    Column(modifier = Modifier.padding(14.dp)) {
        // Top row: icon + name + duration badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TalosIconBadge(
                icon = Icons.Outlined.FitnessCenter,
                color = MetricForce,
                size = 36.dp,
                iconSize = 18.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.exerciseName ?: "Workout",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${session.workingReps} reps • $displayWeight $unitLabel/cable",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Duration badge
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Text(
                    text = "${(session.duration / 60000).coerceAtLeast(1)} min",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// ============================================================================
// ACTIVE CYCLE CARD
// ============================================================================

@Composable
private fun ActiveCycleCard(
    cycle: TrainingCycle,
    progress: CycleProgress?,
    routines: List<Routine>,
    onTap: () -> Unit
) {
    val currentDayNum = progress?.currentDayNumber ?: 1
    val cycleDay = cycle.days.find { it.dayNumber == currentDayNum }
    val routine = cycleDay?.routineId?.let { id -> routines.find { it.id == id } }
    val isRest = cycleDay?.isRestDay == true || cycleDay?.routineId == null

    Surface(
        onClick = onTap,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (isRest) "REST DAY" else "UP NEXT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = cycleDay?.name ?: "Day $currentDayNum",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = routine?.name ?: if (isRest) "Take it easy today" else cycle.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            // Progress bar
            if (cycle.days.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                val progressValue = currentDayNum.toFloat() / cycle.days.size.toFloat()
                LinearProgressIndicator(
                    progress = { progressValue },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                    drawStopIndicator = {}
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Day $currentDayNum of ${cycle.days.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TalosTextTertiary
                )
            }
        }
    }
}
