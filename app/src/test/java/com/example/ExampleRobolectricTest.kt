package com.example

import android.content.Context
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ApplicationProvider
import com.example.data.ProjectTemplates
import com.example.data.WorkspaceRepository
import com.example.model.TemplateId
import com.example.ui.editor.CodeSyntaxVisualTransformation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read app name string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("CodeForge", appName)
  }

  @Test
  fun `templates generate real android and web files`() {
    val androidFiles = ProjectTemplates.buildFiles(TemplateId.ANDROID_STARTER, "DemoApp", "demoapp")
    assertTrue(androidFiles.containsKey(".github/workflows/build.yml"))
    assertTrue(androidFiles.containsKey("app/build.gradle.kts"))
    assertTrue(androidFiles.keys.any { it.endsWith("MainActivity.kt") })

    val webFiles = ProjectTemplates.buildFiles(TemplateId.WEB_MODERN_DASHBOARD, "WebDemo", "webdemo")
    assertTrue(webFiles.containsKey("index.html"))
    assertTrue(webFiles.containsKey("styles/main.css"))
    assertTrue(webFiles.containsKey("scripts/app.js"))
  }

  @Test
  fun `workspace repository seeds and edits files on disk`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = WorkspaceRepository(context)
    val projects = repo.loadOrSeedProjects()
    assertTrue(projects.isNotEmpty())
    val first = projects.first()
    assertTrue(repo.createFile(first.id, "notes/test.txt", "hola mundo"))
    assertEquals("hola mundo", repo.readFile(first.id, "notes/test.txt"))
  }

  @Test
  fun `syntax highlighter preserves exact character count`() {
    val sample = "fun main() { val x = 42 // comment\n println(\"hi\") }"
    val transformed = CodeSyntaxVisualTransformation("kt").filter(AnnotatedString(sample))
    assertEquals(sample, transformed.text.text)
  }
}
