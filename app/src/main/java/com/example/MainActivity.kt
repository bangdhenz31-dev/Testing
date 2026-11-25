package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.audio.DawAudioEngine
import com.example.data.DawRepository
import com.example.data.ThemePreferences
import com.example.data.ThemeSettings
import com.example.ui.AuthScreen
import com.example.ui.DawViewModel
import com.example.ui.FirebaseConfigMissingScreen
import com.example.ui.components.ThemeSelectorDialog
import com.example.ui.screens.CloudExportScreen
import com.example.ui.screens.DawTransportHeader
import com.example.ui.screens.EqualizerPluginScreen
import com.example.ui.screens.MixerScreen
import com.example.ui.screens.SpatialRadarScreen
import com.example.ui.screens.YouTubeDeckScreen
import com.example.ui.signOutUser
import com.example.ui.theme.DawThemePreset
import com.example.ui.theme.SpatialDawTheme
import com.example.ui.theme.StudioTheme
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

private enum class StudioDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    MIXER("mixer", R.string.tab_mixer, Icons.Filled.Tune),
    SPATIAL("spatial", R.string.tab_spatial, Icons.Filled.Radar),
    EQUALIZER("equalizer", R.string.tab_eq, Icons.Filled.Equalizer),
    YOUTUBE("youtube", R.string.tab_youtube, Icons.Filled.PlayCircle),
    COLLAB("collab", R.string.tab_collab, Icons.Filled.Cloud),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpatialDawRoot()
        }
    }
}

@Composable
private fun SpatialDawRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themePreferences = remember(context) { ThemePreferences(context.applicationContext) }
    val themeSettings by themePreferences.settingsFlow.collectAsStateWithLifecycle(
        initialValue = ThemeSettings()
    )

    SpatialDawTheme(
        themePreset = themeSettings.preset,
        useDynamicColor = themeSettings.useDynamicColor,
    ) {
        AppNavigation(
            themeSettings = themeSettings,
            onSelectPreset = { preset ->
                scope.launch { themePreferences.setThemePreset(preset) }
            },
            onToggleDynamicColor = { enabled ->
                scope.launch { themePreferences.setDynamicColor(enabled) }
            },
        )
    }
}

@Composable
fun AppNavigation(
    themeSettings: ThemeSettings,
    onSelectPreset: (DawThemePreset) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val isFirebaseConfigured = remember(context) {
        FirebaseApp.getApps(context).isNotEmpty()
    }
    if (!isFirebaseConfigured) {
        FirebaseConfigMissingScreen()
        return
    }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
    } else {
        key(user.uid) {
            StudioMainScreen(
                currentUserId = user.uid,
                themeSettings = themeSettings,
                onSelectPreset = onSelectPreset,
                onToggleDynamicColor = onToggleDynamicColor,
            )
        }
    }
}

@Composable
fun StudioMainScreen(
    currentUserId: String,
    themeSettings: ThemeSettings,
    onSelectPreset: (DawThemePreset) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val colors = StudioTheme.colors
    val selectedIconContrast = if (colors.neon.luminance() > 0.45f) Color.Black else Color.White

    var showThemeDialog by remember { mutableStateOf(false) }

    val viewModel: DawViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                DawViewModel(
                    repository = DawRepository(db),
                    audioEngine = DawAudioEngine(),
                    currentUserId = currentUserId,
                )
            }
        }
    )

    val studioState by viewModel.studioState.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val projectsState by viewModel.projectsState.collectAsStateWithLifecycle()
    val notesState by viewModel.notesState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: StudioDestination.MIXER.route

    LaunchedEffect(studioState.statusMessage) {
        val msg = studioState.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    if (showThemeDialog) {
        ThemeSelectorDialog(
            themeSettings = themeSettings,
            onSelectPreset = onSelectPreset,
            onToggleDynamicColor = onToggleDynamicColor,
            onDismiss = { showThemeDialog = false },
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = colors.bg,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(containerColor = colors.surface) {
                        StudioDestination.entries.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = dest.icon,
                                        contentDescription = stringResource(dest.labelRes),
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(dest.labelRes),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                modifier = Modifier.testTag("nav_${dest.route}"),
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = selectedIconContrast,
                                    selectedTextColor = colors.neon,
                                    indicatorColor = colors.neon,
                                    unselectedIconColor = colors.textMuted,
                                    unselectedTextColor = colors.textMuted,
                                ),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = colors.surface,
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        StudioDestination.entries.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = dest.icon,
                                        contentDescription = stringResource(dest.labelRes),
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag("nav_${dest.route}"),
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = selectedIconContrast,
                                    selectedTextColor = colors.neon,
                                    indicatorColor = colors.neon,
                                    unselectedIconColor = colors.textMuted,
                                    unselectedTextColor = colors.textMuted,
                                ),
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    DawTransportHeader(
                        projectTitle = studioState.activeProject.title,
                        bpm = studioState.activeProject.bpm,
                        latencyMs = telemetry.estimatedLatencyMs,
                        latencyFrames = studioState.activeProject.latencyFrames,
                        isPlaying = telemetry.isPlaying,
                        isSyncingCloud = studioState.isSyncingCloud,
                        onTogglePlay = viewModel::togglePlayback,
                        onBpmDelta = { delta ->
                            viewModel.updateActiveProject {
                                it.copy(bpm = (it.bpm + delta).coerceIn(40L, 240L))
                            }
                        },
                        onOpenThemePicker = { showThemeDialog = true },
                        onSaveCloud = viewModel::saveActiveProjectToCloud,
                        onSignOut = {
                            viewModel.audioEngine.stopPlayback()
                            signOutUser(credentialManager, scope)
                        },
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        NavHost(
                            navController = navController,
                            startDestination = StudioDestination.MIXER.route,
                        ) {
                            composable(StudioDestination.MIXER.route) {
                                MixerScreen(
                                    uiState = studioState,
                                    telemetry = telemetry,
                                    viewModel = viewModel,
                                )
                            }
                            composable(StudioDestination.SPATIAL.route) {
                                SpatialRadarScreen(
                                    uiState = studioState,
                                    telemetry = telemetry,
                                    viewModel = viewModel,
                                )
                            }
                            composable(StudioDestination.EQUALIZER.route) {
                                EqualizerPluginScreen(
                                    uiState = studioState,
                                    telemetry = telemetry,
                                    viewModel = viewModel,
                                )
                            }
                            composable(StudioDestination.YOUTUBE.route) {
                                YouTubeDeckScreen(
                                    uiState = studioState,
                                    telemetry = telemetry,
                                    viewModel = viewModel,
                                )
                            }
                            composable(StudioDestination.COLLAB.route) {
                                CloudExportScreen(
                                    currentUserId = currentUserId,
                                    uiState = studioState,
                                    projectsState = projectsState,
                                    notesState = notesState,
                                    viewModel = viewModel,
                                    themeSettings = themeSettings,
                                    onSelectPreset = onSelectPreset,
                                    onToggleDynamicColor = onToggleDynamicColor,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
