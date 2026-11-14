package com.example.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FileNode
import com.example.model.IdeProject
import com.example.ui.theme.ForgeAmber
import com.example.ui.theme.ForgeCyan
import com.example.ui.theme.ForgeEmerald
import com.example.ui.theme.ForgeViolet
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun FileExplorerPanel(
    project: IdeProject?,
    fileTree: List<FileNode>,
    expandedFolders: Set<String>,
    activeFilePath: String?,
    onToggleFolder: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onCreateFile: (String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameNode: (String, String) -> Unit,
    onDeleteNode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateFileDialog by remember { mutableStateOf<String?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<FileNode?>(null) }
    var deleteTarget by remember { mutableStateOf<FileNode?>(null) }

    val flattenedVisibleNodes = remember(fileTree, expandedFolders) {
        buildList {
            fun traverse(nodes: List<FileNode>, depth: Int) {
                nodes.forEach { node ->
                    add(node to depth)
                    if (node.isDirectory && node.relativePath in expandedFolders) {
                        traverse(node.children, depth + 1)
                    }
                }
            }
            traverse(fileTree, 0)
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(ObsidianSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "EXPLORADOR",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )
                Text(
                    text = project?.name ?: "Sin proyecto",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = { showCreateFileDialog = "" },
                modifier = Modifier.testTag("explorer_new_file_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Nuevo archivo",
                    tint = ForgeCyan
                )
            }
            IconButton(
                onClick = { showCreateFolderDialog = "" },
                modifier = Modifier.testTag("explorer_new_folder_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CreateNewFolder,
                    contentDescription = "Nueva carpeta",
                    tint = TextSecondary
                )
            }
        }

        HorizontalDivider(color = ObsidianBorder)

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 6.dp)
        ) {
            items(flattenedVisibleNodes, key = { it.first.relativePath }) { (node, depth) ->
                FileTreeRow(
                    node = node,
                    depth = depth,
                    isExpanded = node.relativePath in expandedFolders,
                    isSelected = node.relativePath == activeFilePath,
                    onClick = {
                        if (node.isDirectory) {
                            onToggleFolder(node.relativePath)
                        } else {
                            onOpenFile(node.relativePath)
                        }
                    },
                    onNewFileInside = { showCreateFileDialog = "${node.relativePath}/" },
                    onNewFolderInside = { showCreateFolderDialog = "${node.relativePath}/" },
                    onRename = { renameTarget = node },
                    onDelete = { deleteTarget = node }
                )
            }
        }
    }

    showCreateFileDialog?.let { prefix ->
        PathInputDialog(
            title = "Nuevo archivo",
            label = "Ruta relativa (ej. src/Utils.kt)",
            initialValue = prefix,
            confirmText = "Crear",
            onDismiss = { showCreateFileDialog = null },
            onConfirm = { path ->
                onCreateFile(path)
                showCreateFileDialog = null
            }
        )
    }

    showCreateFolderDialog?.let { prefix ->
        PathInputDialog(
            title = "Nueva carpeta",
            label = "Ruta de carpeta",
            initialValue = prefix,
            confirmText = "Crear",
            onDismiss = { showCreateFolderDialog = null },
            onConfirm = { path ->
                onCreateFolder(path)
                showCreateFolderDialog = null
            }
        )
    }

    renameTarget?.let { node ->
        PathInputDialog(
            title = "Renombrar",
            label = "Nueva ruta relativa",
            initialValue = node.relativePath,
            confirmText = "Guardar",
            onDismiss = { renameTarget = null },
            onConfirm = { newPath ->
                onRenameNode(node.relativePath, newPath)
                renameTarget = null
            }
        )
    }

    deleteTarget?.let { node ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Eliminar ${if (node.isDirectory) "carpeta" else "archivo"}") },
            text = { Text("¿Seguro que deseas eliminar '${node.relativePath}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteNode(node.relativePath)
                        deleteTarget = null
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun FileTreeRow(
    node: FileNode,
    depth: Int,
    isExpanded: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onNewFileInside: () -> Unit,
    onNewFolderInside: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) ObsidianSurfaceElevated else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = (8 + depth * 14).dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (node.isDirectory) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                contentDescription = null,
                tint = ForgeAmber,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Spacer(Modifier.width(18.dp))
            FileExtensionChip(fileName = node.name)
        }

        Spacer(Modifier.width(8.dp))

        Text(
            text = node.name,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = JetBrainsMonoFamily,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (isSelected) ForgeCyan else TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Opciones",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                if (node.isDirectory) {
                    DropdownMenuItem(
                        text = { Text("Nuevo archivo aquí") },
                        leadingIcon = { Icon(Icons.Default.Add, null) },
                        onClick = {
                            menuExpanded = false
                            onNewFileInside()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Nueva subcarpeta") },
                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                        onClick = {
                            menuExpanded = false
                            onNewFolderInside()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Renombrar") },
                    leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                    onClick = {
                        menuExpanded = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.DeleteOutline,
                            null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
fun FileExtensionChip(fileName: String) {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    val (label, color) = when (ext) {
        "kt" -> "KT" to ForgeViolet
        "kts", "gradle" -> "GR" to ForgeEmerald
        "xml" -> "XML" to ForgeAmber
        "html", "htm" -> "HTML" to Color(0xFFFB923C)
        "css" -> "CSS" to ForgeCyan
        "js" -> "JS" to ForgeAmber
        "yml", "yaml" -> "YML" to Color(0xFFF472B6)
        "json" -> "{}" to ForgeEmerald
        "md" -> "MD" to TextSecondary
        else -> null to TextMuted
    }

    if (label != null) {
        Surface(
            color = color.copy(alpha = 0.16f),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = label,
                color = color,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    } else {
        Icon(
            imageVector = Icons.Default.InsertDriveFile,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun PathInputDialog(
    title: String,
    label: String,
    initialValue: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (value.isNotBlank()) onConfirm(value.trim())
                }
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
