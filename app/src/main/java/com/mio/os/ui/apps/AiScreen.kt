package com.mio.os.ui.apps

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mio.os.core.AiEngine
import com.mio.os.core.AiMessage
import com.mio.os.core.Mio
import com.mio.os.core.MioNav
import com.mio.os.core.MioScreen
import com.mio.os.ui.components.AmbientOrb
import com.mio.os.ui.components.GlassCard
import com.mio.os.ui.components.MioLogo
import com.mio.os.ui.components.MioWallpaper
import com.mio.os.ui.components.bounceClick
import com.mio.os.ui.theme.LocalAccent
import com.mio.os.ui.theme.LocalSettings
import com.mio.os.ui.theme.MioColors
import com.mio.os.ui.theme.MioType
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

@Composable
fun AiScreen() {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val scope = rememberCoroutineScope()
    val accent = LocalAccent.current
    val listState = rememberLazyListState()

    var messages by remember { mutableStateOf(loadHistory()) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showSetup by remember { mutableStateOf(false) }

    var pendingVoice by remember { mutableStateOf(false) }
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            input = spoken
            pendingVoice = true
        }
    }

    fun persist(list: List<AiMessage>) {
        val arr = JSONArray()
        list.takeLast(80).forEach { m ->
            arr.put(JSONObject().put("role", m.role).put("text", m.text).put("time", m.time))
        }
        Mio.prefs.edit { it.copy(aiHistory = arr.toString()) }
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        val userMsg = AiMessage("user", text)
        val next = messages + userMsg
        messages = next
        persist(next)
        input = ""
        busy = true
        scope.launch {
            val reply = AiEngine.send(context, next.dropLast(1), text)
            val updated = messages + reply
            messages = updated
            persist(updated)
            busy = false
            listState.animateScrollToItem((updated.size - 1).coerceAtLeast(0))
        }
    }

    LaunchedEffect(pendingVoice) {
        if (pendingVoice) {
            pendingVoice = false
            send()
        }
    }

    fun launchVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to Mio")
        }
        runCatching { voiceLauncher.launch(intent) }
            .onFailure {
                com.mio.os.core.LauncherHelper.toast(context, "No speech recognition service installed")
            }
    }

    Box(Modifier.fillMaxSize()) {
        MioWallpaper(settings, extraDim = 0.55f)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {

            // Header
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.09f))
                        .bounceClick { MioNav.pop() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MioLogo(Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Mio AI", style = MioType.title.copy(fontSize = 14.sp, letterSpacing = 0.6.sp))
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.09f))
                        .bounceClick { showSetup = !showSetup },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Settings, "AI settings", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            // Orb + intro
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AmbientOrb(Modifier.size(if (messages.isEmpty()) 132.dp else 76.dp))
                Spacer(Modifier.height(12.dp))
                Text("Mio AI", style = MioType.h1.copy(fontSize = if (messages.isEmpty()) 24.sp else 18.sp))
                Spacer(Modifier.height(4.dp))
                Text(
                    if (messages.isEmpty()) "How can I help you today?" else
                        (if (settings.aiEndpoint.isBlank()) "Local mode · on-device actions" else "Connected · ${settings.aiModel}"),
                    style = MioType.body.copy(fontSize = 11.5.sp),
                )
            }

            Spacer(Modifier.height(14.dp))

            if (messages.isEmpty()) {
                QuickActions(
                    onAction = { prompt ->
                        input = prompt
                        send()
                    },
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(messages) { m -> ChatBubble(m) }
                    if (busy) {
                        item { TypingBubble() }
                    }
                }
            }

            // Input bar
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(accent.copy(alpha = 0.22f))
                        .bounceClick { launchVoice() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Mic, "Voice", tint = accent, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (input.isEmpty()) {
                        Text(
                            "Ask Mio anything…",
                            style = MioType.body.copy(fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.4f)),
                        )
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        textStyle = MioType.title.copy(fontSize = 13.sp),
                        cursorBrush = SolidColor(accent),
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(32.dp).clip(CircleShape)
                        .background(
                            if (input.isBlank()) androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.10f))
                            else Brush.linearGradient(listOf(accent, MioColors.PurpleSoft))
                        )
                        .bounceClick(scaleDown = 0.9f) { send() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Send, "Send", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }

        if (showSetup) {
            AiSetupSheet(
                endpoint = settings.aiEndpoint,
                apiKey = settings.aiKey,
                model = settings.aiModel,
                onDismiss = { showSetup = false },
                onSave = { e, k, m ->
                    Mio.prefs.edit { it.copy(aiEndpoint = e, aiKey = k, aiModel = m) }
                    showSetup = false
                },
            )
        }
    }
}

