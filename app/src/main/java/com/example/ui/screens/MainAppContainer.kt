package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.LanguageViewModel

@Composable
fun MainAppContainer(
    viewModel: LanguageViewModel,
    modifier: Modifier = Modifier
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val activeLang by viewModel.activeLanguage.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar(
                windowInsets = WindowInsets.navigationBars,
                tonalElevation = 8.dp
            ) {
                // Tab Chat
                NavigationBarItem(
                    selected = currentScreen == AppScreen.CHAT,
                    onClick = {
                        if (currentScreen == AppScreen.CHAT) {
                            viewModel.closeChatDetail()
                        } else {
                            viewModel.setScreen(AppScreen.CHAT)
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = if (currentScreen == AppScreen.CHAT) Icons.Default.ChatBubble
                            else Icons.Default.ChatBubbleOutline,
                            contentDescription = "Chat"
                        )
                    },
                    label = { Text("Chat") }
                )

                // Tab Exercises
                NavigationBarItem(
                    selected = currentScreen == AppScreen.EXERCISES,
                    onClick = { viewModel.setScreen(AppScreen.EXERCISES) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Assignment,
                            contentDescription = "Exercices"
                        )
                    },
                    label = { Text("Exercices") }
                )

                // Tab Languages
                NavigationBarItem(
                    selected = currentScreen == AppScreen.LANGUAGES,
                    onClick = { viewModel.setScreen(AppScreen.LANGUAGES) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = "Langues"
                        )
                    },
                    label = { Text("Langues") }
                )

                // Tab Settings
                NavigationBarItem(
                    selected = currentScreen == AppScreen.SETTINGS,
                    onClick = { viewModel.setScreen(AppScreen.SETTINGS) },
                    icon = {
                        Icon(
                            imageVector = if (currentScreen == AppScreen.SETTINGS) Icons.Default.Settings
                            else Icons.Default.Settings,
                            contentDescription = "Paramètres"
                        )
                    },
                    label = { Text("Paramètres") }
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Elegant Animated Switcher for Tab Screens
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                },
                label = "ScreenTransition"
            ) { targetScreen ->
                when (targetScreen) {
                    AppScreen.CHAT -> ChatScreen(viewModel = viewModel)
                    AppScreen.EXERCISES -> ExercisesScreen(viewModel = viewModel)
                    AppScreen.LANGUAGES -> LanguagesScreen(viewModel = viewModel)
                    AppScreen.SETTINGS -> SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }
}
