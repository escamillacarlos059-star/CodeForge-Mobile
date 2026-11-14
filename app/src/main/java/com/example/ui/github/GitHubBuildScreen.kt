package com.example.ui.github

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.IdeUiState
import com.example.model.DownloadedArtifact
import com.example.model.GitHubArtifact
import com.example.model.GitHubJob
import com.example.model.GitHubRepo
import com.example.model.GitHubWorkflowRun
import com.example.ui.theme.ForgeAmber
import com.example.ui.theme.ForgeCrimson
import com.example.ui.theme.ForgeCyan
import com.example.ui.theme.ForgeEmerald
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.ApkInstaller
import com.example.util.InstallOutcome

@Composable
fun GitHubBuildScreen(
    state: IdeUiState,
    onTokenChange: (String) -> Unit,
    onConnectGitHub: () -> Unit,
    onDisconnectGitHub: () -> Unit,
    onSelectRepo: (GitHubRepo) -> Unit,
    onSelectManualRepo: (String) -> Unit,
    onCreateRepo: (String, String, Boolean) -> Unit,
    onBranchChange: (String) -> Unit,
    onCommitMessageChange: (String) -> Unit,
    onPushAndBuild: () -> Unit,
    onManualDispatch: () -> Unit,
    onRefreshRuns: () -> Unit,
    onSelectRun: (GitHubWorkflowRun) -> Unit,
    onDownloadArtifact: (GitHubArtifact, ((DownloadedArtifact) -> Unit)?) -> Unit,
    onInstallError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showToken by remember { mutableStateOf(false) }
    var showCreateRepoDialog by remember { mutableStateOf(false) }
    var showManualRepoDialog by remember { mutableStateOf(false) }
    var repoMenuExpanded by remember { mutableStateOf(false) }
    var pendingUnknownSourcesPath by remember { mutableStateOf<String?>(null) }
    var canInstallPackages by remember { mutableStateOf(ApkInstaller.canInstallPackages(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canInstallPackages = ApkInstaller.canInstallPackages(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun triggerApkInstall(localPath: String) {
        when (val outcome = ApkInstaller.installApk(context, localPath)) {
            InstallOutcome.LaunchedInstaller -> Unit
            InstallOutcome.NeedsUnknownSourcesPermission -> {
                pendingUnknownSourcesPath = localPath
            }
            is InstallOutcome.Error -> onInstallError(outcome.message)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. GitHub Token Connection Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = ForgeCyan)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Conexión Real GitHub API",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        if (state.githubUser != null) {
                            IconButton(onClick = onDisconnectGitHub) {
                                Icon(Icons.Default.LinkOff, contentDescription = "Desconectar", tint = TextMuted)
                            }
                        }
                    }

                    if (state.githubUser == null) {
                        Text(
                            text = "Usa un Personal Access Token con permisos 'repo' y 'workflow' para subir tus proyectos y compilar APK/AAB en GitHub Actions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        OutlinedTextField(
                            value = state.githubToken,
                            onValueChange = onTokenChange,
                            label = { Text("GitHub Personal Access Token (ghp_...)") },
                            singleLine = true,
                            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showToken = !showToken }) {
                                    Icon(
                                        imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Mostrar token"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("github_token_input")
                        )
                        Button(
                            onClick = onConnectGitHub,
                            enabled = !state.isConnectingGitHub && state.githubToken.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("github_connect_button")
                        ) {
                            if (state.isConnectingGitHub) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Verificando con api.github.com...")
                            } else {
                                Text("Conectar y Verificar Token")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                ApkInstaller.openUrlInBrowser(
                                    context,
                                    "https://github.com/settings/tokens/new?scopes=repo,workflow&description=CodeForge+Android+IDE"
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Generar Token en GitHub (repo + workflow)")
                        }
                    } else {
                        Surface(
                            color = ObsidianSurfaceElevated,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(ForgeCyan.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = state.githubUser.login.take(2).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = ForgeCyan
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${state.githubUser.name} (@${state.githubUser.login})",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Scopes: ${state.githubUser.scopes.joinToString(", ").ifBlank { "Fine-grained PAT" }}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ForgeEmerald
                                    )
                                }
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ForgeEmerald)
                            }
                        }
                    }

                    HorizontalDivider(color = ObsidianBorder)

                    // Android Install Unknown Apps Permission Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.InstallMobile,
                            contentDescription = null,
                            tint = if (canInstallPackages) ForgeEmerald else ForgeAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (canInstallPackages) {
                                "Permiso instalador Android: Concedido"
                            } else {
                                "Permiso instalador Android: Pendiente"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (canInstallPackages) ForgeEmerald else ForgeAmber,
                            modifier = Modifier.weight(1f)
                        )
                        if (!canInstallPackages) {
                            TextButton(
                                onClick = { ApkInstaller.openUnknownSourcesSettings(context) },
                                modifier = Modifier.testTag("grant_install_permission_button")
                            ) {
                                Text("Dar permiso")
                            }
                        }
                    }
                }
            }
        }

        // 2. Repository Target + Push & Trigger Build Card
        if (state.githubUser != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Repositorio Destino (${state.activeProject?.name ?: ""})",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { showManualRepoDialog = true }) {
                                Text("owner/repo")
                            }
                            TextButton(
                                onClick = { showCreateRepoDialog = true },
                                modifier = Modifier.testTag("create_github_repo_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Nuevo")
                            }
                        }

                        // Repo Picker
                        Box {
                            Surface(
                                onClick = { repoMenuExpanded = true },
                                color = ObsidianSurfaceElevated,
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = state.selectedRepo?.fullName ?: "Seleccionar repositorio...",
                                        fontFamily = JetBrainsMonoFamily,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (state.selectedRepo != null) ForgeCyan else TextMuted,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(Icons.Default.ExpandMore, contentDescription = null, tint = TextSecondary)
                                }
                            }
                            DropdownMenu(
                                expanded = repoMenuExpanded,
                                onDismissRequest = { repoMenuExpanded = false }
                            ) {
                                state.githubRepos.forEach { repo ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(repo.fullName, fontFamily = JetBrainsMonoFamily, fontSize = 13.sp)
                                                Text(
                                                    "${if (repo.isPrivate) "Privado" else "Público"} • rama ${repo.defaultBranch}",
                                                    fontSize = 11.sp,
                                                    color = TextMuted
                                                )
                                            }
                                        },
                                        onClick = {
                                            repoMenuExpanded = false
                                            onSelectRepo(repo)
                                        }
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = state.targetBranch,
                                onValueChange = onBranchChange,
                                label = { Text("Rama") },
                                singleLine = true,
                                modifier = Modifier.weight(0.35f)
                            )
                            OutlinedTextField(
                                value = state.commitMessage,
                                onValueChange = onCommitMessageChange,
                                label = { Text("Mensaje de commit") },
                                singleLine = true,
                                modifier = Modifier.weight(0.65f)
                            )
                        }

                        if (state.isPushing) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(
                                    text = state.pushProgress,
                                    fontFamily = JetBrainsMonoFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ForgeCyan
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onPushAndBuild,
                                enabled = !state.isPushing && state.selectedRepo != null && state.activeProject != null,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("push_and_build_button")
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Subir y Compilar APK/AAB")
                            }

                            OutlinedButton(
                                onClick = onManualDispatch,
                                enabled = !state.isPushing && state.selectedRepo != null
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Disparar workflow")
                            }
                        }
                    }
                }
            }

            // 3. Live GitHub Actions Monitor Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Estado en Vivo — GitHub Actions",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            if (state.isPollingRun) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                            }
                            IconButton(
                                onClick = onRefreshRuns,
                                modifier = Modifier.testTag("refresh_runs_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Actualizar compilaciones", tint = ForgeCyan)
                            }
                        }

                        if (state.workflowRuns.isEmpty()) {
                            Text(
                                text = "Aún no hay ejecuciones de GitHub Actions en este repositorio. Presiona 'Subir y Compilar APK/AAB' para iniciar una compilación real.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(state.workflowRuns, key = { it.id }) { run ->
                                    val isSelected = run.id == state.selectedRun?.id
                                    val statusColor = runStatusColor(run.status, run.conclusion)
                                    Surface(
                                        onClick = { onSelectRun(run) },
                                        color = if (isSelected) ObsidianSurfaceElevated else ObsidianBg,
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSelected) statusColor else ObsidianBorder
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(statusColor)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "#${run.runNumber} ${run.conclusion ?: run.status}",
                                                fontFamily = JetBrainsMonoFamily,
                                                fontSize = 11.sp,
                                                color = TextPrimary
                                            )
                                        }
                                    }
                                }
                            }

                            state.selectedRun?.let { run ->
                                HorizontalDivider(color = ObsidianBorder)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${run.name} (#${run.runNumber})",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "Commit ${run.headSha.take(7)} • Estado: ${run.conclusion ?: run.status}",
                                            fontFamily = JetBrainsMonoFamily,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = runStatusColor(run.status, run.conclusion)
                                        )
                                    }
                                    IconButton(onClick = { ApkInstaller.openUrlInBrowser(context, run.htmlUrl) }) {
                                        Icon(Icons.Default.OpenInBrowser, contentDescription = "Abrir en GitHub", tint = ForgeCyan)
                                    }
                                }

                                state.runJobs.forEach { job ->
                                    JobStepsTimeline(job)
                                }
                            }
                        }
                    }
                }
            }

            // 4. Real Artifacts Download & Native APK Installer Card
            if (state.runArtifacts.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Android, contentDescription = null, tint = ForgeEmerald)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Artefactos Reales Compilados",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                            }

                            state.runArtifacts.forEach { artifact ->
                                val downloaded = state.downloadedArtifacts[artifact.id]
                                val isDownloading = state.downloadingArtifactId == artifact.id
                                val isApkArtifact = artifact.name.contains("apk", ignoreCase = true) || downloaded?.isApk == true

                                Surface(
                                    color = ObsidianBg,
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = artifact.name,
                                                    fontFamily = JetBrainsMonoFamily,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = if (isApkArtifact) ForgeEmerald else ForgeCyan
                                                )
                                                Text(
                                                    text = "${formatBytes(artifact.sizeInBytes)} • ${artifact.archiveDownloadUrl}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TextMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        if (isDownloading) {
                                            LinearProgressIndicator(
                                                progress = { state.downloadProgress },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Text(
                                                text = "Descargando y extrayendo ${(state.downloadProgress * 100).toInt()}%...",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ForgeCyan
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (isApkArtifact) {
                                                Button(
                                                    onClick = {
                                                        if (downloaded != null) {
                                                            triggerApkInstall(downloaded.localFilePath)
                                                        } else {
                                                            onDownloadArtifact(artifact) { result ->
                                                                if (result.isApk) {
                                                                    triggerApkInstall(result.localFilePath)
                                                                }
                                                            }
                                                        }
                                                    },
                                                    enabled = !isDownloading,
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = ForgeEmerald,
                                                        contentColor = ObsidianBg
                                                    ),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag("download_install_apk_${artifact.id}")
                                                ) {
                                                    Icon(Icons.Default.InstallMobile, contentDescription = null)
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        if (downloaded != null) "Instalar app-debug.apk" else "Descargar e Instalar APK"
                                                    )
                                                }
                                            } else {
                                                Button(
                                                    onClick = {
                                                        if (downloaded != null) {
                                                            ApkInstaller.shareFile(
                                                                context,
                                                                downloaded.localFilePath,
                                                                "application/octet-stream",
                                                                "Exportar ${downloaded.fileName}"
                                                            )
                                                        } else {
                                                            onDownloadArtifact(artifact, null)
                                                        }
                                                    },
                                                    enabled = !isDownloading,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(
                                                        imageVector = if (downloaded != null) Icons.Default.Share else Icons.Default.Download,
                                                        contentDescription = null
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(if (downloaded != null) "Exportar / Compartir AAB" else "Descargar AAB")
                                                }
                                            }

                                            if (downloaded != null && isApkArtifact) {
                                                OutlinedButton(
                                                    onClick = {
                                                        ApkInstaller.shareFile(
                                                            context,
                                                            downloaded.localFilePath,
                                                            "application/vnd.android.package-archive",
                                                            "Compartir APK"
                                                        )
                                                    }
                                                ) {
                                                    Icon(Icons.Default.Share, contentDescription = "Compartir APK")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateRepoDialog) {
        CreateRepoDialog(
            defaultName = state.activeProject?.name?.lowercase()?.replace(" ", "-") ?: "mi-app-codeforge",
            onDismiss = { showCreateRepoDialog = false },
            onCreate = { name, desc, isPriv ->
                showCreateRepoDialog = false
                onCreateRepo(name, desc, isPriv)
            }
        )
    }

    if (showManualRepoDialog) {
        var slugInput by remember {
            mutableStateOf(
                state.selectedRepo?.fullName
                    ?: "${state.githubUser?.login ?: "usuario"}/mi-repositorio"
            )
        }
        AlertDialog(
            onDismissRequest = { showManualRepoDialog = false },
            title = { Text("Conectar repositorio por nombre") },
            text = {
                OutlinedTextField(
                    value = slugInput,
                    onValueChange = { slugInput = it },
                    label = { Text("usuario/repositorio o URL de GitHub") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showManualRepoDialog = false
                        onSelectManualRepo(slugInput)
                    }
                ) {
                    Text("Buscar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualRepoDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    pendingUnknownSourcesPath?.let { path ->
        AlertDialog(
            onDismissRequest = { pendingUnknownSourcesPath = null },
            title = { Text("Permitir instalación de apps") },
            text = {
                Text("Android requiere habilitar 'Instalar aplicaciones desconocidas' para CodeForge para abrir el instalador nativo del sistema con tu APK compilado.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingUnknownSourcesPath = null
                        ApkInstaller.openUnknownSourcesSettings(context)
                    }
                ) {
                    Text("Abrir Ajustes")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUnknownSourcesPath = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun JobStepsTimeline(job: GitHubJob) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianBg, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Job: ${job.name}",
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary
        )
        job.steps.forEach { step ->
            val color = runStatusColor(step.status, step.conclusion)
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    step.status != "completed" && step.status != "queued" -> {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = ForgeAmber)
                    }
                    step.conclusion == "success" -> {
                        Icon(Icons.Default.CheckCircle, null, tint = ForgeEmerald, modifier = Modifier.size(14.dp))
                    }
                    step.conclusion == "failure" -> {
                        Icon(Icons.Default.Error, null, tint = ForgeCrimson, modifier = Modifier.size(14.dp))
                    }
                    else -> {
                        Icon(Icons.Default.Schedule, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${step.number}. ${step.name}",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = if (step.conclusion == "failure") ForgeCrimson else TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = step.conclusion ?: step.status,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = color
                )
            }
        }
    }
}

@Composable
private fun CreateRepoDialog(
    defaultName: String,
    onDismiss: () -> Unit,
    onCreate: (String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(defaultName) }
    var desc by remember { mutableStateOf("Proyecto creado en CodeForge IDE") }
    var isPrivate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear Repositorio en GitHub") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del repositorio") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Descripción") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { isPrivate = !isPrivate }
                ) {
                    Checkbox(checked = isPrivate, onCheckedChange = { isPrivate = it })
                    Text("Repositorio privado")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) onCreate(name.trim(), desc.trim(), isPrivate)
                }
            ) {
                Text("Crear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

private fun runStatusColor(status: String, conclusion: String?): Color = when {
    conclusion == "success" -> ForgeEmerald
    conclusion == "failure" || conclusion == "cancelled" -> ForgeCrimson
    status == "in_progress" -> ForgeAmber
    status == "queued" -> ForgeCyan
    else -> TextMuted
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) "${"%.2f".format(mb)} MB" else "${bytes / 1024} KB"
}
