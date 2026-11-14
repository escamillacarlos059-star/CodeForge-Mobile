package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GitHubApiClient
import com.example.data.SettingsRepository
import com.example.data.WorkspaceRepository
import com.example.model.ConsoleEntry
import com.example.model.DownloadedArtifact
import com.example.model.FileNode
import com.example.model.GitHubArtifact
import com.example.model.GitHubJob
import com.example.model.GitHubRepo
import com.example.model.GitHubUser
import com.example.model.GitHubWorkflowRun
import com.example.model.IdeDestination
import com.example.model.IdeProject
import com.example.model.LogCategory
import com.example.model.LogLevel
import com.example.model.OpenTab
import com.example.model.TemplateId
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class IdeUiState(
    val currentDestination: IdeDestination = IdeDestination.EDITOR,
    val projects: List<IdeProject> = emptyList(),
    val activeProject: IdeProject? = null,
    val fileCounts: Map<String, Int> = emptyMap(),
    val fileTree: List<FileNode> = emptyList(),
    val expandedFolders: Set<String> = emptySet(),
    val openTabs: List<OpenTab> = emptyList(),
    val activeTabPath: String? = null,
    val consoleLogs: List<ConsoleEntry> = emptyList(),
    val isConsoleExpanded: Boolean = false,
    val consoleFilter: LogCategory = LogCategory.ALL,
    val editorFontSizeSp: Int = 13,
    val wordWrap: Boolean = false,
    val previewReloadTick: Int = 0,
    val pendingJsEval: String? = null,
    // GitHub & CI state
    val githubToken: String = "",
    val githubUser: GitHubUser? = null,
    val githubRepos: List<GitHubRepo> = emptyList(),
    val selectedRepo: GitHubRepo? = null,
    val targetBranch: String = "main",
    val commitMessage: String = "feat: actualización desde CodeForge IDE",
    val isConnectingGitHub: Boolean = false,
    val isPushing: Boolean = false,
    val pushProgress: String = "",
    val isPollingRun: Boolean = false,
    val workflowRuns: List<GitHubWorkflowRun> = emptyList(),
    val selectedRun: GitHubWorkflowRun? = null,
    val runJobs: List<GitHubJob> = emptyList(),
    val runArtifacts: List<GitHubArtifact> = emptyList(),
    val downloadingArtifactId: Long? = null,
    val downloadProgress: Float = 0f,
    val downloadedArtifacts: Map<Long, DownloadedArtifact> = emptyMap(),
    val isCloningRepo: Boolean = false,
    val cloneProgress: String = "",
    val toastMessage: String? = null,
    val errorMessage: String? = null
) {
    val activeTab: OpenTab?
        get() = openTabs.firstOrNull { it.relativePath == activeTabPath }
}

class IdeViewModel(application: Application) : AndroidViewModel(application) {

    private val workspaceRepo = WorkspaceRepository(application)
    private val settingsRepo = SettingsRepository(application)
    private val logIdGen = AtomicLong(1L)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val _uiState = MutableStateFlow(IdeUiState())
    val uiState: StateFlow<IdeUiState> = _uiState.asStateFlow()

    private val gitHubClient = GitHubApiClient { cat, lvl, tag, msg ->
        appendLog(cat, lvl, tag, msg)
    }

    private var pollingJob: Job? = null

    init {
        initializeWorkspace()
    }

    private fun initializeWorkspace() {
        viewModelScope.launch {
            appendLog(LogCategory.SYSTEM, LogLevel.INFO, "CodeForge", "Inicializando entorno de trabajo...")
            val settings = settingsRepo.settingsFlow.first()
            val projects = workspaceRepo.loadOrSeedProjects()
            val counts = projects.associate { it.id to workspaceRepo.countFiles(it.id) }
            val active = projects.firstOrNull { it.id == settings.activeProjectId } ?: projects.firstOrNull()

            _uiState.update {
                it.copy(
                    projects = projects,
                    fileCounts = counts,
                    githubToken = settings.githubToken,
                    editorFontSizeSp = settings.editorFontSizeSp,
                    wordWrap = settings.wordWrap
                )
            }

            if (active != null) {
                selectProject(active)
            }

            if (settings.githubToken.isNotBlank()) {
                connectGitHub(settings.githubToken, silent = true)
            }
        }
    }

