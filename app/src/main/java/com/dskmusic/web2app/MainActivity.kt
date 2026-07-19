package com.dskmusic.web2app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import com.dskmusic.web2app.databinding.ActivityMainBinding
import com.dskmusic.web2app.update.UpdateChecker
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private var croppedBitmap: Bitmap? = null
    private var backgroundColor: Int? = null
    private var pendingCameraUri: Uri? = null
    private var editingId: String? = null

    override fun useNoActionBar(): Boolean = true

    private val getContentLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { startCrop(it) }
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            pendingCameraUri?.let { startCrop(it) }
        }
    }

    private val cropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { data -> UCrop.getOutput(data)?.let { loadBitmapFromUri(it) } }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else showToast(R.string.permission_denied)
    }

    private val storagePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) getContentLauncher.launch("image/*") else showToast(R.string.permission_denied)
    }

    private val pixabayLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val url = result.data?.getStringExtra(PixabaySearchActivity.EXTRA_IMAGE_URL)
            if (url != null) downloadThenCrop(url)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        binding.tvFooterHeader.text = HtmlCompat.fromHtml(getString(R.string.footer_html_short), HtmlCompat.FROM_HTML_MODE_LEGACY)
        binding.tvFooterHeader.movementMethod = android.text.method.LinkMovementMethod.getInstance()

        binding.etUrl.hideKeyboardOnImeAction()
        binding.etName.hideKeyboardOnImeAction()

        // ponytail: swallow touch scrolling so the main screen never moves, even if content overflows
        binding.scrollMain.setOnTouchListener { _, _ -> true }

        binding.etUrl.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) tryAutoFavicon(binding.etUrl.text?.toString()?.trim().orEmpty())
        }

        binding.btnSelectImage.setOnClickListener { showImageSourceDialog() }
        binding.btnBackgroundColor.setOnClickListener { showBackgroundColorDialog() }
        binding.btnGenerate.setOnClickListener { generateShortcut() }

        applyDefaultShortcutOptions()
        loadEditTarget()
        handleShareIntent()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmExit()
        })

        lifecycleScope.launch {
            UpdateChecker.checkForUpdate(this@MainActivity)?.let { UpdateChecker.promptInstall(this@MainActivity, it) }
        }
    }

    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setTitle(R.string.exit_confirm_title)
            .setMessage(R.string.exit_confirm_message)
            .setPositiveButton(R.string.exit_confirm_positive) { _, _ -> finish() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applyDefaultShortcutOptions() {
        when (Prefs.getDefaultShortcutTheme(this)) {
            WebViewActivity.THEME_LIGHT -> binding.rgShortcutTheme.check(R.id.rbShortcutLight)
            WebViewActivity.THEME_DARK -> binding.rgShortcutTheme.check(R.id.rbShortcutDark)
            else -> binding.rgShortcutTheme.check(R.id.rbShortcutSystem)
        }
        binding.swAllowRotation.isChecked = Prefs.getDefaultAllowRotation(this)
        binding.swDesktopMode.isChecked = Prefs.getDefaultDesktopMode(this)
        binding.swIncognito.isChecked = Prefs.getDefaultIncognito(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_shortcuts_manager -> {
                startActivity(Intent(this, ShortcutManagerActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadEditTarget() {
        val id = intent.getStringExtra(EXTRA_EDIT_ID) ?: return
        val saved = ShortcutStore.loadAll(this).find { it.id == id } ?: return
        editingId = id
        binding.btnGenerate.setText(R.string.update_shortcut)

        binding.etUrl.setText(saved.url)
        binding.etName.setText(saved.name)
        backgroundColor = saved.backgroundColor

        val sourceFile = ShortcutStore.sourceIconFile(this, id)
        if (sourceFile.exists()) {
            croppedBitmap = BitmapFactory.decodeFile(sourceFile.absolutePath)
            refreshPreview()
        }

        when (saved.forcedTheme) {
            WebViewActivity.THEME_LIGHT -> binding.rgShortcutTheme.check(R.id.rbShortcutLight)
            WebViewActivity.THEME_DARK -> binding.rgShortcutTheme.check(R.id.rbShortcutDark)
            else -> binding.rgShortcutTheme.check(R.id.rbShortcutSystem)
        }
        binding.swAllowRotation.isChecked = saved.allowRotation
        binding.swDesktopMode.isChecked = saved.desktopMode
        binding.swIncognito.isChecked = saved.incognito
    }

    private fun handleShareIntent() {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val url = Regex("""https?://\S+""").find(text)?.value ?: text.trim()
        if (url.isNotEmpty()) {
            binding.etUrl.setText(url)
            tryAutoFavicon(url)
        }
    }

    private fun tryAutoFavicon(url: String) {
        if (url.isEmpty() || croppedBitmap != null) return
        lifecycleScope.launch {
            val iconUrl = FaviconFetcher.find(url) ?: return@launch
            val bitmap = BitmapUtils.download(iconUrl) ?: return@launch
            if (croppedBitmap == null) {
                croppedBitmap = BitmapUtils.cropToSquare(bitmap)
                refreshPreview()
            }
        }
    }

    private fun showImageSourceDialog() {
        val options = arrayOf(
            getString(R.string.image_source_device),
            getString(R.string.image_source_camera),
            getString(R.string.image_source_internet),
            getString(R.string.image_source_web_icon)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.select_image_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> requestGalleryImage()
                    1 -> requestCameraImage()
                    2 -> pixabayLauncher.launch(Intent(this, PixabaySearchActivity::class.java))
                    3 -> requestWebIcon()
                }
            }
            .show()
    }

    private fun requestWebIcon() {
        val url = binding.etUrl.text?.toString()?.trim().orEmpty()
        if (url.isEmpty()) {
            showToast(R.string.error_url_required)
            return
        }
        lifecycleScope.launch {
            val iconUrl = FaviconFetcher.find(url)
            if (iconUrl == null) {
                showToast(R.string.favicon_not_found)
            } else {
                downloadThenCrop(iconUrl)
            }
        }
    }

    private fun downloadThenCrop(url: String) {
        lifecycleScope.launch {
            val bitmap = BitmapUtils.download(url)
            if (bitmap == null) {
                showToast(R.string.image_download_error)
            } else {
                startCrop(cacheBitmapAsUri(bitmap))
            }
        }
    }

    private fun requestGalleryImage() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            getContentLauncher.launch("image/*")
        } else {
            storagePermissionLauncher.launch(permission)
        }
    }

    private fun requestCameraImage() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val imagesDir = File(cacheDir, "images").apply { mkdirs() }
        val file = File(imagesDir, "camera_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        pendingCameraUri = uri
        takePictureLauncher.launch(uri)
    }

    private fun cacheBitmapAsUri(bitmap: Bitmap): Uri {
        val imagesDir = File(cacheDir, "images").apply { mkdirs() }
        val file = File(imagesDir, "src_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    }

    private fun startCrop(sourceUri: Uri) {
        val imagesDir = File(cacheDir, "images").apply { mkdirs() }
        val destFile = File(imagesDir, "cropped_${System.currentTimeMillis()}.png")
        val destUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", destFile)

        val options = UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.PNG)
            setCompressionQuality(100)
            setFreeStyleCropEnabled(false)
            setToolbarTitle(getString(R.string.select_image_title))
        }

        val intent = UCrop.of(sourceUri, destUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(512, 512)
            .withOptions(options)
            .getIntent(this)
        cropLauncher.launch(intent)
    }

    private fun loadBitmapFromUri(uri: Uri) {
        contentResolver.openInputStream(uri)?.use { stream ->
            val bitmap = BitmapFactory.decodeStream(stream)
            if (bitmap != null) {
                croppedBitmap = bitmap
                refreshPreview()
            }
        }
    }

    private fun showBackgroundColorDialog() {
        val names = arrayOf(
            getString(R.string.bg_color_transparent),
            getString(R.string.bg_color_white),
            getString(R.string.bg_color_black),
            getString(R.string.bg_color_blue),
            getString(R.string.bg_color_green),
            getString(R.string.bg_color_purple),
            getString(R.string.bg_color_custom)
        )
        val values = arrayOf(
            null,
            Color.WHITE,
            Color.BLACK,
            ContextCompat.getColor(this, R.color.blue_500),
            ContextCompat.getColor(this, R.color.green_500),
            ContextCompat.getColor(this, R.color.purple_500),
            null
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.background_color_title)
            .setItems(names) { _, which ->
                if (which == values.lastIndex) {
                    showCustomColorDialog()
                } else {
                    backgroundColor = values[which]
                    refreshPreview()
                }
            }
            .show()
    }

    private fun showCustomColorDialog() {
        val dialogBinding = com.dskmusic.web2app.databinding.DialogCustomColorBinding.inflate(layoutInflater)
        var updating = false
        val initial = backgroundColor ?: Color.WHITE

        fun applyColor(color: Int) {
            dialogBinding.colorPreview.setBackgroundColor(color)
        }

        fun colorFromSliders(): Int = Color.rgb(
            dialogBinding.sbRed.progress,
            dialogBinding.sbGreen.progress,
            dialogBinding.sbBlue.progress
        )

        val sliderListener = object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || updating) return
                val color = colorFromSliders()
                updating = true
                dialogBinding.etHex.setText(String.format("#%06X", color and 0xFFFFFF))
                updating = false
                applyColor(color)
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        }
        dialogBinding.sbRed.setOnSeekBarChangeListener(sliderListener)
        dialogBinding.sbGreen.setOnSeekBarChangeListener(sliderListener)
        dialogBinding.sbBlue.setOnSeekBarChangeListener(sliderListener)

        dialogBinding.etHex.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (updating) return
                val color = try {
                    Color.parseColor(s.toString())
                } catch (e: IllegalArgumentException) {
                    return
                }
                updating = true
                dialogBinding.sbRed.progress = Color.red(color)
                dialogBinding.sbGreen.progress = Color.green(color)
                dialogBinding.sbBlue.progress = Color.blue(color)
                updating = false
                applyColor(color)
            }
        })

        dialogBinding.sbRed.progress = Color.red(initial)
        dialogBinding.sbGreen.progress = Color.green(initial)
        dialogBinding.sbBlue.progress = Color.blue(initial)
        dialogBinding.etHex.setText(String.format("#%06X", initial and 0xFFFFFF))
        applyColor(initial)

        AlertDialog.Builder(this)
            .setTitle(R.string.bg_color_custom)
            .setView(dialogBinding.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                backgroundColor = colorFromSliders()
                refreshPreview()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun refreshPreview() {
        val src = croppedBitmap ?: return
        binding.ivPreview.setImageBitmap(BitmapUtils.composeAdaptive(src, backgroundColor))
    }

    private fun generateShortcut() {
        val rawUrl = binding.etUrl.text?.toString()?.trim().orEmpty()
        val name = binding.etName.text?.toString()?.trim().orEmpty()

        if (rawUrl.isEmpty()) {
            binding.etUrl.error = getString(R.string.error_url_required)
            return
        }
        if (name.isEmpty()) {
            binding.etName.error = getString(R.string.error_name_required)
            return
        }

        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            showToast(R.string.shortcut_not_supported)
            return
        }

        val url = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) rawUrl else "https://$rawUrl"
        val id = editingId ?: "shortcut_${System.currentTimeMillis()}"

        val finalIconBitmap = croppedBitmap?.let { BitmapUtils.composeAdaptive(it, backgroundColor) }
        val icon = finalIconBitmap?.let { IconCompat.createWithAdaptiveBitmap(it) }
            ?: IconCompat.createWithResource(this, R.mipmap.ic_launcher)

        val forcedTheme = when (binding.rgShortcutTheme.checkedRadioButtonId) {
            R.id.rbShortcutLight -> WebViewActivity.THEME_LIGHT
            R.id.rbShortcutDark -> WebViewActivity.THEME_DARK
            else -> WebViewActivity.THEME_SYSTEM
        }
        val allowRotation = binding.swAllowRotation.isChecked
        val desktopMode = binding.swDesktopMode.isChecked
        val incognito = binding.swIncognito.isChecked
        Prefs.setDefaultShortcutOptions(this, forcedTheme, allowRotation, desktopMode, incognito)

        val intent = Intent(this, WebViewActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(WebViewActivity.EXTRA_URL, url)
            putExtra(WebViewActivity.EXTRA_SHORTCUT_ID, id)
            putExtra(WebViewActivity.EXTRA_FORCE_THEME, forcedTheme)
            putExtra(WebViewActivity.EXTRA_ALLOW_ROTATION, allowRotation)
            putExtra(WebViewActivity.EXTRA_DESKTOP_MODE, desktopMode)
            putExtra(WebViewActivity.EXTRA_INCOGNITO, incognito)
        }

        val shortcutInfo = ShortcutInfoCompat.Builder(this, id)
            .setShortLabel(name)
            .setLongLabel(name)
            .setIcon(icon)
            .setIntent(intent)
            .build()

        persistShortcutRecord(id, name, url, forcedTheme, allowRotation, desktopMode, incognito, finalIconBitmap)

        if (editingId != null) {
            ShortcutManagerCompat.updateShortcuts(this, listOf(shortcutInfo))
            showToast(R.string.shortcut_updated)
        } else {
            ShortcutManagerCompat.requestPinShortcut(this, shortcutInfo, null)
            showToast(R.string.shortcut_created)
        }
        resetForm()
        moveTaskToBack(true)
    }

    /**
     * moveTaskToBack() only backgrounds this Activity instance rather than finishing it, and
     * standard launch mode resumes that same instance (no onCreate/onNewIntent) when the user
     * later taps the launcher icon. Without this, re-opening the app could silently resume with
     * a stale editingId and the previous shortcut's data still filled in.
     */
    private fun resetForm() {
        editingId = null
        croppedBitmap = null
        backgroundColor = null
        binding.etUrl.text = null
        binding.etName.text = null
        binding.ivPreview.setImageResource(R.mipmap.ic_launcher)
        applyDefaultShortcutOptions()
        binding.btnGenerate.setText(R.string.generate_shortcut)
    }

    private fun persistShortcutRecord(
        id: String,
        name: String,
        url: String,
        forcedTheme: String,
        allowRotation: Boolean,
        desktopMode: Boolean,
        incognito: Boolean,
        finalIcon: Bitmap?
    ) {
        croppedBitmap?.let { raw ->
            FileOutputStream(ShortcutStore.sourceIconFile(this, id)).use { raw.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        finalIcon?.let { icon ->
            FileOutputStream(ShortcutStore.iconFile(this, id)).use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        ShortcutStore.upsert(
            this,
            SavedShortcut(
                id = id,
                name = name,
                url = url,
                backgroundColor = backgroundColor,
                forcedTheme = forcedTheme,
                allowRotation = allowRotation,
                desktopMode = desktopMode,
                incognito = incognito,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private fun showToast(resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_EDIT_ID = "extra_edit_id"
    }
}
