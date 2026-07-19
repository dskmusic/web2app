package com.dskmusic.web2app

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import com.dskmusic.web2app.databinding.ActivitySettingsBinding
import com.dskmusic.web2app.update.UpdateChecker
import kotlinx.coroutines.launch

class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding

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
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.tvFooter.text = HtmlCompat.fromHtml(getString(R.string.footer_html), HtmlCompat.FROM_HTML_MODE_LEGACY)
        binding.tvFooter.movementMethod = android.text.method.LinkMovementMethod.getInstance()

        setupThemeSelector()
        setupLanguageSelector()

        binding.btnExport.setOnClickListener { exportLauncher.launch("web2app_shortcuts.json") }
        binding.btnImport.setOnClickListener { importLauncher.launch(arrayOf("application/json")) }
        binding.btnReset.setOnClickListener { confirmReset() }
        binding.btnCheckUpdate.setOnClickListener { checkForUpdate() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupThemeSelector() {
        val labels = listOf(
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark),
            getString(R.string.theme_blue),
            getString(R.string.theme_green),
            getString(R.string.theme_dark_blue),
            getString(R.string.theme_dark_green),
            getString(R.string.theme_amoled)
        )
        val values = listOf(
            Prefs.THEME_SYSTEM,
            Prefs.THEME_LIGHT,
            Prefs.THEME_DARK,
            Prefs.THEME_BLUE,
            Prefs.THEME_GREEN,
            Prefs.THEME_DARK_BLUE,
            Prefs.THEME_DARK_GREEN,
            Prefs.THEME_AMOLED
        )
        binding.actTheme.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, labels))

        val currentIndex = values.indexOf(Prefs.getTheme(this)).coerceAtLeast(0)
        binding.actTheme.setText(labels[currentIndex], false)

        binding.actTheme.setOnItemClickListener { _, _, position, _ ->
            Prefs.setTheme(this, values[position])
        }
    }

    private fun setupLanguageSelector() {
        val labels = listOf(
            getString(R.string.language_system),
            getString(R.string.language_es),
            getString(R.string.language_en)
        )
        val values = listOf(Prefs.LANG_SYSTEM, Prefs.LANG_ES, Prefs.LANG_EN)
        binding.actLanguage.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, labels))

        val currentIndex = values.indexOf(Prefs.getLanguage(this)).coerceAtLeast(0)
        binding.actLanguage.setText(labels[currentIndex], false)

        binding.actLanguage.setOnItemClickListener { _, _, position, _ ->
            val lang = values[position]
            Prefs.setLanguage(this, lang)
            applyLocale(lang)
        }
    }

    private fun checkForUpdate() {
        lifecycleScope.launch {
            val info = UpdateChecker.checkForUpdate(this@SettingsActivity)
            if (info != null) {
                UpdateChecker.promptInstall(this@SettingsActivity, info)
            } else {
                Toast.makeText(this@SettingsActivity, R.string.update_none, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmReset() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.reset_confirm_title)
            .setMessage(R.string.reset_confirm_message)
            .setPositiveButton(R.string.reset_confirm_positive) { _, _ -> performReset() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun performReset() {
        Prefs.clear(this)
        cacheDir.deleteRecursively()
        externalCacheDir?.deleteRecursively()
        applyLocale(Prefs.LANG_SYSTEM)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
