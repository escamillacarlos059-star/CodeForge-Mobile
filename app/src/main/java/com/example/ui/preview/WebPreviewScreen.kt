package com.example.ui.preview

import android.annotation.SuppressLint
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.IdeUiState
import com.example.model.LogCategory
import com.example.model.LogLevel
import com.example.model.ProjectType
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
import java.io.File
import java.io.FileInputStream

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LivePreviewScreen(
    state: IdeUiState,
    projectDir: File?,
    onReloadWeb: () -> Unit,
    onConsoleLog: (LogCategory, LogLevel, String, String) -> Unit,
    onEvaluateJs: (String) -> Unit,
    onConsumeJsEval: () -> Unit,
    onGoToGitHubBuild: () -> Unit,
    modifier: Modifier = Modifier
) {
    val project = state.activeProject
    if (project == null || projectDir == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Selecciona o crea un proyecto para previsualizar.", color = TextSecondary)
        }
        return
    }

    if (project.type == ProjectType.WEB_STATIC) {
        var mobileViewport by remember { mutableStateOf(false) }
        var jsCommand by remember { mutableStateOf("") }
        var webViewRef by remember { mutableStateOf<WebView?>(null) }

        LaunchedEffect(state.previewReloadTick, project.id) {
            webViewRef?.loadUrl("https://codeforge.local/index.html?t=${state.previewReloadTick}")
        }

        LaunchedEffect(state.pendingJsEval) {
            val script = state.pendingJsEval ?: return@LaunchedEffect
            webViewRef?.evaluateJavascript(script) { result ->
                if (!result.isNullOrBlank() && result != "null") {
                    onConsoleLog(LogCategory.WEB_CONSOLE, LogLevel.SUCCESS, "JS <", result)
                }
            }
            onConsumeJsEval()
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(ObsidianBg)
        ) {
            // Browser Preview Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianSurface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = ObsidianBg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "https://codeforge.local/index.html",
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = ForgeEmerald,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { mobileViewport = !mobileViewport }) {
                    Icon(
                        imageVector = if (mobileViewport) Icons.Default.PhoneAndroid else Icons.Default.DesktopWindows,
                        contentDescription = "Cambiar viewport",
                        tint = ForgeCyan
                    )
                }
                IconButton(
                    onClick = onReloadWeb,
                    modifier = Modifier.testTag("reload_web_preview_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Recargar vista previa",
                        tint = TextPrimary
                    )
                }
            }

            HorizontalDivider(color = ObsidianBorder)

            // Live WebView Surface
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(if (mobileViewport) 12.dp else 0.dp),
                contentAlignment = Alignment.Center
            ) {
                val frameMod = if (mobileViewport) {
                    Modifier
                        .widthIn(max = 390.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                        .border(2.dp, ObsidianBorder, RoundedCornerShape(18.dp))
                } else {
                    Modifier.fillMaxSize()
                }

                AndroidView(
                    modifier = frameMod.testTag("web_preview_webview"),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.cacheMode = WebSettings.LOAD_NO_CACHE

                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                                    val level = when (msg.messageLevel()) {
                                        ConsoleMessage.MessageLevel.ERROR -> LogLevel.ERROR
                                        ConsoleMessage.MessageLevel.WARNING -> LogLevel.WARN
                                        else -> LogLevel.INFO
                                    }
                                    onConsoleLog(
                                        LogCategory.WEB_CONSOLE,
                                        level,
                                        "WebView",
                                        "${msg.message()} (L${msg.lineNumber()})"
                                    )
                                    return true
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val url = request?.url ?: return null
                                    if (url.host != "codeforge.local") return super.shouldInterceptRequest(view, request)
                                    val relPath = url.path?.trimStart('/')?.ifBlank { "index.html" } ?: "index.html"
                                    val targetFile = File(projectDir, relPath).canonicalFile
                                    if (!targetFile.path.startsWith(projectDir.canonicalPath) || !targetFile.exists() || !targetFile.isFile) {
                                        return WebResourceResponse(
                                            "text/plain",
                                            "utf-8",
                                            404,
                                            "Not Found",
                                            emptyMap(),
                                            "Archivo no encontrado: $relPath".byteInputStream()
                                        )
                                    }
                                    return WebResourceResponse(
                                        guessMimeType(relPath),
                                        "utf-8",
                                        FileInputStream(targetFile)
                                    )
                                }
                            }

                            webViewRef = this
                            loadUrl("https://codeforge.local/index.html")
                        }
                    },
                    update = { webView ->
                        webViewRef = webView
                    }
                )
            }

            // Live JS Eval Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianSurface)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = jsCommand,
                    onValueChange = { jsCommand = it },
                    placeholder = { Text("Ejecutar JavaScript en vivo (ej. document.title)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        if (jsCommand.isNotBlank()) {
                            onEvaluateJs(jsCommand)
                            jsCommand = ""
                        }
                    }
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Ejecutar JS", tint = ForgeCyan)
                }
            }
        }
    } else {
        // Android Compose Project Inspector + Direct Jump to Real GitHub Actions APK/AAB Compiler
        val gradleBuildFile = remember(projectDir, state.previewReloadTick) {
            File(projectDir, "app/build.gradle.kts").takeIf { it.exists() }?.readText().orEmpty()
        }
        val workflowName = remember(projectDir, state.previewReloadTick) {
            when {
                File(projectDir, ".github/workflows/build.yml").exists() -> ".github/workflows/build.yml (Listo)"
                File(projectDir, ".github/workflows/android-build.yml").exists() -> ".github/workflows/android-build.yml (Listo)"
                else -> "Falta workflow"
            }
        }
        val appId = remember(gradleBuildFile) {
            Regex("""applicationId\s*=\s*"([^"]+)"""").find(gradleBuildFile)?.groupValues?.getOrNull(1)
                ?: "com.codeforge.myapp"
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(ObsidianBg)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            tint = ForgeEmerald,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(project.name, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                            Text("Proyecto nativo Android Kotlin + Jetpack Compose", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                    HorizontalDivider(color = ObsidianBorder)
                    InfoRow("Application ID", appId)
                    InfoRow("Workflow CI/CD", workflowName)
                    InfoRow("Salidas de compilación", "app-debug.apk + app-release.aab")
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Compilación Real en la Nube", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        text = "Las apps Android Kotlin/Compose se compilan con el Android SDK + Gradle real en GitHub Actions. Al terminar, podrás descargar e instalar el app-debug.apk directamente en este teléfono.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    ChecklistItem("Proyecto Gradle Kotlin DSL configurado")
                    ChecklistItem("GitHub Actions con JDK 17 + Android SDK + Gradle 8.11.1")
                    ChecklistItem("Instalador nativo Android integrado al finalizar")
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = onGoToGitHubBuild,
                        modifier = Modifier.fillMaxWidth().testTag("preview_go_github_build")
                    ) {
                        Icon(Icons.Default.RocketLaunch, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Compilar APK y AAB en GitHub")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Text(value, fontFamily = JetBrainsMonoFamily, style = MaterialTheme.typography.bodySmall, color = ForgeCyan)
    }
}

@Composable
private fun ChecklistItem(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ForgeEmerald, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
    }
}

private fun guessMimeType(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "html", "htm" -> "text/html"
    "css" -> "text/css"
    "js" -> "application/javascript"
    "json" -> "application/json"
    "svg" -> "image/svg+xml"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    else -> "text/plain"
}
