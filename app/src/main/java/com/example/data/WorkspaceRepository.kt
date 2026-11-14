package com.example.data

import android.content.Context
import com.example.model.FileNode
import com.example.model.IdeProject
import com.example.model.ProjectType
import com.example.model.TemplateId
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@JsonClass(generateAdapter = true)
data class ProjectIndexWrapper(
    val projects: List<IdeProject>
)

class WorkspaceRepository(private val context: Context) {

    private val workspacesRoot: File = File(context.filesDir, "workspaces").apply { mkdirs() }
    private val indexFile: File = File(workspacesRoot, "projects_index.json")

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val indexAdapter = moshi.adapter(ProjectIndexWrapper::class.java)

    suspend fun loadOrSeedProjects(): List<IdeProject> = withContext(Dispatchers.IO) {
        if (!indexFile.exists()) {
            val androidDemo = createProjectInternal(
                name = "MiAppCompose",
                templateId = TemplateId.ANDROID_STARTER,
                description = "App Android Kotlin + Jetpack Compose con workflow APK/AAB"
            )
            val webDemo = createProjectInternal(
                name = "PulseWebApp",
                templateId = TemplateId.WEB_MODERN_DASHBOARD,
                description = "Web HTML5 + CSS3 + JS con vista previa en vivo"
            )
            val initial = listOf(androidDemo, webDemo)
            saveIndex(initial)
            return@withContext initial
        }
        val raw = runCatching { indexFile.readText() }.getOrNull().orEmpty()
        val parsed = runCatching { indexAdapter.fromJson(raw)?.projects }.getOrNull()
        if (parsed.isNullOrEmpty()) {
            val fallback = createProjectInternal(
                name = "MiAppCompose",
                templateId = TemplateId.ANDROID_STARTER,
                description = "App Android Kotlin + Jetpack Compose"
            )
            saveIndex(listOf(fallback))
            return@withContext listOf(fallback)
        }
        parsed.sortedByDescending { it.lastModified }
    }

    suspend fun createProjectFromTemplate(
        name: String,
        templateId: TemplateId
    ): IdeProject = withContext(Dispatchers.IO) {
        val current = loadOrSeedProjects().toMutableList()
        val created = createProjectInternal(
            name = name.trim().ifBlank { templateId.title },
            templateId = templateId,
            description = templateId.subtitle
        )
        current.add(0, created)
        saveIndex(current)
        created
    }

    suspend fun createProjectFromRemoteFiles(
        repoName: String,
        owner: String,
        branch: String,
        files: Map<String, String>
    ): IdeProject = withContext(Dispatchers.IO) {
        val current = loadOrSeedProjects().toMutableList()
        val id = "proj_${UUID.randomUUID().toString().take(8)}"
        val dir = projectDir(id).apply { mkdirs() }
        files.forEach { (relPath, content) ->
            val target = safeResolve(dir, relPath) ?: return@forEach
            target.parentFile?.mkdirs()
            target.writeText(content)
        }
        val detectedType = if (
            files.keys.any { it.endsWith("build.gradle.kts") || it.endsWith("build.gradle") || it.endsWith("AndroidManifest.xml") }
        ) {
            ProjectType.ANDROID_COMPOSE
        } else {
            ProjectType.WEB_STATIC
        }
        val project = IdeProject(
            id = id,
            name = repoName,
            type = detectedType,
            description = "Importado desde GitHub ($owner/$repoName)",
            githubOwner = owner,
            githubRepo = repoName,
            githubBranch = branch,
            lastModified = System.currentTimeMillis()
        )
        current.add(0, project)
        saveIndex(current)
        project
    }

    suspend fun updateProjectLink(
        projectId: String,
        owner: String,
        repo: String,
        branch: String
    ): List<IdeProject> = withContext(Dispatchers.IO) {
        val updated = loadOrSeedProjects().map {
            if (it.id == projectId) {
                it.copy(
                    githubOwner = owner,
                    githubRepo = repo,
                    githubBranch = branch,
                    lastModified = System.currentTimeMillis()
                )
            } else {
                it
            }
        }
        saveIndex(updated)
        updated
    }

    suspend fun deleteProject(projectId: String): List<IdeProject> = withContext(Dispatchers.IO) {
        val remaining = loadOrSeedProjects().filterNot { it.id == projectId }
        projectDir(projectId).deleteRecursively()
        saveIndex(remaining)
        if (remaining.isEmpty()) loadOrSeedProjects() else remaining
    }

    fun getProjectDir(projectId: String): File = projectDir(projectId)

    suspend fun buildFileTree(projectId: String): List<FileNode> = withContext(Dispatchers.IO) {
        val root = projectDir(projectId)
        if (!root.exists()) return@withContext emptyList()
        readDirectoryChildren(root, root)
    }

    suspend fun countFiles(projectId: String): Int = withContext(Dispatchers.IO) {
        val root = projectDir(projectId)
        if (!root.exists()) 0 else root.walkTopDown().count { it.isFile }
    }

    suspend fun readFile(projectId: String, relativePath: String): String = withContext(Dispatchers.IO) {
        val file = safeResolve(projectDir(projectId), relativePath) ?: return@withContext ""
        if (!file.exists() || !file.isFile) return@withContext ""
        runCatching { file.readText() }.getOrDefault("")
    }

    suspend fun writeFile(projectId: String, relativePath: String, content: String): Boolean =
        withContext(Dispatchers.IO) {
            val file = safeResolve(projectDir(projectId), relativePath) ?: return@withContext false
            file.parentFile?.mkdirs()
            file.writeText(content)
            touchProject(projectId)
            true
        }

