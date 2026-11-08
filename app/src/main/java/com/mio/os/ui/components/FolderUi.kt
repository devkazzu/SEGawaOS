package com.mio.os.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mio.os.core.FoldersRepo
import com.mio.os.core.Mio
import com.mio.os.core.MioApp
import com.mio.os.core.MioFolder
import com.mio.os.ui.theme.LocalAccent
import com.mio.os.ui.theme.LocalApps
import com.mio.os.ui.theme.MioColors
import com.mio.os.ui.theme.MioType

@Composable
fun FolderTile(
    folder: MioFolder,
    iconSize: Dp = 52.dp,
    showLabel: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val allApps = LocalApps.current
    val inside = remember(folder, allApps) {
        folder.apps.mapNotNull { pkg -> allApps.firstOrNull { it.packageName == pkg } }
    }
    Column(
        Modifier.width(iconSize + 22.dp).bounceClick(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(iconSize).clip(RoundedCornerShape(iconSize * 0.30f))
                .background(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.06f))
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(iconSize * 0.30f))
                .padding(iconSize * 0.12f),
            contentAlignment = Alignment.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                inside.take(4).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        row.forEach { app -> AppIconView(app, iconSize * 0.34f) }
                    }
                }
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(7.dp))
            Text(
                folder.name,
                style = MioType.micro.copy(color = Color.White.copy(alpha = 0.88f), fontSize = 10.5.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun FolderPickerSheet(app: MioApp, onDismiss: () -> Unit) {
    val folders = remember { FoldersRepo.all() }
    var name by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF0D0918),
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("Move to folder", style = MioType.h2)
            Spacer(Modifier.height(4.dp))
            Text(app.name, style = MioType.body.copy(fontSize = 11.5.sp))
            Spacer(Modifier.height(14.dp))
            folders.forEach { f ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .bounceClick(scaleDown = 0.98f) {
                            FoldersRepo.addTo(f.name, app.packageName)
                            onDismiss()
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Folder, null, tint = LocalAccent.current, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(f.name, style = MioType.title.copy(fontSize = 13.sp), modifier = Modifier.weight(1f))
                    Text("${f.apps.size} apps", style = MioType.micro.copy(fontSize = 9.5.sp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("NEW FOLDER", style = MioType.micro.copy(fontSize = 9.sp, letterSpacing = 1.4.sp))
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (name.isEmpty()) {
                    Text("Folder name", style = MioType.body.copy(fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.4f)))
                }
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = MioType.title.copy(fontSize = 13.sp),
                    cursorBrush = SolidColor(LocalAccent.current),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row {
                com.mio.os.ui.settings.SettingsPill("Create & add", primary = true) {
                    FoldersRepo.create(name.ifBlank { "Folder" }, app.packageName)
                    onDismiss()
                }
                Spacer(Modifier.width(8.dp))
                com.mio.os.ui.settings.SettingsPill("Cancel") { onDismiss() }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun FolderContentSheet(folder: MioFolder, onDismiss: () -> Unit) {
    val allApps = LocalApps.current
    var rename by remember { mutableStateOf(false) }
    var name by remember(folder.name) { mutableStateOf(folder.name) }
    val apps = remember(folder, allApps) {
        folder.apps.mapNotNull { pkg -> allApps.firstOrNull { it.packageName == pkg } }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF0D0918),
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            if (rename) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                ) {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        textStyle = MioType.title.copy(fontSize = 14.sp),
                        cursorBrush = SolidColor(LocalAccent.current),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(10.dp))
                com.mio.os.ui.settings.SettingsPill("Rename", primary = true) {
                    FoldersRepo.rename(folder.name, name)
                    rename = false
                    onDismiss()
                }
            } else {
                Text(folder.name, style = MioType.h2)
                Spacer(Modifier.height(2.dp))
                Text("${apps.size} apps · long-press an icon to remove", style = MioType.micro.copy(fontSize = 10.sp))
            }
            Spacer(Modifier.height(14.dp))
            apps.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { app ->
                        Column(
                            Modifier.width(66.dp).bounceClick(
                                onClick = { Mio.apps.launch(app); onDismiss() },
                                onLongClick = {
                                    FoldersRepo.removeApp(folder.name, app.packageName)
                                    onDismiss()
                                },
                            ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AppIconView(app, 48.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                app.name,
                                style = MioType.micro.copy(fontSize = 9.5.sp),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.width(66.dp)) }
                }
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(6.dp))
            Row {
                com.mio.os.ui.settings.SettingsPill("Rename") { rename = true }
                Spacer(Modifier.width(8.dp))
                com.mio.os.ui.settings.SettingsPill("Delete folder") {
                    FoldersRepo.delete(folder.name)
                    onDismiss()
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
