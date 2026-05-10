package com.nothing.news

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.nothing.news.ui.news.NewsScreen
import com.nothing.news.ui.search.SearchScreen
import com.nothing.news.ui.settings.SettingsScreen
import com.nothing.news.ui.feeds.MyFeedsScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: com.nothing.news.ui.news.NewsViewModel = hiltViewModel()
            val themePreference by viewModel.themePreference.collectAsState()
            
            val darkTheme = when (themePreference) {
                "Light" -> false
                "Dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            com.nothing.news.ui.theme.NothingNewsTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "news") {
                        composable("news") {
                            NewsScreen(
                                viewModel = viewModel,
                                onNavigateToSearch = { navController.navigate("search") },
                                onNavigateToSettings = { navController.navigate("settings") }
                            )
                        }
                        composable("search") {
                            SearchScreen(
                                onBack = { navController.popBackStack() },
                                onNavigateToMyFeeds = { navController.navigate("my_feeds") },
                                viewModel = viewModel
                            )
                        }
                        composable("my_feeds") {
                            MyFeedsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onNavigateToPermissions = { navController.navigate("permissions") },
                                onNavigateToSync = { navController.navigate("sync") }
                            )
                        }
                        composable("sync") {
                            com.nothing.news.ui.settings.SyncScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("permissions") {
                            com.nothing.news.ui.settings.PermissionsScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
