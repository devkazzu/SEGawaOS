package com.mio.os.ui.apps

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mio.os.core.BackupCodec
import com.mio.os.core.LauncherHelper
import com.mio.os.core.Mio
import com.mio.os.ui.components.GlassCard
import com.mio.os.ui.components.SectionHeader
import com.mio.os.ui.settings.MioScreenScaffold
import com.mio.os.ui.settings.SettingsPill
import com.mio.os.ui.setup.MioTextField
import com.mio.os.ui.theme.LocalSettings
import com.mio.os.ui.theme.MioType
import java.text.DateFormat
import java.util.Date

/**
 * Backup & restore: exports every SEGAWA OS setting — theme, wallpaper,
 * dock, playlists, routines, notes, icon pack — into one JSON document,
 * optionally encrypted with AES-256-GCM under a user password. The file is
 * written through the system file picker, so it lands wherever the user
 * wants (local folder, cloud drive, USB).
 */
@Composable
fun BackupScreen() {
    val context = LocalContext.current
    val settings = LocalSettings.current
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun stamp(info: String) {
        val full = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date()) +
            " · $info"
        Mio.prefs.edit { it.copy(backupInfo = full) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) {
            busy = false
            return@rememberLauncherForActivityResult
        }
        val ok = runCatching {
            val parts = mapOf(
                "prefs" to Mio.prefs.exportJson(),
                "meta" to "segawa-backup " + DateFormat.getDateTimeInstance().format(Date()),
            )
            val packed = BackupCodec.pack(parts)
            val out = if (password.isBlank()) packed else BackupCodec.encrypt(password, packed)
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(out.toByteArray(Charsets.UTF_8))
            } ?: error("no stream")
        }.isSuccess
        busy = false
        if (ok) {
            val label = if (password.isBlank()) "Exported (unencrypted)" else "Exported (encrypted)"
            stamp(label)
            LauncherHelper.toast(context, "Backup saved")
        } else {
            LauncherHelper.toast(context, "Export failed")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            busy = false
            return@rememberLauncherForActivityResult
        }
        val raw = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        }.getOrNull()
        var restored = 0
        if (!raw.isNullOrBlank()) {
            val direct = BackupCodec.unpack(raw)
            val doc = direct ?: if (password.isBlank()) {
                null
            } else {
                BackupCodec.decrypt(password, raw)?.let { BackupCodec.unpack(it) }
            }
            val prefsJson = doc?.get("prefs")
            if (!prefsJson.isNullOrBlank() && Mio.prefs.importJson(prefsJson)) {
                restored = doc.size
            }
        }
        busy = false
        if (restored > 0) {
            stamp("Restored $restored sections")
            LauncherHelper.toast(context, "Backup restored — theme and layout applied")
        } else {
            val hint = if (BackupCodec.unpack(raw ?: "") == null && password.isBlank()) {
                "Wrong file — or enter the backup password first"
            } else {
                "Could not read that backup"
            }
            LauncherHelper.toast(context, hint)
        }
    }

    MioScreenScaffold(title = "Backup & restore", subtitle = "Your data, your file") { m ->
        Column(m.padding(bottom = 28.dp)) {
            GlassCard(Modifier.fillMaxWidth(), corner = 20.dp, alpha = 0.07f) {
                Column(Modifier.padding(16.dp)) {
                    Text("One file holds everything", style = MioType.title.copy(fontSize = 13.sp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Theme, wallpaper, dock, favorites, playlists, routines, notes, icon pack, " +
                            "browser bookmarks and every setting. Apps and media stay on the device — " +
                            "only SEGAWA OS data is backed up.",
                        style = MioType.body.copy(fontSize = 11.sp, lineHeight = 16.sp),
                    )
                    if (settings.backupInfo.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Last: ${settings.backupInfo}",
                            style = MioType.micro.copy(fontSize = 10.sp),
                        )
                    }
                }
            }

            SectionHeader("Password (optional)")
            MioTextField(
                value = password,
                onValueChange = { password = it },
                label = "Leave blank for a plain backup",
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "With a password the file is encrypted (AES-256-GCM). Without it, anyone reading " +
                    "the file can see your notes and settings — keep it somewhere safe.",
                style = MioType.micro.copy(fontSize = 9.5.sp),
            )

            SectionHeader("Actions")
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsPill("Export backup", primary = true) {
                    if (!busy) {
                        busy = true
                        exportLauncher.launch("segawa-backup-v2.json")
                    }
                }
                Spacer(Modifier.width(8.dp))
                SettingsPill("Import backup") {
                    if (!busy) {
                        busy = true
                        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Importing replaces all current SEGAWA OS settings with the file's contents. " +
                    "Export first if you want a way back.",
                style = MioType.micro.copy(fontSize = 9.5.sp),
            )
        }
    }
}