private fun loadHistory(): List<AiMessage> {
    val raw = Mio.prefs.current.aiHistory
    if (raw.isBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            AiMessage(o.optString("role", "assistant"), o.optString("text", ""), o.optLong("time", 0L))
        }.filter { it.text.isNotBlank() }
    }.getOrDefault(emptyList())
}

@Composable
private fun QuickActions(onAction: (String) -> Unit) {
    val actions = listOf(
        "Chat" to "Tell me something interesting",
        "Create" to "note Write my study plan",
        "Analyze" to "battery",
        "Tools" to "torch on",
    )
    Column(Modifier.padding(horizontal = 24.dp)) {
        actions.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, prompt) ->
                    GlassCard(
                        Modifier.weight(1f).bounceClick(scaleDown = 0.96f) { onAction(prompt) },
                        corner = 18.dp, alpha = 0.07f,
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Add, null, tint = LocalAccent.current, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(label, style = MioType.title.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(prompt, style = MioType.micro.copy(fontSize = 9.5.sp), maxLines = 2)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ChatBubble(m: AiMessage) {
    val isUser = m.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.86f)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 18.dp,
                    )
                )
                .background(
                    if (isUser) Brush.linearGradient(listOf(LocalAccent.current.copy(alpha = 0.85f), MioColors.PurpleSoft.copy(alpha = 0.75f)))
                    else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.06f)))
                )
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Text(
                m.text,
                style = MioType.body.copy(
                    color = Color.White.copy(alpha = 0.94f),
                    fontSize = 12.5.sp, lineHeight = 18.sp,
                ),
            )
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(Modifier.fillMaxWidth()) {
        Box(
            Modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text("Mio is thinking…", style = MioType.micro.copy(fontSize = 10.5.sp))
        }
    }
}

@Composable
private fun AiSetupSheet(
    endpoint: String,
    apiKey: String,
    model: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var e by remember { mutableStateOf(endpoint) }
    var k by remember { mutableStateOf(apiKey) }
    var m by remember { mutableStateOf(model) }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D0918),
        dragHandle = { com.mio.os.ui.components.SheetHandle() },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("Connect your AI", style = MioType.h2)
            Spacer(Modifier.height(6.dp))
            Text(
                "MIO OS ships with an offline assistant that can operate your device. " +
                    "Paste any OpenAI-compatible endpoint to unlock full conversation. " +
                    "The key is stored only on this device.",
                style = MioType.body.copy(fontSize = 11.5.sp, lineHeight = 17.sp),
            )
            Spacer(Modifier.height(14.dp))
            com.mio.os.ui.setup.MioTextField(e, { e = it }, "Endpoint (https://api.openai.com/v1)")
            Spacer(Modifier.height(10.dp))
            com.mio.os.ui.setup.MioTextField(k, { k = it }, "API key")
            Spacer(Modifier.height(10.dp))
            com.mio.os.ui.setup.MioTextField(m, { m = it }, "Model (gpt-4o-mini)")
            Spacer(Modifier.height(16.dp))
            Row {
                com.mio.os.ui.settings.SettingsPill("Save", primary = true) { onSave(e.trim(), k.trim(), m.trim()) }
                Spacer(Modifier.width(8.dp))
                com.mio.os.ui.settings.SettingsPill("Use offline mode") { onSave("", "", "") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
