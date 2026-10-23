package com.example.soundboard.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.soundboard.AppContainer
import com.example.soundboard.ui.editor.EditorRoute
import com.example.soundboard.ui.editor.EditorViewModel
import com.example.soundboard.ui.launch.TITLE_CARD_MILLIS
import com.example.soundboard.ui.launch.TitleCard
import com.example.soundboard.ui.main.MainRoute
import com.example.soundboard.ui.main.MainViewModel
import com.example.soundboard.ui.onboarding.OnboardingScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"

    private const val ARG_BUTTON_ID = "buttonId"
    private const val ARG_TUTORIAL = "tutorial"
    const val EDITOR = "editor?$ARG_BUTTON_ID={$ARG_BUTTON_ID}&$ARG_TUTORIAL={$ARG_TUTORIAL}"

    /** Editor for a new button (null id) or an existing one. Button ids are UUIDs, safe in a route. */
    fun editor(buttonId: String? = null, tutorial: Boolean = false): String =
        if (buttonId == null) "editor?$ARG_TUTORIAL=$tutorial" else "editor?$ARG_BUTTON_ID=$buttonId&$ARG_TUTORIAL=$tutorial"

    val editorArguments = listOf(
        navArgument(ARG_BUTTON_ID) {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
        navArgument(ARG_TUTORIAL) {
            type = NavType.BoolType
            defaultValue = false
        },
    )

    const val ARG_BUTTON_ID_KEY = ARG_BUTTON_ID
    const val ARG_TUTORIAL_KEY = ARG_TUTORIAL
}

/**
 * Single-Activity navigation: Onboarding (welcome) -> ButtonEditor (tutorial) -> Main, or straight
 * to Main when buttons already exist. The first screen is picked once the database has answered.
 */
@Composable
fun SoundboardNavHost(container: AppContainer) {
    val startRoute by produceState<String?>(initialValue = null) {
        value = if (container.buttonRepository.count() == 0) Routes.ONBOARDING else Routes.MAIN
    }
    // Saveable so rotating or returning to the app doesn't replay the title card.
    var showTitleCard by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        if (showTitleCard) {
            delay(TITLE_CARD_MILLIS)
            showTitleCard = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        startRoute?.let { start -> SoundboardNavGraph(container, start) }
        // Stays up until the first screen is ready, then fades to reveal it.
        AnimatedVisibility(
            visible = showTitleCard || startRoute == null,
            enter = EnterTransition.None,
            exit = fadeOut(tween(durationMillis = 300)),
        ) {
            TitleCard(onSkip = { showTitleCard = false })
        }
    }
}

@Composable
private fun SoundboardNavGraph(container: AppContainer, start: String) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = start) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onCreateFirstButton = { navController.navigate(Routes.editor(tutorial = true)) })
        }
        composable(Routes.MAIN) {
            val viewModel: MainViewModel = viewModel(factory = MainViewModel.factory(container))
            MainRoute(
                viewModel = viewModel,
                onNewButton = { navController.navigate(Routes.editor()) },
                onEditButton = { id -> navController.navigate(Routes.editor(buttonId = id)) },
            )
        }
        composable(Routes.EDITOR, arguments = Routes.editorArguments) { entry ->
            val buttonId = entry.arguments?.getString(Routes.ARG_BUTTON_ID_KEY)
            val tutorial = entry.arguments?.getBoolean(Routes.ARG_TUTORIAL_KEY) ?: false
            val viewModel: EditorViewModel = viewModel(factory = EditorViewModel.factory(container, buttonId))
            EditorRoute(
                viewModel = viewModel,
                tutorial = tutorial,
                onFinished = { saved ->
                    if (saved && tutorial) {
                        // End of the tutorial: replace welcome + editor with the Main screen.
                        navController.navigate(Routes.MAIN) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onCancel = { navController.popBackStack() },
            )
        }
    }
}
