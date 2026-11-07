package com.mio.os.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Home-screen folders: a named list of package names, stored as JSON in the
 * existing preference store so no database is needed.
 */
data class MioFolder(val name: String, val apps: List<String>)

object FoldersRepo {

    fun all(): List<MioFolder> {
        val raw = Mio.prefs.current.folders
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = o.optString("name", "Folder")
                val appsArr = o.optJSONArray("apps") ?: JSONArray()
                val apps = (0 until appsArr.length()).map { appsArr.optString(it) }
                    .filter { it.isNotBlank() }
                MioFolder(name, apps)
            }.filter { it.name.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun persist(folders: List<MioFolder>) {
        val arr = JSONArray()
        folders.forEach { f ->
            val appsArr = JSONArray()
            f.apps.forEach { appsArr.put(it) }
            arr.put(JSONObject().put("name", f.name).put("apps", appsArr))
        }
        Mio.prefs.edit { it.copy(folders = arr.toString()) }
    }

    fun create(name: String, packageName: String?) {
        val folders = all().toMutableList()
        val existing = folders.indexOfFirst { it.name.equals(name, true) }
        if (existing >= 0) {
            if (packageName != null && packageName !in folders[existing].apps) {
                folders[existing] = folders[existing].copy(apps = folders[existing].apps + packageName)
            }
        } else {
            folders += MioFolder(name.ifBlank { "Folder" }, listOfNotNull(packageName))
        }
        persist(folders)
    }

    fun addTo(name: String, packageName: String) {
        persist(
            all().map {
                if (it.name == name && packageName !in it.apps) it.copy(apps = it.apps + packageName) else it
            }
        )
    }

    fun removeApp(name: String, packageName: String) {
        persist(
            all().map {
                if (it.name == name) it.copy(apps = it.apps - packageName) else it
            }.filter { it.apps.isNotEmpty() }
        )
    }

    fun rename(old: String, new: String) {
        if (new.isBlank()) return
        persist(all().map { if (it.name == old) it.copy(name = new) else it })
    }

    fun delete(name: String) = persist(all().filterNot { it.name == name })

    fun folderOf(packageName: String): String? =
        all().firstOrNull { packageName in it.apps }?.name
}
