package com.devil.phoenixproject.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devil.phoenixproject.data.repository.ExerciseRepository
import com.devil.phoenixproject.domain.model.PersonalRecord
import com.devil.phoenixproject.domain.model.WeightUnit
import com.devil.phoenixproject.domain.model.WorkoutSession
import com.devil.phoenixproject.ui.theme.Spacing
import com.devil.phoenixproject.presentation.components.*
import com.devil.phoenixproject.presentation.util.ResponsiveDimensions

/**
 * Wrapper composable that constrains card width on tablets to prevent over-stretching.
 */
@Composable
private fun ResponsiveCardWrapper(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val maxWidth = ResponsiveDimensions.cardMaxWidth()

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = if (maxWidth != null) {
                Modifier.widthIn(max = maxWidth).fillMaxWidth()
            } else {
                Modifier.fillMaxWidth()
            }
        ) {
            content()
        }
    }
}

/**
 * Improved Insights Tab - Clear, actionable analytics with proper formatting
 */
@Composable
fun InsightsTab(
    prs: List<PersonalRecord>,
    workoutSessions: List<WorkoutSession>,
    exerciseRepository: ExerciseRepository,
    modifier: Modifier = Modifier,
    weightUnit: WeightUnit = WeightUnit.KG
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
        item {
            Text(
                text = "DASHBOARD",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Your training overview",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 1. This Week Summary Card - week-over-week comparison
        item {
            ResponsiveCardWrapper {
                ThisWeekSummaryCard(
                    workoutSessions = workoutSessions,
                    personalRecords = prs,
                    weightUnit = weightUnit,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 2. Progressive Overload Card - heaviest weight per exercise over time
        if (workoutSessions.any { it.exerciseId != null }) {
            item {
                ResponsiveCardWrapper {
                    ProgressiveOverloadCard(
                        workoutSessions = workoutSessions,
                        weightUnit = weightUnit,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 3. Workout Frequency Card - sessions per week bar chart
        if (workoutSessions.isNotEmpty()) {
            item {
                ResponsiveCardWrapper {
                    WorkoutFrequencyCard(
                        workoutSessions = workoutSessions,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 4. Volume By Exercise Card - top exercises by total volume
        if (workoutSessions.any { it.exerciseId != null }) {
            item {
                ResponsiveCardWrapper {
                    VolumeByExerciseCard(
                        workoutSessions = workoutSessions,
                        weightUnit = weightUnit,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 5. Muscle Volume Card - per-muscle-group weekly set counts
        if (workoutSessions.any { it.exerciseId != null }) {
            item {
                ResponsiveCardWrapper {
                    MuscleVolumeCard(
                        workoutSessions = workoutSessions,
                        exerciseRepository = exerciseRepository,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Empty state
        if (prs.isEmpty() && workoutSessions.isEmpty()) {
            item {
                ResponsiveCardWrapper {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Insights,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No Insights Yet",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Complete workouts to unlock insights",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
