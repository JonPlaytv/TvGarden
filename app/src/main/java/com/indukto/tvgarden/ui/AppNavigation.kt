package com.indukto.tvgarden.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel()
    val channels by viewModel.channels.collectAsState()
    val selectedChannel by viewModel.selectedChannel.collectAsState()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                channels = channels,
                onChannelClick = { channel ->
                    viewModel.selectChannel(channel)
                    navController.navigate("player")
                }
            )
        }
        composable("player") {
            // Using let for safety
            selectedChannel?.let { channel ->
                PlayerScreen(
                    streamUrl = channel.streamUrl,
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
