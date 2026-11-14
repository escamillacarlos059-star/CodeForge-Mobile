package com.example.model

import com.squareup.moshi.JsonClass

enum class ProjectType(val label: String) {
    ANDROID_COMPOSE("Android Kotlin / Compose"),
    WEB_STATIC("Web HTML / CSS / JS")
}

enum class TemplateId(
    val title: String,
    val subtitle: String,
    val type: ProjectType
) {
    ANDROID_STARTER(
        title = "Android Kotlin + Compose",
        subtitle = "Proyecto Gradle completo con Material 3 y workflow de GitHub Actions para APK y AAB.",
        type = ProjectType.ANDROID_COMPOSE
    ),
    ANDROID_CANVAS_LAB(
        title = "Android Canvas Interactivo",
        subtitle = "App Android animada con física de partículas en Compose Canvas lista para compilar APK/AAB.",
        type = ProjectType.ANDROID_COMPOSE
    ),
    WEB_MODERN_DASHBOARD(
        title = "Web App HTML / CSS / JS",
        subtitle = "Aplicación web multipantalla con vista previa en vivo, LocalStorage y consola JS real.",
        type = ProjectType.WEB_STATIC
    )
}

@JsonClass(generateAdapter = true)
data class IdeProject(
    val id: String,
    val name: String,
    val type: ProjectType,
    val description: String,
    val githubOwner: String? = null,
    val githubRepo: String? = null,
    val githubBranch: String = "main",
    val lastModified: Long = System.currentTimeMillis()
)

data class FileNode(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val children: List<FileNode> = emptyList(),
    val sizeBytes: Long = 0L
)

data class OpenTab(
    val relativePath: String,
    val name: String,
    val content: String,
    val savedContent: String = content
) {
    val isDirty: Boolean get() = content != savedContent
}

enum class LogCategory(val label: String) {
    ALL("Todo"),
    SYSTEM("Sistema"),
    WEB_CONSOLE("Consola JS"),
    GITHUB_API("GitHub API"),
    BUILD("Compilación")
}

enum class LogLevel {
    INFO,
    SUCCESS,
    WARN,
    ERROR
}

data class ConsoleEntry(
    val id: Long,
    val timestamp: String,
    val category: LogCategory,
    val level: LogLevel,
    val tag: String,
    val message: String
)

enum class IdeDestination(val label: String) {
    EDITOR("Editor"),
    PREVIEW("Preview"),
    GITHUB_CI("GitHub CI"),
    PROJECTS("Proyectos")
}
