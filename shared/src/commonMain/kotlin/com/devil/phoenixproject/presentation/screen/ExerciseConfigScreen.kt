package com.devil.phoenixproject.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.devil.phoenixproject.data.repository.ExerciseRepository
import com.devil.phoenixproject.data.repository.ExerciseVideoEntity
import com.devil.phoenixproject.data.repository.PersonalRecordRepository
import com.devil.phoenixproject.domain.model.*
import com.devil.phoenixproject.presentation.components.VideoPlayer
import com.devil.phoenixproject.presentation.navigation.NavigationRoutes
import com.devil.phoenixproject.presentation.viewmodel.ExerciseConfigViewModel
import com.devil.phoenixproject.presentation.viewmodel.ExerciseType
import com.devil.phoenixproject.presentation.viewmodel.SetMode
import com.devil.phoenixproject.presentation.viewmodel.MainViewModel
import com.devil.phoenixproject.ui.theme.Spacing
import org.koin.compose.koinInject

/**
 * Full-screen exercise configuration screen — replaces the old
 * ExerciseEditBottomSheet. Renders inside the shared EnhancedMainScreen
 * scaffold with "Configure Exercise" as the top bar title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseConfigScreen(
    exercise: RoutineExercise,
    navController: NavController,
    viewModel: MainViewModel,
    weightUnit: WeightUnit,
    enableVideoPlayback: Boolean,
    kgToDisplay: (Float, WeightUnit) -> Float,
    displayToKg: (Float, WeightUnit) -> Float,
    exerciseRepository: ExerciseRepository,
    formatWeight: (Float, WeightUnit) -> String,
    onSave: (RoutineExercise) -> Unit,
    onCancel: () -> Unit,
    buttonText: String = "Add to Routine",
) {
    val personalRecordRepository: PersonalRecordRepository = koinInject()

    // Create local ViewModel instance for exercise configuration state
    val configViewModel = remember { ExerciseConfigViewModel(personalRecordRepository) }

    // Set the shared scaffold title
    LaunchedEffect(Unit) {
        viewModel.updateTopBarTitle("Configure Exercise")
    }

    // Fetch videos for exercise
    var videos by remember { mutableStateOf<List<ExerciseVideoEntity>>(emptyList()) }
    LaunchedEffect(exercise.exercise.id) {
        exercise.exercise.id?.let { exerciseId ->
            try {
                videos = exerciseRepository.getVideos(exerciseId)
            } catch (_: Exception) {
                // Handle error - videos will remain empty
            }
        }
    }
    val preferredVideo = videos.firstOrNull { it.angle == "FRONT" } ?: videos.firstOrNull()

    // Initialize the ViewModel
    LaunchedEffect(exercise, weightUnit) {
        configViewModel.initialize(exercise, weightUnit, kgToDisplay, displayToKg)
    }

    // Collect state from the ViewModel
    val exerciseType by configViewModel.exerciseType.collectAsState()
    val setMode by configViewModel.setMode.collectAsState()
    val sets by configViewModel.sets.collectAsState()
    val selectedMode by configViewModel.selectedMode.collectAsState()
    val weightChange by configViewModel.weightChange.collectAsState()
    val rest by configViewModel.rest.collectAsState()
    val perSetRestTime by configViewModel.perSetRestTime.collectAsState()
    val eccentricLoad by configViewModel.eccentricLoad.collectAsState()
    val echoLevel by configViewModel.echoLevel.collectAsState()
    val stallDetectionEnabled by configViewModel.stallDetectionEnabled.collectAsState()
    val repCountTiming by configViewModel.repCountTiming.collectAsState()
    val stopAtTop by configViewModel.stopAtTop.collectAsState()
    val currentExercisePR by configViewModel.currentExercisePR.collectAsState()
    val usePercentOfPR by configViewModel.usePercentOfPR.collectAsState()
    val weightPercentOfPR by configViewModel.weightPercentOfPR.collectAsState()

    val weightSuffix = if (weightUnit == WeightUnit.LB) "lbs" else "kg"
    val maxWeight = if (weightUnit == WeightUnit.LB) 242f else 110f
    val weightStep = if (weightUnit == WeightUnit.LB) 0.5f else 0.25f
    val maxWeightChange = 10

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.small)
    ) {
        // Header: exercise name
        Column {
            Text(
                exercise.exercise.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(Spacing.small))

        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            // Video Player
            if (enableVideoPlayback) {
                preferredVideo?.let { video ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        VideoPlayer(
                            videoUrl = video.videoUrl,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Personal Record Display
            currentExercisePR?.let { pr ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.medium),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = "Personal Record",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    "Personal Record",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    "${formatWeight(pr.weightPerCableKg, weightUnit)}/cable x ${pr.reps} reps",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // Weight Configuration Section (PR Percentage Scaling)
            if (exerciseType == ExerciseType.STANDARD) {
                WeightConfigurationCard(
                    usePercentOfPR = usePercentOfPR,
                    weightPercentOfPR = weightPercentOfPR,
                    currentExercisePR = currentExercisePR,
                    weightUnit = weightUnit,
                    formatWeight = formatWeight,
                    onUsePercentOfPRChange = configViewModel::onUsePercentOfPRChange,
                    onWeightPercentOfPRChange = configViewModel::onWeightPercentOfPRChange
                )
            }

            // Mode Selector
            if (exerciseType == ExerciseType.STANDARD) {
                ModeSelector(
                    selectedMode = selectedMode,
                    onModeChange = configViewModel::onSelectedModeChange
                )
            }

            // TUT Beast toggle
            val isTutMode = selectedMode is WorkoutMode.TUT || selectedMode is WorkoutMode.TUTBeast
            if (isTutMode) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.small),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Beast Mode",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Switch(
                            checked = selectedMode is WorkoutMode.TUTBeast,
                            onCheckedChange = { isBeast ->
                                configViewModel.onSelectedModeChange(if (isBeast) WorkoutMode.TUTBeast else WorkoutMode.TUT)
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary, checkedThumbColor = Color.White, uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant, uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
            }

            // Echo Mode options
            val isEchoMode = selectedMode is WorkoutMode.Echo
            if (isEchoMode) {
                EccentricLoadSelector(
                    eccentricLoad = eccentricLoad,
                    onLoadChange = configViewModel::onEccentricLoadChange
                )
                EchoLevelSelector(
                    level = echoLevel,
                    onLevelChange = configViewModel::onEchoLevelChange
                )
            }

            // Weight Change Per Rep
            if (exerciseType == ExerciseType.STANDARD && !isEchoMode) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.medium)
                    ) {
                        Text(
                            "Weight Change Per Rep",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(Spacing.medium))

                        com.devil.phoenixproject.presentation.components.ProgressionSlider(
                            value = weightChange.toFloat(),
                            onValueChange = { configViewModel.onWeightChange(it.toInt()) },
                            valueRange = -maxWeightChange.toFloat()..maxWeightChange.toFloat(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Negative = Regression, Positive = Progression",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.small)
                        )
                    }
                }
            }

            // Set Mode Toggle
            SetModeToggle(
                setMode = setMode,
                onModeChange = configViewModel::onSetModeChange
            )

            // Per Set Rest Time toggle
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.small),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Per Set Rest Time",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (perSetRestTime) FontWeight.Bold else FontWeight.Normal,
                        color = if (perSetRestTime) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = perSetRestTime,
                        onCheckedChange = configViewModel::onPerSetRestTimeChange,
                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary, checkedThumbColor = Color.White, uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant, uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }

            // Stall Detection toggle
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.small),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Stall Detection",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (stallDetectionEnabled) FontWeight.Bold else FontWeight.Normal,
                            color = if (stallDetectionEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Auto-stop set when movement pauses for 5 seconds",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = stallDetectionEnabled,
                        onCheckedChange = configViewModel::onStallDetectionEnabledChange,
                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary, checkedThumbColor = Color.White, uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant, uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }

            // Rep Count Timing toggle
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.small),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Rep Count Timing",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (repCountTiming == RepCountTiming.TOP) FontWeight.Bold else FontWeight.Normal,
                            color = if (repCountTiming == RepCountTiming.TOP) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (repCountTiming == RepCountTiming.TOP)
                                "Count at top of lift (concentric peak)"
                            else
                                "Count at bottom (eccentric valley)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = repCountTiming == RepCountTiming.TOP,
                        onCheckedChange = { isTop ->
                            configViewModel.onRepCountTimingChange(
                                if (isTop) RepCountTiming.TOP else RepCountTiming.BOTTOM
                            )
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary, checkedThumbColor = Color.White, uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant, uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }

            // Stop at Top toggle -- hidden for fully AMRAP exercises
            if (!sets.all { it.reps == null }) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.small),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Stop at Top",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (stopAtTop) FontWeight.Bold else FontWeight.Normal,
                                color = if (stopAtTop) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (stopAtTop)
                                    "Final rep stops at contracted position (top of lift)"
                                else
                                    "Final rep stops at extended position (bottom)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = stopAtTop,
                            onCheckedChange = configViewModel::onStopAtTopChange,
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary, checkedThumbColor = Color.White, uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant, uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
            }

            // Sets Configuration
            SetsConfiguration(
                sets = sets,
                setMode = setMode,
                exerciseType = exerciseType,
                weightSuffix = weightSuffix,
                maxWeight = maxWeight,
                weightStep = weightStep,
                isEchoMode = isEchoMode,
                perSetRestTime = perSetRestTime,
                onRepsChange = configViewModel::updateReps,
                onWeightChange = configViewModel::updateWeight,
                onDurationChange = configViewModel::updateDuration,
                onRestChange = configViewModel::updateRestTime,
                onAddSet = configViewModel::addSet,
                onDeleteSet = configViewModel::deleteSet
            )

            // Single rest time picker
            if (!perSetRestTime) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 0.dp
                ) {
                    Column(modifier = Modifier.padding(Spacing.small)) {
                        Text(
                            "Rest Time: ${rest}s",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = Spacing.extraSmall)
                        )
                        Slider(
                            value = rest.toFloat(),
                            onValueChange = { configViewModel.onRestChange(it.toInt()) },
                            valueRange = 0f..300f,
                            steps = 59,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.small))

        // Bottom actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            OutlinedButton(
                onClick = {
                    configViewModel.onDismiss()
                    onCancel()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Button(
                onClick = { configViewModel.onSave(onSave) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                enabled = sets.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp
                )
            ) {
                Text(
                    buttonText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
