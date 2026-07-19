package com.dskmusic.web2app

import android.Manifest
import android.app.Dialog
import android.app.DownloadManager
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.dskmusic.web2app.databinding.ActivityWebviewBinding

class WebViewActivity : BaseActivity() {

    private lateinit var binding: ActivityWebviewBinding
    private var incognito = false
    private var popupDialog: Dialog? = null

    private val storagePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(this, R.string.download_permission_needed, Toast.LENGTH_SHORT).show()
    }

    override fun useNoActionBar(): Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDataDirectorySuffix()
        binding = ActivityWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestedOrientation = if (intent.getBooleanExtra(EXTRA_ALLOW_ROTATION, true)) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        incognito = intent.getBooleanExtra(EXTRA_INCOGNITO, false)
        if (incognito) clearBrowsingData()

        val url = intent.getStringExtra(EXTRA_URL)

        binding.webView.settings.javaScriptEnabled = true
        binding.webView.settings.domStorageEnabled = true
        binding.webView.webViewClient = WebViewClient()
        setupPopupSupport()
        setupDownloads()
        hideWebViewUserAgentMarker()
        applyForcedTheme()
        applyDesktopMode()
        applyZoom()
        applySelectionLock()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    confirmExit()
                }
            }
        })

        if (url != null) {
            binding.webView.loadUrl(url)
        }
    }

    override fun onDestroy() {
        popupDialog?.dismiss()
        if (incognito) clearBrowsingData()
        super.onDestroy()
    }

    /**
     * A plain WebView can't open real popup windows (window.open()), which OAuth flows like
     * "Sign in with Google" rely on — without this the click silently does nothing and the page
     * is left blank once the (never-shown) popup would have redirected back. This creates a real
     * WebView for the popup, shown in a floating dialog, that closes itself once the flow calls
     * window.close() (or navigates away, which the dialog's own back/dismiss then handles).
     */
    private fun setupPopupSupport() {
        binding.webView.settings.setSupportMultipleWindows(true)
        binding.webView.settings.javaScriptCanOpenWindowsAutomatically = true
        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                val popupWebView = WebView(this@WebViewActivity).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = binding.webView.settings.userAgentString
                    webViewClient = WebViewClient()
                }
                popupDialog?.dismiss()
                popupDialog = Dialog(this@WebViewActivity).apply {
                    setContentView(popupWebView)
                    setOnDismissListener { popupWebView.destroy() }
                    show()
                }
                (resultMsg.obj as WebView.WebViewTransport).webView = popupWebView
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView) {
                popupDialog?.dismiss()
                popupDialog = null
            }
        }
    }

    /** WebView doesn't download anything on its own; DownloadManager does the actual fetching. */
    private fun setupDownloads() {
        binding.webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            ) {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return@setDownloadListener
            }
            runCatching {
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimeType)
                    addRequestHeader("User-Agent", userAgent)
                    addRequestHeader("cookie", CookieManager.getInstance().getCookie(url))
                    setTitle(fileName)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                }
                (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this, R.string.download_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmExit() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.exit_confirm_title)
            .setMessage(R.string.exit_confirm_message)
            .setPositiveButton(R.string.exit_confirm_positive) { _, _ -> finish() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun clearBrowsingData() {
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
        binding.webView.clearCache(true)
        binding.webView.clearHistory()
        binding.webView.clearFormData()
    }

    /**
     * Gives each shortcut its own cookie/cache/localStorage directory instead of sharing one
     * global WebView profile. Must run before any WebView is constructed in this process.
     */
    private fun applyDataDirectorySuffix() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val shortcutId = intent.getStringExtra(EXTRA_SHORTCUT_ID) ?: return
        try {
            WebView.setDataDirectorySuffix(shortcutId.replace(Regex("[^A-Za-z0-9_]"), "_"))
        } catch (e: IllegalStateException) {
            // ponytail: another shortcut's WebView already claimed this process's data directory
            // this session (falls back to the shared/default one until the process restarts).
            // Full concurrent isolation would need a process-per-shortcut manifest split.
        }
    }

    /**
     * The default WebView user agent includes a "; wv)" marker identifying it as an embedded
     * WebView. Google (and some other sites) deliberately reject OAuth/login flows from that
     * marker (error 403 disallowed_useragent) as an anti-phishing measure, even though the page
     * otherwise renders and behaves identically. Stripping it makes the UA match a regular Chrome
     * browser's, unblocking those flows. Overwritten by applyDesktopMode() when that's enabled,
     * since its full desktop UA string doesn't carry the marker either.
     */
    private fun hideWebViewUserAgentMarker() {
        val settings = binding.webView.settings
        settings.userAgentString = settings.userAgentString.replace("; wv", "")
    }

    private fun applyDesktopMode() {
        if (!intent.getBooleanExtra(EXTRA_DESKTOP_MODE, false)) return
        binding.webView.settings.userAgentString = DESKTOP_USER_AGENT
        binding.webView.settings.useWideViewPort = true
        binding.webView.settings.loadWithOverviewMode = true
        binding.webView.settings.builtInZoomControls = true
        binding.webView.settings.displayZoomControls = false
    }

    private fun applyZoom() {
        val allowZoom = intent.getBooleanExtra(EXTRA_ALLOW_ZOOM, false)
        binding.webView.settings.setSupportZoom(allowZoom)
        binding.webView.settings.builtInZoomControls = allowZoom
        binding.webView.settings.displayZoomControls = false
    }

    /**
     * When off (default), long-pressing swallows the event everywhere except on actual text
     * fields (via HitTestResult.EDIT_TEXT_TYPE), so pages don't pop up text/image selection or
     * a context menu on a long press, while copy/paste inside inputs keeps working normally.
     */
    private fun applySelectionLock() {
        val allowSelection = intent.getBooleanExtra(EXTRA_ALLOW_SELECTION, false)
        if (allowSelection) return
        binding.webView.setOnLongClickListener {
            binding.webView.hitTestResult.type != WebView.HitTestResult.EDIT_TEXT_TYPE
        }
    }

    private fun applyForcedTheme() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) return
        val forced = intent.getStringExtra(EXTRA_FORCE_THEME) ?: THEME_SYSTEM
        val dark = when (forced) {
            THEME_DARK -> true
            THEME_LIGHT -> false
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(binding.webView.settings, dark)
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_SHORTCUT_ID = "extra_shortcut_id"
        const val EXTRA_FORCE_THEME = "extra_force_theme"
        const val EXTRA_ALLOW_ROTATION = "extra_allow_rotation"
        const val EXTRA_DESKTOP_MODE = "extra_desktop_mode"
        const val EXTRA_INCOGNITO = "extra_incognito"
        const val EXTRA_ALLOW_ZOOM = "extra_allow_zoom"
        const val EXTRA_ALLOW_SELECTION = "extra_allow_selection"
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
