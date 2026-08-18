package com.dskmusic.web2app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    fun captureFile(context: Context, id: String): File = File(iconsDir(context), "${id}_capture.png")

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
        captureFile(context, id).delete()
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
        put("folder", s.folder)
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
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        folder = o.optString("folder", "")
    )

    /** Self-contained export: app settings, plus shortcut metadata and the final icon (base64), in one JSON file. */
    fun exportTo(context: Context, uri: Uri) {
        val shortcuts = JSONArray()
        loadAll(context).forEach { s ->
            val json = toJson(s)
            val bytes = iconFile(context, s.id).takeIf { it.exists() }?.readBytes()
            json.put("icon", bytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: JSONObject.NULL)
            val sourceBytes = sourceIconFile(context, s.id).takeIf { it.exists() }?.readBytes()
            json.put("sourceIcon", sourceBytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: JSONObject.NULL)
            val captureBytes = captureFile(context, s.id).takeIf { it.exists() }?.readBytes()
            json.put("capture", captureBytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: JSONObject.NULL)
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
            if (!o.isNull("sourceIcon")) {
                val bytes = Base64.decode(o.getString("sourceIcon"), Base64.NO_WRAP)
                sourceIconFile(context, shortcut.id).writeBytes(bytes)
            }
            if (!o.isNull("capture")) {
                val bytes = Base64.decode(o.getString("capture"), Base64.NO_WRAP)
                captureFile(context, shortcut.id).writeBytes(bytes)
            }
            newList.add(0, shortcut)
            imported++
        }
        saveAll(context, newList)
        return imported
    }

    private const val BACKUP_FOLDER = "Web2App"
    private const val MAX_AUTO_BACKUPS = 10

    /** Timestamped default name for a backup file: 20260818_223112_backup.json. */
    fun backupFileName(): String =
        "${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}_backup.json"

    /**
     * Silent backup written straight into the shared "Web2App" folder — no picker, no prompt.
     * Keeps only the last [MAX_AUTO_BACKUPS] backups it wrote itself (tracked in Prefs, oldest
     * deleted first, log-rotate style); a manual export the user placed in the same folder is
     * never touched, since deletion only ever targets refs this function recorded itself.
     */
    fun writeAutoBackup(context: Context) {
        runCatching {
            val (uri, ref) = createBackupFile(context, backupFileName()) ?: return
            exportTo(context, uri)

            val updated = Prefs.getAutoBackups(context) + ref
            val overflow = (updated.size - MAX_AUTO_BACKUPS).coerceAtLeast(0)
            updated.take(overflow).forEach { deleteBackupRef(context, it) }
            Prefs.setAutoBackups(context, updated.drop(overflow))
        }
    }

    /** Creates a new file in the shared "Web2App" folder. Returns the writable Uri plus a ref string for later deletion. */
    private fun createBackupFile(context: Context, fileName: String): Pair<Uri, String>? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$BACKUP_FOLDER/")
            }
            val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values) ?: return null
            return uri to uri.toString()
        }
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStorageDirectory(), BACKUP_FOLDER).apply { mkdirs() }
        val file = File(dir, fileName)
        return Uri.fromFile(file) to file.absolutePath
    }

    private fun deleteBackupRef(context: Context, ref: String) {
        if (ref.startsWith("content://")) {
            runCatching { context.contentResolver.delete(Uri.parse(ref), null, null) }
        } else {
            runCatching { File(ref).delete() }
        }
    }
}