    fun navigateTo(destination: IdeDestination) {
        _uiState.update { it.copy(currentDestination = destination) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(toastMessage = null, errorMessage = null) }
    }

    fun showToast(msg: String) {
        _uiState.update { it.copy(toastMessage = msg) }
    }

    fun appendLog(category: LogCategory, level: LogLevel, tag: String, message: String) {
        val entry = ConsoleEntry(
            id = logIdGen.getAndIncrement(),
            timestamp = timeFormat.format(Date()),
            category = category,
            level = level,
            tag = tag,
            message = message
        )
        _uiState.update { state ->
            val next = (state.consoleLogs + entry).takeLast(400)
            state.copy(consoleLogs = next)
        }
    }

    fun clearConsole() {
        _uiState.update { it.copy(consoleLogs = emptyList()) }
    }

    fun setConsoleExpanded(expanded: Boolean) {
        _uiState.update { it.copy(isConsoleExpanded = expanded) }
    }

    fun setConsoleFilter(category: LogCategory) {
        _uiState.update { it.copy(consoleFilter = category) }
    }

    fun adjustEditorFontSize(delta: Int) {
        val next = (_uiState.value.editorFontSizeSp + delta).coerceIn(10, 22)
        _uiState.update { it.copy(editorFontSizeSp = next) }
        viewModelScope.launch { settingsRepo.saveEditorFontSize(next) }
    }

    fun toggleWordWrap() {
        val next = !_uiState.value.wordWrap
        _uiState.update { it.copy(wordWrap = next) }
        viewModelScope.launch { settingsRepo.saveWordWrap(next) }
    }

    fun selectProject(project: IdeProject) {
        viewModelScope.launch {
            settingsRepo.saveActiveProjectId(project.id)
            val tree = workspaceRepo.buildFileTree(project.id)
            val defaultExpanded = collectTopFolderPaths(tree)
            val entryPath = workspaceRepo.pickDefaultEntryFile(project, tree)
            val initialTabs = if (entryPath != null) {
                val content = workspaceRepo.readFile(project.id, entryPath)
                listOf(OpenTab(entryPath, File(entryPath).name, content))
            } else {
                emptyList()
            }

            val linkedRepo = _uiState.value.githubRepos.firstOrNull {
                it.ownerLogin == project.githubOwner && it.name == project.githubRepo
            }

            _uiState.update {
                it.copy(
                    activeProject = project,
                    fileTree = tree,
                    expandedFolders = defaultExpanded,
                    openTabs = initialTabs,
                    activeTabPath = entryPath,
                    selectedRepo = linkedRepo ?: it.selectedRepo,
                    targetBranch = project.githubBranch.ifBlank { linkedRepo?.defaultBranch ?: "main" },
                    previewReloadTick = it.previewReloadTick + 1
                )
            }
            appendLog(
                LogCategory.SYSTEM,
                LogLevel.INFO,
                "Proyecto",
                "Abierto '${project.name}' (${project.type.label})"
            )
            if (linkedRepo != null && _uiState.value.githubToken.isNotBlank()) {
                refreshWorkflowRuns()
            }
        }
    }

    fun createProjectFromTemplate(name: String, templateId: TemplateId) {
        viewModelScope.launch {
            val created = workspaceRepo.createProjectFromTemplate(name, templateId)
            val projects = workspaceRepo.loadOrSeedProjects()
            val counts = projects.associate { it.id to workspaceRepo.countFiles(it.id) }
            _uiState.update {
                it.copy(
                    projects = projects,
                    fileCounts = counts,
                    toastMessage = "Proyecto '${created.name}' creado"
                )
            }
            selectProject(created)
            navigateTo(IdeDestination.EDITOR)
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            val remaining = workspaceRepo.deleteProject(projectId)
            val counts = remaining.associate { it.id to workspaceRepo.countFiles(it.id) }
            _uiState.update { it.copy(projects = remaining, fileCounts = counts) }
            if (_uiState.value.activeProject?.id == projectId) {
                remaining.firstOrNull()?.let { selectProject(it) }
            }
        }
    }

