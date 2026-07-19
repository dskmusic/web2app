package com.dskmusic.web2app

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Local catalog of shortcuts this app has generated. ShortcutManager itself only lets a launcher
 * pin/re-pin icons — it keeps no queryable history — so this is our own bookkeeping, backed by a
 * JSON file plus a folder of icon PNGs, used for the manager screen, editing, and export/import.
 */
object ShortcutStore {
    private const val CATALOG_FILE = "shortcuts_catalog.json"
    private const val ICONS_DIR = "shortcut_icons"

    fun iconFile(context: Context, id: String): File = File(iconsDir(context), "$id.png")
    fun sourceIconFile(context: Context, id: String): File = File(iconsDir(context), "${id}_source.png")

    private fun iconsDir(context: Context) = File(context.filesDir, ICONS_DIR).apply { mkdirs() }
    private fun catalogFile(context: Context) = File(context.filesDir, CATALOG_FILE)

    fun loadAll(context: Context): List<SavedShortcut> {
        val file = catalogFile(context)
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        return (0 until array.length()).map { fromJson(array.getJSONObject(it)) }
    }

    private fun saveAll(context: Context, list: List<SavedShortcut>) {
        val array = JSONArray()
        list.forEach { array.put(toJson(it)) }
        catalogFile(context).writeText(array.toString())
    }

    fun upsert(context: Context, shortcut: SavedShortcut) {
        val list = loadAll(context).filterNot { it.id == shortcut.id }.toMutableList()
        list.add(0, shortcut)
        saveAll(context, list)
    }

    fun remove(context: Context, id: String) {
        saveAll(context, loadAll(context).filterNot { it.id == id })
        iconFile(context, id).delete()
        sourceIconFile(context, id).delete()
    }

    private fun toJson(s: SavedShortcut) = JSONObject().apply {
        put("id", s.id)
        put("name", s.name)
        put("url", s.url)
        put("backgroundColor", s.backgroundColor ?: JSONObject.NULL)
        put("forcedTheme", s.forcedTheme)
        put("allowRotation", s.allowRotation)
        put("desktopMode", s.desktopMode)
        put("incognito", s.incognito)
        put("allowZoom", s.allowZoom)
        put("allowSelection", s.allowSelection)
        put("createdAt", s.createdAt)
    }

    private fun fromJson(o: JSONObject) = SavedShortcut(
        id = o.getString("id"),
        name = o.getString("name"),
        url = o.getString("url"),
        backgroundColor = if (o.isNull("backgroundColor")) null else o.getInt("backgroundColor"),
        forcedTheme = o.optString("forcedTheme", WebViewActivity.THEME_SYSTEM),
        allowRotation = o.optBoolean("allowRotation", true),
        desktopMode = o.optBoolean("desktopMode", false),
        incognito = o.optBoolean("incognito", false),
        allowZoom = o.optBoolean("allowZoom", false),
        allowSelection = o.optBoolean("allowSelection", false),
        createdAt = o.optLong("createdAt", System.currentTimeMillis())
    )

    /** Self-contained export: app settings, plus shortcut metadata and the final icon (base64), in one JSON file. */
    fun exportTo(context: Context, uri: Uri) {
        val shortcuts = JSONArray()
        loadAll(context).forEach { s ->
            val json = toJson(s)
            val bytes = iconFile(context, s.id).takeIf { it.exists() }?.readBytes()
            json.put("icon", bytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: JSONObject.NULL)
            shortcuts.put(json)
        }
        val settings = JSONObject().apply {
            put("theme", Prefs.getTheme(context))
            put("language", Prefs.getLanguage(context))
            put("defaultShortcutTheme", Prefs.getDefaultShortcutTheme(context))
            put("defaultAllowRotation", Prefs.getDefaultAllowRotation(context))
            put("defaultDesktopMode", Prefs.getDefaultDesktopMode(context))
            put("defaultIncognito", Prefs.getDefaultIncognito(context))
            put("defaultAllowZoom", Prefs.getDefaultAllowZoom(context))
            put("defaultAllowSelection", Prefs.getDefaultAllowSelection(context))
        }
        val root = JSONObject().apply {
            put("settings", settings)
            put("shortcuts", shortcuts)
        }
        context.contentResolver.openOutputStream(uri)?.use { it.write(root.toString().toByteArray()) }
    }

    /** Merges entries from [uri] into the local catalog and restores app settings. Returns count of shortcuts imported. */
    fun importFrom(context: Context, uri: Uri): Int {
        val text = context.contentResolver.openInputStream(uri)?.use { it.reader().readText() } ?: return 0
        val trimmed = text.trim()
        // ponytail: accept both the current object format and the older plain-array export (shortcuts only)
        val shortcutsArray: JSONArray
        if (trimmed.startsWith("[")) {
            shortcutsArray = JSONArray(trimmed)
        } else {
            val root = JSONObject(trimmed)
            shortcutsArray = root.optJSONArray("shortcuts") ?: JSONArray()
            root.optJSONObject("settings")?.let { settings ->
                val theme = settings.optString("theme", Prefs.getTheme(context))
                val language = settings.optString("language", Prefs.getLanguage(context))
                Prefs.setTheme(context, theme)
                Prefs.setLanguage(context, language)
                applyLocale(language)

                Prefs.setDefaultShortcutOptions(
                    context,
                    settings.optString("defaultShortcutTheme", Prefs.getDefaultShortcutTheme(context)),
                    settings.optBoolean("defaultAllowRotation", Prefs.getDefaultAllowRotation(context)),
                    settings.optBoolean("defaultDesktopMode", Prefs.getDefaultDesktopMode(context)),
                    settings.optBoolean("defaultIncognito", Prefs.getDefaultIncognito(context)),
                    settings.optBoolean("defaultAllowZoom", Prefs.getDefaultAllowZoom(context)),
                    settings.optBoolean("defaultAllowSelection", Prefs.getDefaultAllowSelection(context))
                )
            }
        }

        val existingIds = loadAll(context).map { it.id }.toSet()
        val newList = loadAll(context).toMutableList()
        var imported = 0
        for (i in 0 until shortcutsArray.length()) {
            val o = shortcutsArray.getJSONObject(i)
            val shortcut = fromJson(o)
            if (shortcut.id in existingIds) continue
            if (!o.isNull("icon")) {
                val bytes = Base64.decode(o.getString("icon"), Base64.NO_WRAP)
                iconFile(context, shortcut.id).writeBytes(bytes)
            }
            newList.add(0, shortcut)
            imported++
        }
        saveAll(context, newList)
        return imported
    }
}
