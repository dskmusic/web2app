package com.dskmusic.web2app

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.dskmusic.web2app.databinding.ActivityWebviewBinding

class WebViewActivity : BaseActivity() {

    private lateinit var binding: ActivityWebviewBinding
    private var incognito = false

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
        binding.webView.webChromeClient = WebChromeClient()
        applyForcedTheme()
        applyDesktopMode()

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
        if (incognito) clearBrowsingData()
        super.onDestroy()
    }

    private fun confirmExit() {
        AlertDialog.Builder(this)
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

    private fun applyDesktopMode() {
        if (!intent.getBooleanExtra(EXTRA_DESKTOP_MODE, false)) return
        binding.webView.settings.userAgentString = DESKTOP_USER_AGENT
        binding.webView.settings.useWideViewPort = true
        binding.webView.settings.loadWithOverviewMode = true
        binding.webView.settings.builtInZoomControls = true
        binding.webView.settings.displayZoomControls = false
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
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