    fun getActiveProjectDir(): File? {
        val id = _uiState.value.activeProject?.id ?: return null
        return workspaceRepo.getProjectDir(id)
    }

    fun toggleFolderExpanded(relativePath: String) {
        _uiState.update { state ->
            val next = if (relativePath in state.expandedFolders) {
                state.expandedFolders - relativePath
            } else {
                state.expandedFolders + relativePath
            }
            state.copy(expandedFolders = next)
        }
    }

    fun openFile(relativePath: String) {
        val projectId = _uiState.value.activeProject?.id ?: return
        val existing = _uiState.value.openTabs.firstOrNull { it.relativePath == relativePath }
        if (existing != null) {
            _uiState.update { it.copy(activeTabPath = relativePath) }
            return
        }
        viewModelScope.launch {
            val content = workspaceRepo.readFile(projectId, relativePath)
            val newTab = OpenTab(
                relativePath = relativePath,
                name = File(relativePath).name,
                content = content
            )
            _uiState.update { state ->
                state.copy(
                    openTabs = state.openTabs + newTab,
                    activeTabPath = relativePath
                )
            }
        }
    }

    fun closeTab(relativePath: String) {
        _uiState.update { state ->
            val updated = state.openTabs.filterNot { it.relativePath == relativePath }
            val nextActive = if (state.activeTabPath == relativePath) {
                updated.lastOrNull()?.relativePath
            } else {
                state.activeTabPath
            }
            state.copy(openTabs = updated, activeTabPath = nextActive)
        }
    }

    fun updateActiveTabContent(newText: String) {
        val activePath = _uiState.value.activeTabPath ?: return
        _uiState.update { state ->
            val updatedTabs = state.openTabs.map { tab ->
                if (tab.relativePath == activePath) tab.copy(content = newText) else tab
            }
            state.copy(openTabs = updatedTabs)
        }
    }

    fun insertSnippetInActiveTab(snippet: String) {
        val active = _uiState.value.activeTab ?: return
        updateActiveTabContent(active.content + snippet)
    }

    fun saveActiveTab(silent: Boolean = false) {
        val projectId = _uiState.value.activeProject?.id ?: return
        val tab = _uiState.value.activeTab ?: return
        viewModelScope.launch {
            val ok = workspaceRepo.writeFile(projectId, tab.relativePath, tab.content)
            if (ok) {
                _uiState.update { state ->
                    val updatedTabs = state.openTabs.map {
                        if (it.relativePath == tab.relativePath) it.copy(savedContent = it.content) else it
                    }
                    state.copy(
                        openTabs = updatedTabs,
                        previewReloadTick = state.previewReloadTick + 1,
                        toastMessage = if (silent) state.toastMessage else "Guardado: ${tab.name}"
                    )
                }
            }
        }
    }

    fun saveAllDirtyTabs(onComplete: (() -> Unit)? = null) {
        val projectId = _uiState.value.activeProject?.id ?: return
        val dirtyTabs = _uiState.value.openTabs.filter { it.isDirty }
        viewModelScope.launch {
            dirtyTabs.forEach { tab ->
                workspaceRepo.writeFile(projectId, tab.relativePath, tab.content)
            }
            if (dirtyTabs.isNotEmpty()) {
                _uiState.update { state ->
                    state.copy(
                        openTabs = state.openTabs.map { it.copy(savedContent = it.content) },
                        previewReloadTick = state.previewReloadTick + 1
                    )
                }
            }
            onComplete?.invoke()
        }
    }

