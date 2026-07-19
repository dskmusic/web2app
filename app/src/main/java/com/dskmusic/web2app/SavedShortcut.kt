package com.dskmusic.web2app

data class SavedShortcut(
    val id: String,
    val name: String,
    val url: String,
    val backgroundColor: Int?,
    val forcedTheme: String,
    val allowRotation: Boolean,
    val desktopMode: Boolean,
    val incognito: Boolean,
    val allowZoom: Boolean,
    val allowSelection: Boolean,
    val createdAt: Long
)
