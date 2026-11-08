package com.mio.os.ui.home

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mio.os.core.FoldersRepo
import com.mio.os.core.HomeLayout
import com.mio.os.core.Mio
import com.mio.os.core.MioNav
import com.mio.os.ui.components.AppShortcut
import com.mio.os.ui.settings.MioScreenScaffold
import com.mio.os.ui.settings.SettingsPill
import com.mio.os.ui.theme.LocalApps
import com.mio.os.ui.theme.LocalSettings
import com.mio.os.ui.theme.MioType
import kotlinx.coroutines.launch

/**
 * Home Screen 2.0 arrangement: long-press any icon and drag it anywhere in
 * the grid (edges auto-scroll). The order persists ([MioSettings.homeOrder]
 * via [HomeLayout]) and the home pages follow it; newly installed apps
 * append at the end.
 */
@Composable
fun ArrangeScreen() {
    val settings = LocalSettings.current
    val apps = LocalApps.current
    val scope = rememberCoroutineScope()
    val base = remember(apps, settings.hiddenApps, settings.folders, settings.homeOrder) {
        val folders = FoldersRepo.all()
        val visible = apps.filter { a ->
            a.packageName !in settings.hiddenApps && folders.none { f -> a.packageName in f.apps }
        }
        val byPkg = visible.associateBy { it.packageName }
        HomeLayout.orderKeys(visible.map { it.packageName }, settings.homeOrder)
            .mapNotNull { byPkg[it] }
    }
    var items by remember(base) { mutableStateOf(base) }
    val gridState = rememberLazyGridState()
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    fun indexAt(offset: Offset): Int? {
        return gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            offset.x >= item.offset.x && offset.x <= item.offset.x + item.size.width &&
                offset.y >= item.offset.y && offset.y <= item.offset.y + item.size.height
        }?.index
    }

    MioScreenScaffold(title = "Arrange icons", subtitle = "Long-press, drag, release") { m ->
        Column(m.padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsPill("Reset order") {
                    Mio.prefs.edit { it.copy(homeOrder = emptyList()) }
                    items = base
                }
                SettingsPill("Done", primary = true) { MioNav.pop() }
            }
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(settings.gridColumns),
                state = gridState,
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxWidth().height(520.dp).pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            indexAt(offset)?.let { i ->
                                items.getOrNull(i)?.let { app ->
                                    dragKey = app.key
                                    dragOffset = Offset.Zero
                                }
                            }
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            val key = dragKey ?: return@detectDragGesturesAfterLongPress
                            dragOffset += amount
                            val y = change.position.y
                            val viewport = gridState.layoutInfo.viewportSize.height.toFloat()
                            if (y < 140) scope.launch { gridState.scrollBy(-22f) }
                            else if (viewport > 0 && y > viewport - 140) {
                                scope.launch { gridState.scrollBy(22f) }
                            }
                            val from = items.indexOfFirst { it.key == key }
                            val to = indexAt(change.position)
                            if (from >= 0 && to != null && to != from && to in items.indices) {
                                items = items.toMutableList().apply { add(to, removeAt(from)) }
                            }
                        },
                        onDragEnd = {
                            Mio.prefs.edit { it.copy(homeOrder = items.map { a -> a.packageName }) }
                            dragKey = null
                            dragOffset = Offset.Zero
                        },
                        onDragCancel = {
                            dragKey = null
                            dragOffset = Offset.Zero
                        },
                    )
                },
            ) {
                items(items, key = { it.key }) { app ->
                    val dragging = app.key == dragKey
                    Box(
                        Modifier.animateItem()
                            .graphicsLayer {
                                translationX = if (dragging) dragOffset.x else 0f
                                translationY = if (dragging) dragOffset.y else 0f
                                val s = if (dragging) 1.12f else 1f
                                scaleX = s
                                scaleY = s
                                alpha = if (dragging) 0.92f else 1f
                            }
                            .zIndex(if (dragging) 1f else 0f),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppShortcut(
                            app = app,
                            iconSize = (46 * settings.iconScale).dp,
                            showLabel = settings.showLabels,
                            onClick = {},
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Tip: the first page holds the first ${settings.gridColumns * 4} icons.",
                style = MioType.micro,
            )
        }
    }
}