    fun createNewFile(relativePath: String) {
        val projectId = _uiState.value.activeProject?.id ?: return
        viewModelScope.launch {
            val ok = workspaceRepo.createFile(projectId, relativePath, defaultContentForPath(relativePath))
            if (ok) {
                refreshTreeAndCounts(projectId)
                openFile(relativePath.trim().trimStart('/'))
                appendLog(LogCategory.SYSTEM, LogLevel.SUCCESS, "Archivo", "Creado: $relativePath")
            } else {
                _uiState.update { it.copy(errorMessage = "No se pudo crear '$relativePath' (¿ya existe?)") }
            }
        }
    }

    fun createNewFolder(relativePath: String) {
        val projectId = _uiState.value.activeProject?.id ?: return
        viewModelScope.launch {
            val ok = workspaceRepo.createFolder(projectId, relativePath)
            if (ok) {
                refreshTreeAndCounts(projectId)
                _uiState.update { it.copy(expandedFolders = it.expandedFolders + relativePath.trim().trimStart('/')) }
                appendLog(LogCategory.SYSTEM, LogLevel.SUCCESS, "Carpeta", "Creada: $relativePath")
            } else {
                _uiState.update { it.copy(errorMessage = "No se pudo crear la carpeta '$relativePath'") }
            }
        }
    }

    fun renameNode(oldPath: String, newPath: String) {
        val projectId = _uiState.value.activeProject?.id ?: return
        viewModelScope.launch {
            val ok = workspaceRepo.renameNode(projectId, oldPath, newPath)
            if (ok) {
                val cleanNew = newPath.trim().trimStart('/')
                _uiState.update { state ->
                    val updatedTabs = state.openTabs.map { tab ->
                        if (tab.relativePath == oldPath) {
                            tab.copy(relativePath = cleanNew, name = File(cleanNew).name)
                        } else {
                            tab
                        }
                    }
                    val updatedActive = if (state.activeTabPath == oldPath) cleanNew else state.activeTabPath
                    state.copy(openTabs = updatedTabs, activeTabPath = updatedActive)
                }
                refreshTreeAndCounts(projectId)
            } else {
                _uiState.update { it.copy(errorMessage = "No se pudo renombrar '$oldPath'") }
            }
        }
    }

    fun deleteNode(relativePath: String) {
        val projectId = _uiState.value.activeProject?.id ?: return
        viewModelScope.launch {
            val ok = workspaceRepo.deleteNode(projectId, relativePath)
            if (ok) {
                _uiState.update { state ->
                    val updatedTabs = state.openTabs.filterNot {
                        it.relativePath == relativePath || it.relativePath.startsWith("$relativePath/")
                    }
                    val nextActive = if (state.activeTabPath !in updatedTabs.map { t -> t.relativePath }) {
                        updatedTabs.lastOrNull()?.relativePath
                    } else {
                        state.activeTabPath
                    }
                    state.copy(openTabs = updatedTabs, activeTabPath = nextActive)
                }
                refreshTreeAndCounts(projectId)
                appendLog(LogCategory.SYSTEM, LogLevel.WARN, "Archivo", "Eliminado: $relativePath")
            }
        }
    }

    fun reloadWebPreview() {
        saveAllDirtyTabs {
            _uiState.update { it.copy(previewReloadTick = it.previewReloadTick + 1) }
        }
    }

    fun evaluateJsInPreview(script: String) {
        if (script.isBlank()) return
        appendLog(LogCategory.WEB_CONSOLE, LogLevel.INFO, "JS >", script)
        _uiState.update { it.copy(pendingJsEval = script) }
    }

    fun consumePendingJsEval() {
        _uiState.update { it.copy(pendingJsEval = null) }
    }

    // --- GitHub API & CI/CD ---

    fun updateGithubTokenInput(token: String) {
        _uiState.update { it.copy(githubToken = token) }
    }

