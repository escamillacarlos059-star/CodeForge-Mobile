package com.example.ui.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.IdeUiState
import com.example.model.GitHubRepo
import com.example.model.IdeProject
import com.example.model.ProjectType
import com.example.model.TemplateId
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

@Composable
fun ProjectsScreen(
    state: IdeUiState,
    onSelectProject: (IdeProject) -> Unit,
    onCreateFromTemplate: (String, TemplateId) -> Unit,
    onDeleteProject: (String) -> Unit,
    onCloneGitHubRepo: (GitHubRepo) -> Unit,
    modifier: Modifier = Modifier
) {
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showCloneDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { showNewProjectDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("new_project_template_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Nueva Plantilla")
                }

                OutlinedButton(
                    onClick = { showCloneDialog = true },
                    enabled = state.githubRepos.isNotEmpty() && !state.isCloningRepo,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("clone_github_repo_button")
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Clonar GitHub")
                }
            }
        }

        if (state.isCloningRepo) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = state.cloneProgress,
                            fontFamily = JetBrainsMonoFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = ForgeCyan
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Plantillas Listas para Usar",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
        }

        items(TemplateId.entries) { template ->
            Card(
                onClick = { showNewProjectDialog = true },
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (template.type == ProjectType.ANDROID_COMPOSE) Icons.Default.Android else Icons.Default.Language,
                        contentDescription = null,
                        tint = if (template.type == ProjectType.ANDROID_COMPOSE) ForgeEmerald else ForgeCyan,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(template.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(template.subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Mis Proyectos Locales (${state.projects.size})",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
        }

        items(state.projects, key = { it.id }) { project ->
            val isActive = project.id == state.activeProject?.id
            val fileCount = state.fileCounts[project.id] ?: 0
            Card(
                onClick = { onSelectProject(project) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive) ObsidianSurfaceElevated else ObsidianSurface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isActive) ForgeCyan else ObsidianBorder
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (project.type == ProjectType.ANDROID_COMPOSE) Icons.Default.Android else Icons.Default.Language,
                        contentDescription = null,
                        tint = if (project.type == ProjectType.ANDROID_COMPOSE) ForgeEmerald else ForgeCyan,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = project.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            if (isActive) {
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = ForgeCyan.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "ACTIVO",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ForgeCyan,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "$fileCount archivos • ${project.type.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        if (!project.githubOwner.isNullOrBlank() && !project.githubRepo.isNullOrBlank()) {
                            Text(
                                text = "GitHub: ${project.githubOwner}/${project.githubRepo} (${project.githubBranch})",
                                fontFamily = JetBrainsMonoFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = ForgeEmerald
                            )
                        }
                    }

                    if (state.projects.size > 1) {
                        IconButton(onClick = { onDeleteProject(project.id) }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Eliminar proyecto",
                                tint = TextMuted
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewProjectDialog) {
        NewProjectTemplateDialog(
            onDismiss = { showNewProjectDialog = false },
            onCreate = { name, templateId ->
                showNewProjectDialog = false
                onCreateFromTemplate(name, templateId)
            }
        )
    }

    if (showCloneDialog) {
        CloneRepoDialog(
            repos = state.githubRepos,
            onDismiss = { showCloneDialog = false },
            onClone = { repo ->
                showCloneDialog = false
                onCloneGitHubRepo(repo)
            }
        )
    }
}

@Composable
private fun NewProjectTemplateDialog(
    onDismiss: () -> Unit,
    onCreate: (String, TemplateId) -> Unit
) {
    var projectName by remember { mutableStateOf("MiAppNueva") }
    var selectedTemplate by remember { mutableStateOf(TemplateId.ANDROID_STARTER) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear Proyecto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("Nombre del proyecto") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider(color = ObsidianBorder)
                TemplateId.entries.forEach { tpl ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTemplate = tpl }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedTemplate == tpl,
                            onClick = { selectedTemplate = tpl }
                        )
                        Column {
                            Text(tpl.title, style = MaterialTheme.typography.titleSmall)
                            Text(tpl.subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(projectName, selectedTemplate) }) {
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

@Composable
private fun CloneRepoDialog(
    repos: List<GitHubRepo>,
    onDismiss: () -> Unit,
    onClone: (GitHubRepo) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importar Repositorio de GitHub") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(repos, key = { it.id }) { repo ->
                    Surface(
                        onClick = { onClone(repo) },
                        color = ObsidianSurfaceElevated,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = repo.fullName,
                                fontFamily = JetBrainsMonoFamily,
                                style = MaterialTheme.typography.titleSmall,
                                color = ForgeCyan
                            )
                            Text(
                                text = "Rama: ${repo.defaultBranch} • ${if (repo.isPrivate) "Privado" else "Público"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}
