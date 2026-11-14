package com.example.data

import com.example.model.TemplateId

object ProjectTemplates {

    fun buildFiles(templateId: TemplateId, appTitle: String, packageSuffix: String): Map<String, String> {
        val safePkg = packageSuffix.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "myapp" }
        val pkgName = "com.codeforge.$safePkg"
        val pkgPath = pkgName.replace('.', '/')

        return when (templateId) {
            TemplateId.ANDROID_STARTER -> androidStarterFiles(appTitle, pkgName, pkgPath)
            TemplateId.ANDROID_CANVAS_LAB -> androidCanvasFiles(appTitle, pkgName, pkgPath)
            TemplateId.WEB_MODERN_DASHBOARD -> webDashboardFiles(appTitle)
        }
    }

    private fun androidWorkflowYml(appTitle: String): String = """
name: Build Android APK & AAB ($appTitle)

on:
  push:
    branches: [ main, master ]
  workflow_dispatch:

jobs:
  build:
    name: Compilar APK & AAB
    runs-on: ubuntu-latest

    steps:
      - name: Checkout del código
        uses: actions/checkout@v4

      - name: Configurar JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Configurar Android SDK
        uses: android-actions/setup-android@v3

      - name: Instalar SDK 35 y aceptar licencias
        run: |
          SDK_DIR="${'$'}{ANDROID_HOME:-${'$'}ANDROID_SDK_ROOT}"
          echo "sdk.dir=${'$'}SDK_DIR" > local.properties
          yes | sdkmanager --licenses > /dev/null || true
          sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

      - name: Configurar Gradle 8.11.1
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '8.11.1'

      - name: Compilar Debug APK y Release AAB
        run: gradle :app:assembleDebug :app:bundleRelease --no-daemon --stacktrace

      - name: Subir app-debug.apk
        uses: actions/upload-artifact@v4
        with:
          name: app-debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
          if-no-files-found: error

      - name: Subir app-release.aab
        uses: actions/upload-artifact@v4
        with:
          name: app-release-aab
          path: app/build/outputs/bundle/release/app-release.aab
          if-no-files-found: error
""".trimStart()

    private fun commonAndroidGradleFiles(appTitle: String, pkgName: String): Map<String, String> = mapOf(
        ".github/workflows/build.yml" to androidWorkflowYml(appTitle),
        "gradle.properties" to """
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
""".trimStart(),
        "settings.gradle.kts" to """
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "${appTitle.replace("\"", "")}"
include(":app")
""".trimStart(),
        "build.gradle.kts" to """
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
""".trimStart(),
        "gradle/libs.versions.toml" to """
[versions]
agp = "8.7.3"
kotlin = "2.0.21"
coreKtx = "1.15.0"
lifecycleRuntimeKtx = "2.8.7"
activityCompose = "1.9.3"
composeBom = "2024.10.01"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
""".trimStart(),
        "app/build.gradle.kts" to """
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "$pkgName"
    compileSdk = 35

    defaultConfig {
        applicationId = "$pkgName"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
}
""".trimStart(),
        "app/src/main/AndroidManifest.xml" to """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:icon="@drawable/ic_app_icon"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
""".trimStart(),
        "app/src/main/res/values/strings.xml" to """
<resources>
    <string name="app_name">$appTitle</string>
</resources>
""".trimStart(),
        "app/src/main/res/drawable/ic_app_icon.xml" to """
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#0F172A"
        android:pathData="M0,0h108v108h-108z" />
    <path
        android:fillColor="#38BDF8"
        android:pathData="M54,24L78,54L54,84L30,54Z" />
    <path
        android:fillColor="#34D399"
        android:pathData="M54,36L68,54L54,72L40,54Z" />
</vector>
""".trimStart()
    )

    private fun androidStarterFiles(appTitle: String, pkgName: String, pkgPath: String): Map<String, String> {
        val base = commonAndroidGradleFiles(appTitle, pkgName).toMutableMap()
        base["app/src/main/java/$pkgPath/MainActivity.kt"] = """
package $pkgName

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class TaskItem(val id: Int, val title: String, val done: Boolean = false)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF38BDF8),
                    secondary = Color(0xFF34D399),
                    background = Color(0xFF0B0F17),
                    surface = Color(0xFF111827)
                )
            ) {
                AppScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen() {
    var tasks by remember {
        mutableStateOf(
            listOf(
                TaskItem(1, "Compilada con GitHub Actions", true),
                TaskItem(2, "Instalada directo en mi Android", false)
            )
        )
    }
    var newTaskText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "$appTitle",
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newTaskText,
                    onValueChange = { newTaskText = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Nueva tarea") },
                    singleLine = true
                )
                FilledIconButton(
                    onClick = {
                        if (newTaskText.isNotBlank()) {
                            val nextId = (tasks.maxOfOrNull { it.id } ?: 0) + 1
                            tasks = tasks + TaskItem(nextId, newTaskText.trim())
                            newTaskText = ""
                        }
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Agregar")
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tasks, key = { it.id }) { task ->
                    ElevatedCard(
                        onClick = {
                            tasks = tasks.map {
                                if (it.id == task.id) it.copy(done = !it.done) else it
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (task.done) Color(0xFF34D399) else Color(0xFF64748B)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = task.title,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { tasks = tasks.filterNot { it.id == task.id } }) {
                                Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                            }
                        }
                    }
                }
            }
        }
    }
}
""".trimStart()
        base["README.md"] = """
# $appTitle

Proyecto Android Kotlin + Jetpack Compose creado desde **CodeForge**.

- Compilación automática en GitHub Actions (`assembleDebug` + `bundleRelease`).
- Artefactos generados: `app-debug.apk` y `app-release.aab`.
""".trimStart()
        return base
    }

    private fun androidCanvasFiles(appTitle: String, pkgName: String, pkgPath: String): Map<String, String> {
        val base = commonAndroidGradleFiles(appTitle, pkgName).toMutableMap()
        base["app/src/main/java/$pkgPath/MainActivity.kt"] = """
package $pkgName

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF38BDF8),
                    background = Color(0xFF070B14),
                    surface = Color(0xFF0F172A)
                )
            ) {
                NebulaCanvasScreen()
            }
        }
    }
}

@Composable
fun NebulaCanvasScreen() {
    var time by remember { mutableFloatStateOf(0f) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var speed by remember { mutableFloatStateOf(1.2f) }

    LaunchedEffect(speed) {
        while (true) {
            withFrameNanos {
                time += 0.025f * speed
            }
        }
    }

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            pointer = change.position
                        }
                    }
            ) {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF070B14), Color(0xFF0F172A))
                    )
                )
                val center = if (pointer == Offset.Zero) Offset(size.width / 2f, size.height / 2f) else pointer
                val count = 36
                for (i in 0 until count) {
                    val angle = time + i * (Math.PI * 2.0 / count).toFloat()
                    val radius = 120f + 75f * sin(time * 1.4f + i * 0.4f)
                    val px = center.x + cos(angle) * radius
                    val py = center.y + sin(angle) * radius
                    val dotColor = if (i % 2 == 0) Color(0xFF38BDF8) else Color(0xFF34D399)
                    drawCircle(
                        color = dotColor.copy(alpha = 0.85f),
                        radius = 10f + (i % 4) * 3f,
                        center = Offset(px, py)
                    )
                }
            }

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("$appTitle — Canvas Lab", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Velocidad orbital: ${'$'}{"%.1f".format(speed)}x")
                    Slider(
                        value = speed,
                        onValueChange = { speed = it },
                        valueRange = 0.2f..3.5f
                    )
                }
            }
        }
    }
}
""".trimStart()
        base["README.md"] = """
# $appTitle

App Android interactiva con Jetpack Compose Canvas lista para compilar en GitHub Actions.
""".trimStart()
        return base
    }

    private fun webDashboardFiles(appTitle: String): Map<String, String> = mapOf(
        "index.html" to """
<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>$appTitle</title>
  <link rel="stylesheet" href="styles/main.css" />
</head>
<body>
  <main class="shell">
    <header class="topbar">
      <div>
        <span class="badge">LIVE WEB PREVIEW</span>
        <h1>$appTitle</h1>
      </div>
      <button id="themeBtn" class="btn-ghost">Alternar Energía</button>
    </header>

    <section class="metrics">
      <article class="card">
        <span class="label">Sesiones Hoy</span>
        <strong id="counterVal" class="value">0</strong>
      </article>
      <article class="card">
        <span class="label">Estado</span>
        <strong id="statusVal" class="value accent">Activo</strong>
      </article>
    </section>

    <section class="panel">
      <h2>Notas Rápidas</h2>
      <form id="noteForm" class="row">
        <input id="noteInput" type="text" placeholder="Escribe una idea o comando..." required />
        <button type="submit" class="btn-primary">Agregar</button>
      </form>
      <ul id="noteList" class="list"></ul>
    </section>
  </main>

  <script src="scripts/app.js"></script>
</body>
</html>
""".trimStart(),
        "styles/main.css" to """
:root {
  --bg: #0b0f17;
  --surface: #111827;
  --border: #243247;
  --accent: #38bdf8;
  --emerald: #34d399;
  --text: #f1f5f9;
  --muted: #94a3b8;
}

* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

body {
  font-family: system-ui, -apple-system, sans-serif;
  background: radial-gradient(circle at top right, #0c233c, var(--bg) 55%);
  color: var(--text);
  min-height: 100vh;
  padding: 20px;
}

.shell {
  max-width: 680px;
  margin: 0 auto;
  display: grid;
  gap: 16px;
}

.topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.badge {
  display: inline-block;
  font-size: 11px;
  letter-spacing: 0.08em;
  color: var(--emerald);
  background: rgba(52, 211, 153, 0.14);
  padding: 4px 10px;
  border-radius: 999px;
  margin-bottom: 6px;
}

h1 {
  font-size: 1.5rem;
}

.metrics {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.card, .panel {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: 16px;
}

.label {
  display: block;
  font-size: 12px;
  color: var(--muted);
}

.value {
  display: block;
  margin-top: 6px;
  font-size: 1.6rem;
}

.accent {
  color: var(--accent);
}

.row {
  display: flex;
  gap: 8px;
  margin: 12px 0;
}

input {
  flex: 1;
  background: #0b0f17;
  border: 1px solid var(--border);
  border-radius: 10px;
  color: var(--text);
  padding: 10px 12px;
  font-size: 14px;
}

button {
  border: none;
  border-radius: 10px;
  padding: 10px 14px;
  font-weight: 600;
  cursor: pointer;
}

.btn-primary {
  background: var(--accent);
  color: #082f49;
}

.btn-ghost {
  background: rgba(56, 189, 248, 0.14);
  color: var(--accent);
}

.list {
  list-style: none;
  display: grid;
  gap: 8px;
}

.list li {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #0e1522;
  border: 1px solid var(--border);
  padding: 10px 12px;
  border-radius: 10px;
  font-size: 14px;
}
""".trimStart(),
        "scripts/app.js" to """
const counterEl = document.getElementById('counterVal');
const statusEl = document.getElementById('statusVal');
const noteForm = document.getElementById('noteForm');
const noteInput = document.getElementById('noteInput');
const noteList = document.getElementById('noteList');
const themeBtn = document.getElementById('themeBtn');

let count = Number(localStorage.getItem('cf_count') || 1);
let notes = JSON.parse(localStorage.getItem('cf_notes') || '["Vista previa en vivo funcionando","Editar HTML/CSS/JS y recargar"]');

function render() {
  counterEl.textContent = count;
  noteList.innerHTML = '';
  notes.forEach((text, idx) => {
    const li = document.createElement('li');
    li.innerHTML = `<span>${'$'}{text}</span><button class="btn-ghost" data-idx="${'$'}{idx}">✕</button>`;
    li.querySelector('button').addEventListener('click', () => {
      notes.splice(idx, 1);
      saveAndRender();
    });
    noteList.appendChild(li);
  });
}

function saveAndRender() {
  localStorage.setItem('cf_count', String(count));
  localStorage.setItem('cf_notes', JSON.stringify(notes));
  render();
}

noteForm.addEventListener('submit', (e) => {
  e.preventDefault();
  const value = noteInput.value.trim();
  if (!value) return;
  notes.unshift(value);
  count += 1;
  noteInput.value = '';
  console.log('Nota agregada:', value);
  saveAndRender();
});

themeBtn.addEventListener('click', () => {
  count += 1;
  statusEl.textContent = count % 2 === 0 ? 'Turbo' : 'Activo';
  console.log('Energía alternada. Sesiones =', count);
  saveAndRender();
});

console.log('PulseWebApp iniciada correctamente');
render();
""".trimStart(),
        "README.md" to """
# $appTitle

Aplicación Web HTML5 + CSS3 + JavaScript con vista previa en vivo en **CodeForge**.
""".trimStart()
    )
}
