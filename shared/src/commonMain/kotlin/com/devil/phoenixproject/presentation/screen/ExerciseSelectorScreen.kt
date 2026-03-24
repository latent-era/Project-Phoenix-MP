package com.devil.phoenixproject.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.devil.phoenixproject.data.repository.ExerciseRepository
import com.devil.phoenixproject.domain.model.Exercise
import com.devil.phoenixproject.presentation.components.CreateExerciseDialog
import com.devil.phoenixproject.presentation.components.CustomExerciseSaveAction
import com.devil.phoenixproject.presentation.components.ExercisePickerContent
import com.devil.phoenixproject.presentation.components.getEquipmentDatabaseValues
import com.devil.phoenixproject.presentation.components.resolveCustomExerciseDeleteTarget
import com.devil.phoenixproject.presentation.components.resolveCustomExerciseSaveAction
import com.devil.phoenixproject.presentation.viewmodel.MainViewModel
import com.devil.phoenixproject.ui.theme.ThemeMode
import kotlinx.coroutines.launch

/**
 * Full-screen exercise selector — a real navigation destination that renders
 * inside the shared EnhancedMainScreen scaffold. No local Scaffold or TopAppBar;
 * the title is set via viewModel.updateTopBarTitle().
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseSelectorScreen(
    navController: NavController,
    viewModel: MainViewModel,
    exerciseRepository: ExerciseRepository,
    onExerciseSelected: (Exercise) -> Unit,
    enableVideoPlayback: Boolean = false,
    enableCustomExercises: Boolean = true,
    themeMode: ThemeMode = ThemeMode.DARK,
) {
    val coroutineScope = rememberCoroutineScope()

    // ---- State copied from ExercisePickerDialog ----
    var searchQuery by remember { mutableStateOf("") }
    var showFavoritesOnly by remember { mutableStateOf(false) }
    var showCustomOnly by remember { mutableStateOf(false) }
    var selectedMuscles by remember { mutableStateOf(setOf<String>()) }
    var selectedEquipment by remember { mutableStateOf(setOf<String>()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var exerciseToEdit by remember { mutableStateOf<Exercise?>(null) }

    val customExercises by exerciseRepository.getCustomExercises()
        .collectAsState(initial = emptyList())

    val allExercises by remember(searchQuery, showFavoritesOnly, showCustomOnly) {
        when {
            showCustomOnly -> exerciseRepository.getCustomExercises()
            showFavoritesOnly -> exerciseRepository.getFavorites()
            searchQuery.isNotBlank() -> exerciseRepository.searchExercises(searchQuery)
            else -> exerciseRepository.getAllExercises()
        }
    }.collectAsState(initial = emptyList())

    val exercises = remember(allExercises, selectedMuscles, selectedEquipment) {
        allExercises.filter { exercise ->
            val matchesMuscle = selectedMuscles.isEmpty() ||
                selectedMuscles.any { muscle ->
                    exercise.muscleGroups.contains(muscle, ignoreCase = true)
                }
            val matchesEquipment = selectedEquipment.isEmpty() ||
                selectedEquipment.any { equipment ->
                    val databaseValues = getEquipmentDatabaseValues(equipment)
                    val equipmentList =
                        exercise.equipment.uppercase().split(",").map { it.trim() }
                    databaseValues.any { dbValue ->
                        equipmentList.contains(dbValue.uppercase())
                    }
                }
            matchesMuscle && matchesEquipment
        }
    }

    fun clearAllFilters() {
        showFavoritesOnly = false
        showCustomOnly = false
        selectedMuscles = emptySet()
        selectedEquipment = emptySet()
    }

    // Set the shared scaffold title
    LaunchedEffect(Unit) {
        viewModel.updateTopBarTitle("Select Exercise")
    }

    LaunchedEffect(Unit) {
        exerciseRepository.importExercises()
    }

    // ---- Create / Edit Exercise Dialog ----
    if (showCreateDialog || exerciseToEdit != null) {
        CreateExerciseDialog(
            existingExercise = exerciseToEdit,
            onSave = { exercise ->
                val editExerciseId = exerciseToEdit?.id
                showCreateDialog = false
                exerciseToEdit = null
                val action = resolveCustomExerciseSaveAction(
                    draftExercise = exercise,
                    editingExerciseId = editExerciseId,
                )
                coroutineScope.launch {
                    when (action) {
                        is CustomExerciseSaveAction.Create -> {
                            exerciseRepository.createCustomExercise(action.exercise)
                        }
                        is CustomExerciseSaveAction.Update -> {
                            exerciseRepository.updateCustomExercise(action.exercise)
                        }
                    }
                }
            },
            onDelete = if (exerciseToEdit != null) {
                {
                    val deleteExerciseId = exerciseToEdit?.id
                    showCreateDialog = false
                    exerciseToEdit = null
                    val targetId = resolveCustomExerciseDeleteTarget(deleteExerciseId)
                    coroutineScope.launch {
                        targetId?.let { exerciseRepository.deleteCustomExercise(it) }
                    }
                }
            } else null,
            onDismiss = {
                showCreateDialog = false
                exerciseToEdit = null
            },
            themeMode = themeMode,
        )
    }

    // ---- Content renders inside the shared EnhancedMainScreen scaffold ----
    Box(modifier = Modifier.fillMaxSize()) {
        ExercisePickerContent(
            exercises = exercises,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            showFavoritesOnly = showFavoritesOnly,
            onToggleFavorites = {
                showFavoritesOnly = !showFavoritesOnly
                if (showFavoritesOnly) showCustomOnly = false
            },
            showCustomOnly = showCustomOnly,
            onToggleCustom = {
                showCustomOnly = !showCustomOnly
                if (showCustomOnly) showFavoritesOnly = false
            },
            customExerciseCount = customExercises.size,
            selectedMuscles = selectedMuscles,
            onToggleMuscle = { muscle ->
                selectedMuscles = if (muscle in selectedMuscles) {
                    selectedMuscles - muscle
                } else {
                    selectedMuscles + muscle
                }
            },
            selectedEquipment = selectedEquipment,
            onToggleEquipment = { equipment ->
                selectedEquipment = if (equipment in selectedEquipment) {
                    selectedEquipment - equipment
                } else {
                    selectedEquipment + equipment
                }
            },
            onClearAllFilters = { clearAllFilters() },
            onExerciseSelected = { exercise ->
                onExerciseSelected(exercise)
            },
            onToggleFavorite = { exercise ->
                exercise.id?.let {
                    coroutineScope.launch {
                        exerciseRepository.toggleFavorite(it)
                    }
                }
            },
            exerciseRepository = exerciseRepository,
            enableVideoPlayback = enableVideoPlayback,
            enableCustomExercises = enableCustomExercises,
            onCreateExercise = { showCreateDialog = true },
            onEditExercise = { exercise -> exerciseToEdit = exercise },
            fullScreen = true,
        )
    }
}
