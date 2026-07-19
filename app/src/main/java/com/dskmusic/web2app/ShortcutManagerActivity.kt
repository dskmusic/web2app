package com.dskmusic.web2app

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.dskmusic.web2app.databinding.ActivityShortcutManagerBinding

class ShortcutManagerActivity : BaseActivity() {

    private lateinit var binding: ActivityShortcutManagerBinding
    private lateinit var adapter: SavedShortcutAdapter

    override fun useNoActionBar(): Boolean = true

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            ShortcutStore.exportTo(this, uri)
            Toast.makeText(this, R.string.export_success, Toast.LENGTH_SHORT).show()
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val count = ShortcutStore.importFrom(this, uri)
            Toast.makeText(this, getString(R.string.import_success, count), Toast.LENGTH_SHORT).show()
            refreshList()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShortcutManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = SavedShortcutAdapter(mutableListOf(), ::onEdit, ::onRepin, ::onDelete)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_shortcut_manager, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export -> { exportLauncher.launch("web2app_shortcuts.json"); true }
            R.id.action_import -> { importLauncher.launch(arrayOf("application/json")); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun refreshList() {
        val list = ShortcutStore.loadAll(this)
        adapter.submitList(list)
        binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun onEdit(item: SavedShortcut) {
        startActivity(Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_EDIT_ID, item.id)
        })
    }

    private fun onRepin(item: SavedShortcut) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            Toast.makeText(this, R.string.shortcut_not_supported, Toast.LENGTH_SHORT).show()
            return
        }
        val iconFile = ShortcutStore.iconFile(this, item.id)
        val icon = if (iconFile.exists()) {
            IconCompat.createWithAdaptiveBitmap(BitmapFactory.decodeFile(iconFile.absolutePath))
        } else {
            IconCompat.createWithResource(this, R.mipmap.ic_launcher)
        }
        val intent = Intent(this, WebViewActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(WebViewActivity.EXTRA_URL, item.url)
            putExtra(WebViewActivity.EXTRA_SHORTCUT_ID, item.id)
            putExtra(WebViewActivity.EXTRA_FORCE_THEME, item.forcedTheme)
            putExtra(WebViewActivity.EXTRA_ALLOW_ROTATION, item.allowRotation)
            putExtra(WebViewActivity.EXTRA_DESKTOP_MODE, item.desktopMode)
            putExtra(WebViewActivity.EXTRA_INCOGNITO, item.incognito)
        }
        val shortcutInfo = ShortcutInfoCompat.Builder(this, item.id)
            .setShortLabel(item.name)
            .setLongLabel(item.name)
            .setIcon(icon)
            .setIntent(intent)
            .build()
        ShortcutManagerCompat.requestPinShortcut(this, shortcutInfo, null)
    }

    private fun onDelete(item: SavedShortcut) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_shortcut_title)
            .setMessage(R.string.delete_shortcut_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                ShortcutStore.remove(this, item.id)
                refreshList()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
