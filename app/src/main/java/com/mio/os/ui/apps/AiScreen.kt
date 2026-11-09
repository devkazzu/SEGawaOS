package com.mio.os.ui.apps

import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
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
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.mio.os.core.AiThread
import com.mio.os.core.AiThreads
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

    var threads by remember {
        val existing = AiThreads.all(Mio.prefs.current.aiThreads)
        if (existing.isNotEmpty()) {
            mutableStateOf(existing)
        } else {
            // One-time migration from the single v1 history.
            val legacy = loadHistory().takeLast(80)
            val seeded = if (legacy.isEmpty()) {
                AiThreads.fresh()
            } else {
                AiThread(
                    "legacy",
                    AiThreads.titleFor(legacy.firstOrNull { it.role == "user" }?.text ?: "Chat"),
                    legacy, System.currentTimeMillis(),
                )
            }
            Mio.prefs.edit { it.copy(aiThreads = AiThreads.persist(listOf(seeded)), aiActiveThread = seeded.id) }
            mutableStateOf(listOf(seeded))
        }
    }
    var activeId by remember {
        mutableStateOf(
            Mio.prefs.current.aiActiveThread.ifBlank { threads.firstOrNull()?.id ?: "" },
        )
    }
    val active = threads.find { it.id == activeId } ?: threads.firstOrNull() ?: AiThreads.fresh()
    val messages = active.messages
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showSetup by remember { mutableStateOf(false) }
    var showThreads by remember { mutableStateOf(false) }

    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember {
        TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { tts.stop(); tts.shutdown() } }
    }

    fun speak(text: String) {
        if (!ttsReady) {
            com.mio.os.core.LauncherHelper.toast(context, "Voice engine not ready yet")
            return
        }
        runCatching { tts.speak(text.take(1200), TextToSpeech.QUEUE_FLUSH, null, "segawa-ai") }
    }

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

    fun persistTo(id: String, list: List<AiMessage>) {
        val target = threads.find { it.id == id } ?: return
        val title = list.firstOrNull { it.role == "user" }?.text?.let { AiThreads.titleFor(it) }
            ?: target.title
        val updated = target.copy(title = title, messages = list.takeLast(80), updated = System.currentTimeMillis())
        val next = (listOf(updated) + threads.filterNot { it.id == id }).sortedByDescending { it.updated }
        threads = next
        Mio.prefs.edit { it.copy(aiThreads = AiThreads.persist(next), aiActiveThread = id) }
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        val id = active.id
        val userMsg = AiMessage("user", text)
        val next = messages + userMsg
        persistTo(id, next)
        input = ""
        busy = true
        scope.launch {
            val reply = AiEngine.send(context, next.dropLast(1), text)
            val base = threads.find { it.id == id }?.messages ?: next
            persistTo(id, base + reply)
            busy = false
            if (Mio.prefs.current.aiTts) speak(reply.text)
            listState.animateScrollToItem(base.size.coerceAtLeast(0))
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
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to Segawa")
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
                    Text("SEGAWA AI", style = MioType.title.copy(fontSize = 14.sp, letterSpacing = 0.6.sp))
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.09f))
                        .bounceClick { showThreads = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.History, "Chats", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
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
                Text("SEGAWA AI", style = MioType.h1.copy(fontSize = if (messages.isEmpty()) 24.sp else 18.sp))
                Spacer(Modifier.height(4.dp))
                Text(
                    if (messages.isEmpty()) "How can I help you today?" else
                        (if (settings.aiEndpoint.isBlank()) "Local mode · on-device actions" else "Connected · ${settings.aiModel}"),
                    style = MioType.body.copy(fontSize = 11.5.sp),
                )
            }

            Spacer(Modifier.height(14.dp))

            if (messages.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    QuickActions(
                        onAction = { prompt ->
                            input = prompt
                            send()
                        },
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(messages) { m ->
                        ChatBubble(m, onSpeak = if (m.role == "assistant") ({ speak(m.text) }) else null)
                    }
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
                            "Ask Segawa anything…",
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
                tts = settings.aiTts,
                onDismiss = { showSetup = false },
                onSave = { e, k, m ->
                    Mio.prefs.edit { it.copy(aiEndpoint = e, aiKey = k, aiModel = m) }
                    showSetup = false
                },
            )
        }

        if (showThreads) {
            ThreadsSheet(
                threads = threads,
                activeId = activeId,
                onSelect = { id ->
                    activeId = id
                    Mio.prefs.edit { it.copy(aiActiveThread = id) }
                    showThreads = false
                },
                onNew = {
                    val t = AiThreads.fresh()
                    val next = (listOf(t) + threads).sortedByDescending { it.updated }
                    threads = next
                    activeId = t.id
                    Mio.prefs.edit { it.copy(aiThreads = AiThreads.persist(next), aiActiveThread = t.id) }
                    showThreads = false
                },
                onDelete = { id ->
                    var next = threads.filterNot { it.id == id }
                    if (next.isEmpty()) next = listOf(AiThreads.fresh())
                    if (activeId == id) activeId = next.first().id
                    threads = next
                    Mio.prefs.edit { it.copy(aiThreads = AiThreads.persist(next), aiActiveThread = activeId) }
                },
                onClear = {
                    val t = AiThreads.fresh()
                    threads = listOf(t)
                    activeId = t.id
                    Mio.prefs.edit { it.copy(aiThreads = AiThreads.persist(listOf(t)), aiActiveThread = t.id) }
                    showThreads = false
                },
                onDismiss = { showThreads = false },
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
        Triple("Chat", "Talk to Segawa about anything", "Tell me something interesting"),
        Triple("Create", "Capture a thought as a note", "note Write my study plan"),
        Triple("Analyze", "Device status at a glance", "battery"),
        Triple("Tools", "Torch, music and quick actions", "torch on"),
    )
    Column(Modifier.padding(horizontal = 24.dp)) {
        actions.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, hint, prompt) ->
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
                            Text(hint, style = MioType.micro.copy(fontSize = 9.5.sp), maxLines = 2)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ChatBubble(m: AiMessage, onSpeak: (() -> Unit)?) {
    val isUser = m.role == "user"
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
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
        if (!isUser) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp, start = 6.dp),
            ) {
                Text(
                    if (m.local) "on-device" else "cloud",
                    style = MioType.micro.copy(fontSize = 8.5.sp, color = Color.White.copy(alpha = 0.45f)),
                )
                if (onSpeak != null) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Read aloud",
                        style = MioType.micro.copy(fontSize = 8.5.sp, color = LocalAccent.current),
                        modifier = Modifier.bounceClick(onClick = onSpeak).padding(vertical = 2.dp),
                    )
                }
            }
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
            Text("Segawa is thinking…", style = MioType.micro.copy(fontSize = 10.5.sp))
        }
    }
}

