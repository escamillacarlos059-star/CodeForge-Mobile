package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.IdeDestination
import com.example.ui.editor.CodeEditorScreen
import com.example.ui.editor.ConsoleDrawer
import com.example.ui.editor.FileExplorerPanel
import com.example.ui.github.GitHubBuildScreen
import com.example.ui.preview.LivePreviewScreen
import com.example.ui.projects.ProjectsScreen
import com.example.ui.theme.CodeForgeTheme
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CodeForgeTheme {
                CodeForgeApp()
            }
        }
    }
}

@Composable
fun CodeForgeApp(viewModel: IdeViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.toastMessage, state.errorMessage) {
        val msg = state.errorMessage ?: state.toastMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearMessages()
    }

    if (state.currentDestination != IdeDestination.EDITOR) {
        BackHandler {
            viewModel.navigateTo(IdeDestination.EDITOR)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedLayout = maxWidth >= 840.dp

        if (isExpandedLayout) {
            Scaffold(
                containerColor = ObsidianBg,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    NavigationRail(containerColor = ObsidianSurface) {
                        IdeDestination.entries.forEach { dest ->
                            val selected = state.currentDestination == dest
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.navigateTo(dest) },
                                icon = { Icon(destinationIcon(dest, selected), contentDescription = dest.label) },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("nav_${dest.name.lowercase()}")
                            )
                        }
                    }

                    if (state.currentDestination == IdeDestination.EDITOR) {
                        FileExplorerPanel(
                            project = state.activeProject,
                            fileTree = state.fileTree,
                            expandedFolders = state.expandedFolders,
                            activeFilePath = state.activeTabPath,
                            onToggleFolder = viewModel::toggleFolderExpanded,
                            onOpenFile = viewModel::openFile,
                            onCreateFile = viewModel::createNewFile,
                            onCreateFolder = viewModel::createNewFolder,
                            onRenameNode = viewModel::renameNode,
                            onDeleteNode = viewModel::deleteNode,
                            modifier = Modifier.width(260.dp)
                        )
                        VerticalDivider(color = ObsidianBorder)
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        IdeMainContent(
                            state = state,
                            viewModel = viewModel,
                            showExplorerToggle = false,
                            onOpenExplorer = {}
                        )
                    }
                }
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = state.currentDestination == IdeDestination.EDITOR,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = ObsidianSurface,
                        modifier = Modifier.width(300.dp)
                    ) {
                        FileExplorerPanel(
                            project = state.activeProject,
                            fileTree = state.fileTree,
                            expandedFolders = state.expandedFolders,
                            activeFilePath = state.activeTabPath,
                            onToggleFolder = viewModel::toggleFolderExpanded,
                            onOpenFile = { path ->
                                viewModel.openFile(path)
                                scope.launch { drawerState.close() }
                            },
                            onCreateFile = viewModel::createNewFile,
                            onCreateFolder = viewModel::createNewFolder,
                            onRenameNode = viewModel::renameNode,
                            onDeleteNode = viewModel::deleteNode
                        )
                    }
                }
            ) {
                Scaffold(
                    containerColor = ObsidianBg,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        NavigationBar(containerColor = ObsidianSurface) {
                            IdeDestination.entries.forEach { dest ->
                                val selected = state.currentDestination == dest
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { viewModel.navigateTo(dest) },
                                    icon = { Icon(destinationIcon(dest, selected), contentDescription = dest.label) },
                                    label = { Text(dest.label) },
                                    modifier = Modifier.testTag("nav_${dest.name.lowercase()}")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        IdeMainContent(
                            state = state,
                            viewModel = viewModel,
                            showExplorerToggle = true,
                            onOpenExplorer = { scope.launch { drawerState.open() } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IdeMainContent(
    state: IdeUiState,
    viewModel: IdeViewModel,
    showExplorerToggle: Boolean,
    onOpenExplorer: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when (state.currentDestination) {
                IdeDestination.EDITOR -> {
                    CodeEditorScreen(
                        state = state,
                        showExplorerToggle = showExplorerToggle,
                        onOpenExplorer = onOpenExplorer,
                        onSelectTab = viewModel::openFile,
                        onCloseTab = viewModel::closeTab,
                        onUpdateContent = viewModel::updateActiveTabContent,
                        onInsertSnippet = viewModel::insertSnippetInActiveTab,
                        onSaveActive = { viewModel.saveActiveTab() },
                        onAdjustFontSize = viewModel::adjustEditorFontSize,
                        onToggleWordWrap = viewModel::toggleWordWrap,
                        onRunOrPreview = {
                            viewModel.saveAllDirtyTabs {
                                viewModel.navigateTo(IdeDestination.PREVIEW)
                            }
                        },
                        onPushAndBuild = {
                            viewModel.saveAllDirtyTabs {
                                viewModel.navigateTo(IdeDestination.GITHUB_CI)
                            }
                        }
                    )
                }

                IdeDestination.PREVIEW -> {
                    LivePreviewScreen(
                        state = state,
                        projectDir = viewModel.getActiveProjectDir(),
                        onReloadWeb = viewModel::reloadWebPreview,
                        onConsoleLog = viewModel::appendLog,
                        onEvaluateJs = viewModel::evaluateJsInPreview,
                        onConsumeJsEval = viewModel::consumePendingJsEval,
                        onGoToGitHubBuild = { viewModel.navigateTo(IdeDestination.GITHUB_CI) }
                    )
                }

                IdeDestination.GITHUB_CI -> {
                    GitHubBuildScreen(
                        state = state,
                        onTokenChange = viewModel::updateGithubTokenInput,
                        onConnectGitHub = { viewModel.connectGitHub() },
                        onDisconnectGitHub = viewModel::disconnectGitHub,
                        onSelectRepo = viewModel::selectRepo,
                        onSelectManualRepo = viewModel::selectManualRepoSlug,
                        onCreateRepo = viewModel::createNewGitHubRepo,
                        onBranchChange = viewModel::updateTargetBranch,
                        onCommitMessageChange = viewModel::updateCommitMessage,
                        onPushAndBuild = viewModel::pushAndTriggerBuild,
                        onManualDispatch = viewModel::triggerManualWorkflow,
                        onRefreshRuns = { viewModel.refreshWorkflowRuns() },
                        onSelectRun = viewModel::selectWorkflowRun,
                        onDownloadArtifact = viewModel::downloadArtifact,
                        onInstallError = viewModel::showToast
                    )
                }

                IdeDestination.PROJECTS -> {
                    ProjectsScreen(
                        state = state,
                        onSelectProject = { proj ->
                            viewModel.selectProject(proj)
                            viewModel.navigateTo(IdeDestination.EDITOR)
                        },
                        onCreateFromTemplate = viewModel::createProjectFromTemplate,
                        onDeleteProject = viewModel::deleteProject,
                        onCloneGitHubRepo = viewModel::cloneGitHubRepo
                    )
                }
            }
        }

        if (state.currentDestination == IdeDestination.EDITOR || state.currentDestination == IdeDestination.PREVIEW) {
            ConsoleDrawer(
                logs = state.consoleLogs,
                isExpanded = state.isConsoleExpanded,
                activeFilter = state.consoleFilter,
                onToggleExpanded = { viewModel.setConsoleExpanded(!state.isConsoleExpanded) },
                onSelectFilter = viewModel::setConsoleFilter,
                onClear = viewModel::clearConsole
            )
        }
    }
}

private fun destinationIcon(destination: IdeDestination, selected: Boolean): ImageVector {
    return when (destination) {
        IdeDestination.EDITOR -> if (selected) Icons.Filled.Code else Icons.Outlined.Code
        IdeDestination.PREVIEW -> if (selected) Icons.Filled.PlayCircle else Icons.Outlined.PlayCircle
        IdeDestination.GITHUB_CI -> if (selected) Icons.Filled.CloudUpload else Icons.Outlined.CloudUpload
        IdeDestination.PROJECTS -> if (selected) Icons.Filled.FolderCopy else Icons.Outlined.FolderCopy
    }
}
