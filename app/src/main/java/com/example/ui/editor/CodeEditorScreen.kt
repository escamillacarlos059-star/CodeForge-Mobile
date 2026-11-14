package com.example.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.IdeUiState
import com.example.model.ProjectType
import com.example.ui.theme.ForgeAmber
import com.example.ui.theme.ForgeCyan
import com.example.ui.theme.ForgeEmerald
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianEditorGutter
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun CodeEditorScreen(
    state: IdeUiState,
    showExplorerToggle: Boolean,
    onOpenExplorer: () -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onUpdateContent: (String) -> Unit,
    onInsertSnippet: (String) -> Unit,
    onSaveActive: () -> Unit,
    onAdjustFontSize: (Int) -> Unit,
    onToggleWordWrap: () -> Unit,
    onRunOrPreview: () -> Unit,
    onPushAndBuild: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showFindBar by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    val activeTab = state.activeTab

    Column(modifier = modifier.fillMaxSize().background(ObsidianBg)) {
        // IDE Workbench Top Action Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianSurface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showExplorerToggle) {
                IconButton(
                    onClick = onOpenExplorer,
                    modifier = Modifier.testTag("open_explorer_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Explorador de archivos",
                        tint = ForgeAmber
                    )
                }
            }

            Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    text = state.activeProject?.name ?: "CodeForge",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = activeTab?.relativePath ?: "Selecciona un archivo",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onSaveActive,
                enabled = activeTab != null,
                modifier = Modifier.testTag("save_file_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "Guardar archivo",
                    tint = if (activeTab?.isDirty == true) ForgeEmerald else TextSecondary
                )
            }

            IconButton(onClick = { showFindBar = !showFindBar }) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Buscar",
                    tint = if (showFindBar) ForgeCyan else TextSecondary
                )
            }

            // Primary Run / Build action pill
            Surface(
                onClick = if (state.activeProject?.type == ProjectType.WEB_STATIC) onRunOrPreview else onPushAndBuild,
                color = if (state.activeProject?.type == ProjectType.WEB_STATIC) {
                    ForgeEmerald.copy(alpha = 0.16f)
                } else {
                    ForgeCyan.copy(alpha = 0.16f)
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("toolbar_run_build_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (state.activeProject?.type == ProjectType.WEB_STATIC) {
                            Icons.Default.PlayArrow
                        } else {
                            Icons.Default.RocketLaunch
                        },
                        contentDescription = null,
                        tint = if (state.activeProject?.type == ProjectType.WEB_STATIC) ForgeEmerald else ForgeCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (state.activeProject?.type == ProjectType.WEB_STATIC) "Preview" else "Compilar",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (state.activeProject?.type == ProjectType.WEB_STATIC) ForgeEmerald else ForgeCyan
                    )
                }
            }
        }

        // Open Tabs Bar
        if (state.openTabs.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianSurface)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(state.openTabs, key = { it.relativePath }) { tab ->
                    val isActive = tab.relativePath == state.activeTabPath
                    Surface(
                        color = if (isActive) ObsidianSurfaceElevated else ObsidianBg,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isActive) ForgeCyan.copy(alpha = 0.55f) else ObsidianBorder
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectTab(tab.relativePath) }
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FileExtensionChip(fileName = tab.name)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = tab.name,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 12.sp,
                                color = if (isActive) TextPrimary else TextSecondary
                            )
                            if (tab.isDirty) {
                                Spacer(Modifier.width(5.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(ForgeAmber)
                                )
                            }
                            IconButton(
                                onClick = { onCloseTab(tab.relativePath) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cerrar pestaña",
                                    tint = TextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = showFindBar) {
            val matchCount = remember(activeTab?.content, findQuery) {
                val text = activeTab?.content.orEmpty()
                if (findQuery.isBlank()) 0 else Regex.escape(findQuery).toRegex(RegexOption.IGNORE_CASE).findAll(text).count()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianSurfaceElevated)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = findQuery,
                    onValueChange = { findQuery = it },
                    placeholder = { Text("Buscar en el archivo actual...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$matchCount coef.",
                    style = MaterialTheme.typography.labelSmall,
                    color = ForgeCyan
                )
            }
        }

        HorizontalDivider(color = ObsidianBorder)

        // Editor Surface
        if (activeTab == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = ForgeCyan.copy(alpha = 0.6f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Abre un archivo desde el Explorador",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary
                    )
                }
            }
        } else {
            val vScroll = rememberScrollState()
            val hScroll = rememberScrollState()
            val lines = remember(activeTab.content) {
                activeTab.content.split('\n').size.coerceAtLeast(1)
            }
            val ext = remember(activeTab.name) {
                activeTab.name.substringAfterLast('.', "")
            }
            val visualTransformation = remember(ext) {
                CodeSyntaxVisualTransformation(ext)
            }

            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Mini editor controls bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianEditorGutter)
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$lines líneas • ${ext.uppercase().ifBlank { "TXT" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { onAdjustFontSize(-1) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.TextDecrease, "Reducir fuente", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                    Text(
                        text = "${state.editorFontSizeSp}sp",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    IconButton(
                        onClick = { onAdjustFontSize(1) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.TextIncrease, "Aumentar fuente", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = onToggleWordWrap,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.WrapText,
                            "Ajuste de línea",
                            tint = if (state.wordWrap) ForgeCyan else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Gutter + Code Field
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(vScroll)
                ) {
                    // Line numbers gutter
                    Column(
                        modifier = Modifier
                            .background(ObsidianEditorGutter)
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        val lineHeightSp = (state.editorFontSizeSp + 6).sp
                        for (lineNum in 1..lines) {
                            Text(
                                text = lineNum.toString(),
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = state.editorFontSizeSp.sp,
                                lineHeight = lineHeightSp,
                                color = TextMuted
                            )
                        }
                    }

                    // Code input
                    val editorModifier = if (state.wordWrap) {
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                    } else {
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .horizontalScroll(hScroll)
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                    }

                    BasicTextField(
                        value = activeTab.content,
                        onValueChange = onUpdateContent,
                        textStyle = TextStyle(
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = state.editorFontSizeSp.sp,
                            lineHeight = (state.editorFontSizeSp + 6).sp,
                            color = TextPrimary
                        ),
                        cursorBrush = SolidColor(ForgeCyan),
                        visualTransformation = visualTransformation,
                        modifier = editorModifier.testTag("code_editor_textfield")
                    )
                }

                // Quick Symbol Accessory Strip
                val quickSymbols = remember {
                    listOf("    " to "TAB", "{" to "{", "}" to "}", "(" to "(", ")" to ")", "<" to "<", ">" to ">", "=" to "=", "\"" to "\"", ";" to ";", "/" to "/")
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianSurface)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickSymbols.forEach { (value, label) ->
                        Surface(
                            onClick = { onInsertSnippet(value) },
                            color = ObsidianSurfaceElevated,
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                        ) {
                            Text(
                                text = label,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 12.sp,
                                color = ForgeCyan,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