@Composable
private fun AiSetupSheet(
    endpoint: String,
    apiKey: String,
    model: String,
    tts: Boolean,
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
                "SEGAWA OS ships with an offline assistant that can operate your device. " +
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
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.mio.os.ui.settings.SettingsPill(if (tts) "Voice replies: on" else "Voice replies: off") {
                    Mio.prefs.edit { it.copy(aiTts = !it.aiTts) }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Voice replies read every answer aloud with the system text-to-speech engine.",
                style = MioType.micro.copy(fontSize = 9.5.sp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ThreadsSheet(
    threads: List<AiThread>,
    activeId: String,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D0918),
        dragHandle = { com.mio.os.ui.components.SheetHandle() },
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Chats", style = MioType.h2, modifier = Modifier.weight(1f))
                com.mio.os.ui.settings.SettingsPill("New chat", primary = true, onClick = onNew)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${threads.size} conversation" + if (threads.size == 1) "" else "s" + " · stored on this device",
                style = MioType.micro.copy(fontSize = 10.sp),
            )
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.height(320.dp)) {
                items(threads, key = { it.id }) { t ->
                    val selected = t.id == activeId
                    GlassCard(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .bounceClick(scaleDown = 0.98f) { onSelect(t.id) },
                        corner = 16.dp, alpha = if (selected) 0.14f else 0.06f,
                    ) {
                        Row(
                            Modifier.padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    t.title,
                                    style = MioType.title.copy(
                                        fontSize = 12.5.sp,
                                        color = if (selected) LocalAccent.current else Color.White,
                                    ),
                                    maxLines = 1,
                                )
                                Text(
                                    (if (t.messages.isEmpty()) "Empty" else "${t.messages.size} messages") +
                                        " · " + android.text.format.DateFormat.format("d MMM · HH:mm", t.updated),
                                    style = MioType.micro.copy(fontSize = 9.5.sp),
                                )
                            }
                            if (threads.size > 1 || t.messages.isNotEmpty()) {
                                Text(
                                    "Delete",
                                    style = MioType.micro.copy(
                                        fontSize = 9.5.sp,
                                        color = Color.White.copy(alpha = 0.55f),
                                    ),
                                    modifier = Modifier.bounceClick { onDelete(t.id) }
                                        .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
                                )
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Text(
                            "Clear all chats",
                            style = MioType.micro.copy(fontSize = 10.5.sp, color = MioColors.Pink),
                            modifier = Modifier.bounceClick(onClick = onClear).padding(8.dp),
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