    suspend fun createFile(projectId: String, relativePath: String, initialContent: String = ""): Boolean =
        withContext(Dispatchers.IO) {
            val clean = relativePath.trim().trimStart('/')
            if (clean.isBlank()) return@withContext false
            val file = safeResolve(projectDir(projectId), clean) ?: return@withContext false
            if (file.exists()) return@withContext false
            file.parentFile?.mkdirs()
            file.writeText(initialContent)
            touchProject(projectId)
            true
        }

    suspend fun createFolder(projectId: String, relativePath: String): Boolean =
        withContext(Dispatchers.IO) {
            val clean = relativePath.trim().trimStart('/')
            if (clean.isBlank()) return@withContext false
            val dir = safeResolve(projectDir(projectId), clean) ?: return@withContext false
            if (dir.exists()) return@withContext false
            val created = dir.mkdirs()
            if (created) touchProject(projectId)
            created
        }

    suspend fun renameNode(projectId: String, oldRelativePath: String, newRelativePath: String): Boolean =
        withContext(Dispatchers.IO) {
            val root = projectDir(projectId)
            val oldFile = safeResolve(root, oldRelativePath) ?: return@withContext false
            val newFile = safeResolve(root, newRelativePath.trim().trimStart('/')) ?: return@withContext false
            if (!oldFile.exists() || newFile.exists()) return@withContext false
            newFile.parentFile?.mkdirs()
            val ok = oldFile.renameTo(newFile)
            if (ok) touchProject(projectId)
            ok
        }

    suspend fun deleteNode(projectId: String, relativePath: String): Boolean =
        withContext(Dispatchers.IO) {
            val file = safeResolve(projectDir(projectId), relativePath) ?: return@withContext false
            if (!file.exists()) return@withContext false
            val ok = if (file.isDirectory) file.deleteRecursively() else file.delete()
            if (ok) touchProject(projectId)
            ok
        }

    suspend fun collectAllFilesForPush(projectId: String): List<Pair<String, ByteArray>> =
        withContext(Dispatchers.IO) {
            val root = projectDir(projectId)
            if (!root.exists()) return@withContext emptyList()
            root.walkTopDown()
                .filter { it.isFile }
                .map { file ->
                    val rel = file.relativeTo(root).invariantSeparatorsPath
                    rel to file.readBytes()
                }
                .sortedBy { it.first }
                .toList()
        }

    fun pickDefaultEntryFile(project: IdeProject, tree: List<FileNode>): String? {
        val allPaths = flattenPaths(tree)
        return when (project.type) {
            ProjectType.WEB_STATIC -> {
                allPaths.firstOrNull { it.equals("index.html", ignoreCase = true) }
                    ?: allPaths.firstOrNull { it.endsWith(".html") }
                    ?: allPaths.firstOrNull()
            }
            ProjectType.ANDROID_COMPOSE -> {
                allPaths.firstOrNull { it.endsWith("MainActivity.kt") }
                    ?: allPaths.firstOrNull { it.endsWith(".kt") }
                    ?: allPaths.firstOrNull { it.endsWith("build.gradle.kts") }
                    ?: allPaths.firstOrNull()
            }
        }
    }

    private fun flattenPaths(nodes: List<FileNode>): List<String> {
        val out = mutableListOf<String>()
        nodes.forEach { node ->
            if (node.isDirectory) {
                out.addAll(flattenPaths(node.children))
            } else {
                out.add(node.relativePath)
            }
        }
        return out
    }

    private fun createProjectInternal(
        name: String,
        templateId: TemplateId,
        description: String
    ): IdeProject {
        val id = "proj_${UUID.randomUUID().toString().take(8)}"
        val dir = projectDir(id).apply { mkdirs() }
        val files = ProjectTemplates.buildFiles(templateId, name, name)
        files.forEach { (relPath, content) ->
            val target = safeResolve(dir, relPath) ?: return@forEach
            target.parentFile?.mkdirs()
            target.writeText(content)
        }
        return IdeProject(
            id = id,
            name = name,
            type = templateId.type,
            description = description,
            lastModified = System.currentTimeMillis()
        )
    }

    private fun readDirectoryChildren(root: File, dir: File): List<FileNode> {
        val items = dir.listFiles()?.toList().orEmpty()
        return items
            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            .map { file ->
                val rel = file.relativeTo(root).invariantSeparatorsPath
                if (file.isDirectory) {
                    FileNode(
                        name = file.name,
                        relativePath = rel,
                        isDirectory = true,
                        children = readDirectoryChildren(root, file)
                    )
                } else {
                    FileNode(
                        name = file.name,
                        relativePath = rel,
                        isDirectory = false,
                        sizeBytes = file.length()
                    )
                }
            }
    }

    private fun touchProject(projectId: String) {
        val raw = runCatching { indexFile.readText() }.getOrNull() ?: return
        val current = runCatching { indexAdapter.fromJson(raw)?.projects }.getOrNull() ?: return
        val updated = current.map {
            if (it.id == projectId) it.copy(lastModified = System.currentTimeMillis()) else it
        }
        saveIndex(updated)
    }

    private fun saveIndex(projects: List<IdeProject>) {
        val json = indexAdapter.toJson(ProjectIndexWrapper(projects))
        indexFile.writeText(json)
    }

    private fun projectDir(projectId: String): File = File(workspacesRoot, projectId)

    private fun safeResolve(root: File, relativePath: String): File? {
        val clean = relativePath.trimStart('/')
        if (clean.isBlank()) return null
        val target = File(root, clean).canonicalFile
        val canonicalRoot = root.canonicalFile
        return if (target.path.startsWith(canonicalRoot.path + File.separator) || target == canonicalRoot) {
            target
        } else {
            null
        }
    }
}