    fun connectGitHub(token: String = _uiState.value.githubToken, silent: Boolean = false) {
        val clean = token.trim()
        if (clean.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Ingresa un GitHub Personal Access Token válido.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isConnectingGitHub = true, githubToken = clean) }
            settingsRepo.saveGithubToken(clean)
            val userRes = gitHubClient.verifyToken(clean)
            if (userRes.isFailure) {
                _uiState.update {
                    it.copy(
                        isConnectingGitHub = false,
                        githubUser = null,
                        errorMessage = if (silent) null else userRes.exceptionOrNull()?.message
                    )
                }
                return@launch
            }
            val user = userRes.getOrThrow()
            val repos = gitHubClient.listRepos(clean).getOrDefault(emptyList())
            val activeProj = _uiState.value.activeProject
            val matched = repos.firstOrNull {
                it.ownerLogin == activeProj?.githubOwner && it.name == activeProj?.githubRepo
            } ?: repos.firstOrNull()

            _uiState.update {
                it.copy(
                    isConnectingGitHub = false,
                    githubUser = user,
                    githubRepos = repos,
                    selectedRepo = matched,
                    targetBranch = activeProj?.githubBranch?.ifBlank { matched?.defaultBranch ?: "main" }
                        ?: matched?.defaultBranch ?: "main",
                    toastMessage = if (silent) null else "Conectado a GitHub como @${user.login}"
                )
            }
            if (matched != null) {
                refreshWorkflowRuns()
            }
        }
    }

    fun disconnectGitHub() {
        pollingJob?.cancel()
        viewModelScope.launch {
            settingsRepo.saveGithubToken("")
            _uiState.update {
                it.copy(
                    githubToken = "",
                    githubUser = null,
                    githubRepos = emptyList(),
                    selectedRepo = null,
                    workflowRuns = emptyList(),
                    selectedRun = null,
                    runJobs = emptyList(),
                    runArtifacts = emptyList()
                )
            }
        }
    }

    fun selectRepo(repo: GitHubRepo) {
        _uiState.update {
            it.copy(
                selectedRepo = repo,
                targetBranch = repo.defaultBranch.ifBlank { "main" },
                workflowRuns = emptyList(),
                selectedRun = null,
                runJobs = emptyList(),
                runArtifacts = emptyList()
            )
        }
        refreshWorkflowRuns()
    }

    fun updateTargetBranch(branch: String) {
        _uiState.update { it.copy(targetBranch = branch) }
    }

    fun updateCommitMessage(msg: String) {
        _uiState.update { it.copy(commitMessage = msg) }
    }

    fun createNewGitHubRepo(name: String, description: String, isPrivate: Boolean) {
        val token = _uiState.value.githubToken
        if (token.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Conecta tu token de GitHub primero.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isConnectingGitHub = true) }
            val res = gitHubClient.createRepo(token, name, description, isPrivate)
            res.onSuccess { newRepo ->
                val updatedRepos = listOf(newRepo) + _uiState.value.githubRepos
                _uiState.update {
                    it.copy(
                        isConnectingGitHub = false,
                        githubRepos = updatedRepos,
                        selectedRepo = newRepo,
                        targetBranch = newRepo.defaultBranch,
                        toastMessage = "Repositorio '${newRepo.fullName}' creado"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isConnectingGitHub = false,
                        errorMessage = err.message ?: "Error al crear repositorio"
                    )
                }
            }
        }
    }

    fun cloneGitHubRepo(repo: GitHubRepo) {
        val token = _uiState.value.githubToken
        if (token.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCloningRepo = true, cloneProgress = "Conectando...") }
            val filesRes = gitHubClient.fetchRepoFiles(
                token = token,
                owner = repo.ownerLogin,
                repo = repo.name,
                branch = repo.defaultBranch
            ) { status ->
                _uiState.update { it.copy(cloneProgress = status) }
            }
            filesRes.onSuccess { fileMap ->
                val project = workspaceRepo.createProjectFromRemoteFiles(
                    repoName = repo.name,
                    owner = repo.ownerLogin,
                    branch = repo.defaultBranch,
                    files = fileMap
                )
                val projects = workspaceRepo.loadOrSeedProjects()
                val counts = projects.associate { it.id to workspaceRepo.countFiles(it.id) }
                _uiState.update {
                    it.copy(
                        isCloningRepo = false,
                        cloneProgress = "",
                        projects = projects,
                        fileCounts = counts,
                        selectedRepo = repo,
                        toastMessage = "Importados ${fileMap.size} archivos de ${repo.fullName}"
                    )
                }
                selectProject(project)
                navigateTo(IdeDestination.EDITOR)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isCloningRepo = false,
                        cloneProgress = "",
                        errorMessage = err.message ?: "Error al clonar repositorio"
                    )
                }
            }
        }
    }

    fun pushAndTriggerBuild() {
        val state = _uiState.value
        val project = state.activeProject ?: return
        val repo = state.selectedRepo ?: run {
            _uiState.update { it.copy(errorMessage = "Selecciona o crea un repositorio de GitHub.") }
            return
        }
        val token = state.githubToken
        if (token.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Conecta tu token de GitHub primero.") }
            return
        }
        val branch = state.targetBranch.trim().ifBlank { repo.defaultBranch }

        saveAllDirtyTabs {
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        isPushing = true,
                        pushProgress = "Preparando archivos locales..."
                    )
                }

                val files = workspaceRepo.collectAllFilesForPush(project.id)
                val pushRes = gitHubClient.pushFilesAtomically(
                    token = token,
                    owner = repo.ownerLogin,
                    repo = repo.name,
                    branch = branch,
                    commitMessage = state.commitMessage,
                    files = files
                ) { progress ->
                    _uiState.update { it.copy(pushProgress = progress) }
                }

                if (pushRes.isFailure) {
                    _uiState.update {
                        it.copy(
                            isPushing = false,
                            pushProgress = "",
                            errorMessage = pushRes.exceptionOrNull()?.message ?: "Error al subir archivos"
                        )
                    }
                    return@launch
                }

                val commitSha = pushRes.getOrThrow()
                val updatedProjects = workspaceRepo.updateProjectLink(
                    projectId = project.id,
                    owner = repo.ownerLogin,
                    repo = repo.name,
                    branch = branch
                )
                val refreshedActive = updatedProjects.firstOrNull { it.id == project.id } ?: project

                // Detect workflow file (.github/workflows/build.yml or legacy android-build.yml)
                val workflowFile = when {
                    files.any { it.first == ".github/workflows/build.yml" } -> "build.yml"
                    files.any { it.first == ".github/workflows/android-build.yml" } -> "android-build.yml"
                    else -> null
                }
                _uiState.update {
                    it.copy(
                        projects = updatedProjects,
                        activeProject = refreshedActive,
                        pushProgress = "Esperando inicio de GitHub Actions..."
                    )
                }

                delay(2000L)
                val runsAfterPush = gitHubClient.listWorkflowRuns(token, repo.ownerLogin, repo.name)
                    .getOrDefault(emptyList())
                val matchingRun = runsAfterPush.firstOrNull { it.headSha == commitSha }

                if (matchingRun == null && workflowFile != null) {
                    gitHubClient.triggerWorkflowDispatch(
                        token = token,
                        owner = repo.ownerLogin,
                        repo = repo.name,
                        workflowFile = workflowFile,
                        branch = branch
                    )
                    delay(1800L)
                }

                _uiState.update {
                    it.copy(
                        isPushing = false,
                        pushProgress = "",
                        toastMessage = "Código subido (${commitSha.take(7)}). Monitoreando compilación..."
                    )
                }

                refreshWorkflowRuns(autoStartPolling = true)
            }
        }
    }

    fun selectManualRepoSlug(slug: String) {
        val token = _uiState.value.githubToken
        if (token.isBlank()) return
        val cleaned = slug.trim()
            .removePrefix("https://github.com/")
            .trim('/')
        val parts = cleaned.split('/')
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            _uiState.update { it.copy(errorMessage = "Usa el formato usuario/repositorio") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isConnectingGitHub = true) }
            gitHubClient.getRepo(token, parts[0], parts[1])
                .onSuccess { repo ->
                    val merged = (listOf(repo) + _uiState.value.githubRepos).distinctBy { it.id }
                    _uiState.update {
                        it.copy(
                            isConnectingGitHub = false,
                            githubRepos = merged,
                            selectedRepo = repo,
                            targetBranch = repo.defaultBranch.ifBlank { "main" },
                            toastMessage = "Repositorio seleccionado: ${repo.fullName}"
                        )
                    }
                    refreshWorkflowRuns()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isConnectingGitHub = false,
                            errorMessage = err.message ?: "No se encontró el repositorio"
                        )
                    }
                }
        }
    }

    fun triggerManualWorkflow() {
        val state = _uiState.value
        val repo = state.selectedRepo ?: return
        val project = state.activeProject
        val token = state.githubToken
        if (token.isBlank()) return
        val branch = state.targetBranch.trim().ifBlank { repo.defaultBranch }

        viewModelScope.launch {
            val files = if (project != null) workspaceRepo.collectAllFilesForPush(project.id) else emptyList()
            val workflowFile = when {
                files.any { it.first == ".github/workflows/build.yml" } -> "build.yml"
                files.any { it.first == ".github/workflows/android-build.yml" } -> "android-build.yml"
                else -> "build.yml"
            }
            val res = gitHubClient.triggerWorkflowDispatch(
                token = token,
                owner = repo.ownerLogin,
                repo = repo.name,
                workflowFile = workflowFile,
                branch = branch
            )
            res.onSuccess {
                _uiState.update { it.copy(toastMessage = "Workflow $workflowFile disparado") }
                delay(2000L)
                refreshWorkflowRuns(autoStartPolling = true)
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message ?: "No se pudo disparar el workflow") }
            }
        }
    }

    fun refreshWorkflowRuns(autoStartPolling: Boolean = false) {
        val state = _uiState.value
        val repo = state.selectedRepo ?: return
        val token = state.githubToken
        if (token.isBlank()) return

        viewModelScope.launch {
            val runs = gitHubClient.listWorkflowRuns(token, repo.ownerLogin, repo.name)
                .getOrDefault(emptyList())
            val selected = runs.firstOrNull { it.id == _uiState.value.selectedRun?.id } ?: runs.firstOrNull()
            _uiState.update { it.copy(workflowRuns = runs, selectedRun = selected) }
            if (selected != null) {
                loadRunDetails(repo, selected)
                if (autoStartPolling || selected.status != "completed") {
                    startLivePolling(repo, selected.id)
                }
            }
        }
    }

    fun selectWorkflowRun(run: GitHubWorkflowRun) {
        val repo = _uiState.value.selectedRepo ?: return
        _uiState.update { it.copy(selectedRun = run) }
        viewModelScope.launch {
            loadRunDetails(repo, run)
            if (run.status != "completed") {
                startLivePolling(repo, run.id)
            } else {
                pollingJob?.cancel()
                _uiState.update { it.copy(isPollingRun = false) }
            }
        }
    }

    private suspend fun loadRunDetails(repo: GitHubRepo, run: GitHubWorkflowRun) {
        val token = _uiState.value.githubToken
        if (token.isBlank()) return
        val jobs = gitHubClient.listRunJobs(token, repo.ownerLogin, repo.name, run.id)
            .getOrDefault(emptyList())
        val artifacts = if (run.status == "completed") {
            gitHubClient.listRunArtifacts(token, repo.ownerLogin, repo.name, run.id)
                .getOrDefault(emptyList())
        } else {
            emptyList()
        }
        _uiState.update { it.copy(runJobs = jobs, runArtifacts = artifacts) }
    }

    private fun startLivePolling(repo: GitHubRepo, runId: Long) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            _uiState.update { it.copy(isPollingRun = true) }
            repeat(90) { // up to ~6 minutes of live polling
                val token = _uiState.value.githubToken
                if (token.isBlank()) {
                    _uiState.update { it.copy(isPollingRun = false) }
                    return@launch
                }
                val runs = gitHubClient.listWorkflowRuns(token, repo.ownerLogin, repo.name)
                    .getOrDefault(emptyList())
                val current = runs.firstOrNull { it.id == runId } ?: runs.firstOrNull()
                if (current != null) {
                    _uiState.update { it.copy(workflowRuns = runs, selectedRun = current) }
                    loadRunDetails(repo, current)
                    if (current.status == "completed") {
                        val level = if (current.conclusion == "success") LogLevel.SUCCESS else LogLevel.ERROR
                        appendLog(
                            LogCategory.BUILD,
                            level,
                            "Run #${current.runNumber}",
                            "Compilación finalizada: ${current.conclusion ?: "completed"}"
                        )
                        _uiState.update { it.copy(isPollingRun = false) }
                        return@launch
                    }
                }
                delay(4000L)
            }
            _uiState.update { it.copy(isPollingRun = false) }
        }
    }

    fun downloadArtifact(artifact: GitHubArtifact, onDownloadedApk: ((DownloadedArtifact) -> Unit)? = null) {
        val token = _uiState.value.githubToken
        if (token.isBlank()) return
        val existing = _uiState.value.downloadedArtifacts[artifact.id]
        if (existing != null && File(existing.localFilePath).exists()) {
            onDownloadedApk?.invoke(existing)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(downloadingArtifactId = artifact.id, downloadProgress = 0.05f) }
            val destFolder = if (artifact.name.contains("aab", ignoreCase = true)) {
                File(getApplication<Application>().filesDir, "bundles")
            } else {
                File(getApplication<Application>().filesDir, "apks")
            }
            val result = gitHubClient.downloadAndExtractArtifact(
                token = token,
                artifact = artifact,
                destDir = destFolder
            ) { progress ->
                _uiState.update { it.copy(downloadProgress = progress) }
            }

            result.onSuccess { downloaded ->
                _uiState.update { state ->
                    state.copy(
                        downloadingArtifactId = null,
                        downloadProgress = 0f,
                        downloadedArtifacts = state.downloadedArtifacts + (artifact.id to downloaded),
                        toastMessage = "Listo: ${downloaded.fileName}"
                    )
                }
                onDownloadedApk?.invoke(downloaded)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        downloadingArtifactId = null,
                        downloadProgress = 0f,
                        errorMessage = err.message ?: "Error al descargar artefacto"
                    )
                }
            }
        }
    }

    private suspend fun refreshTreeAndCounts(projectId: String) {
        val tree = workspaceRepo.buildFileTree(projectId)
        val projects = workspaceRepo.loadOrSeedProjects()
        val counts = projects.associate { it.id to workspaceRepo.countFiles(it.id) }
        _uiState.update {
            it.copy(
                fileTree = tree,
                projects = projects,
                fileCounts = counts
            )
        }
    }

    private fun collectTopFolderPaths(nodes: List<FileNode>): Set<String> {
        val out = mutableSetOf<String>()
        nodes.filter { it.isDirectory }.forEach { node ->
            out.add(node.relativePath)
            if (node.name in setOf("app", "src", "main", "styles", "scripts", ".github")) {
                out.addAll(collectTopFolderPaths(node.children))
            }
        }
        return out
    }

    private fun defaultContentForPath(path: String): String {
        val ext = path.substringAfterLast('.', "").lowercase()
        val baseName = File(path).nameWithoutExtension
        return when (ext) {
            "kt" -> "package com.codeforge.app\n\nfun $baseName() {\n    \n}\n"
            "html" -> "<!DOCTYPE html>\n<html lang=\"es\">\n<head>\n  <meta charset=\"UTF-8\">\n  <title>$baseName</title>\n</head>\n<body>\n  <h1>$baseName</h1>\n</body>\n</html>\n"
            "css" -> "/* $baseName.css */\nbody {\n  margin: 0;\n}\n"
            "js" -> "// $baseName.js\nconsole.log('$baseName cargado');\n"
            "json" -> "{\n  \"name\": \"$baseName\"\n}\n"
            "xml" -> "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n</resources>\n"
            else -> ""
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
