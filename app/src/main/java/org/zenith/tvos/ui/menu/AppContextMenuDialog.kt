package org.zenith.tvos.ui.menu

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import org.zenith.tvos.R
import org.zenith.tvos.core.focus.SpatialFocusEngine
import org.zenith.tvos.core.menu.AppContextMenuEngine
import org.zenith.tvos.ui.dock.AppItem

/**
 * Apple TV style frosted glass modal context menu.
 * Triggered on D-pad Center long-press to manage pinning, reordering, info, hiding, and removal.
 */
class AppContextMenuDialog(
    context: Context,
    private val appItem: AppItem,
    private val isPinned: Boolean,
    private val focusEngine: SpatialFocusEngine,
    private val onTogglePin: (AppItem) -> Unit,
    private val onReorder: (AppItem) -> Unit,
    private val onAppInfo: (AppItem) -> Unit,
    private val onHideApp: (AppItem) -> Unit,
    private val onUninstall: (AppItem) -> Unit
) : Dialog(context) {

    private val menuEngine = AppContextMenuEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setContentView(R.layout.dialog_context_menu)

        val appIcon = findViewById<ImageView>(R.id.menuAppIcon)
        val appTitle = findViewById<TextView>(R.id.menuAppTitle)
        val appMeta = findViewById<TextView>(R.id.menuAppMeta)

        val btnTogglePin = findViewById<Button>(R.id.btnTogglePin)
        val btnReorder = findViewById<Button>(R.id.btnReorder)
        val btnAppInfo = findViewById<Button>(R.id.btnAppInfo)
        val btnHideApp = findViewById<Button>(R.id.btnHideApp)
        val btnUninstall = findViewById<Button>(R.id.btnUninstall)

        // Populate header
        appTitle.text = appItem.label
        appMeta.text = "${appItem.category.displayName.uppercase()} • ${if (appItem.isSystemApp) "SYSTEM" else "USER APP"}"
        if (appItem.iconBitmap != null) {
            appIcon.setImageBitmap(appItem.iconBitmap)
        } else {
            appIcon.setImageResource(R.drawable.ic_tvos_logo)
        }

        // Configure Pin button
        btnTogglePin.text = if (isPinned) "⭐ Remove from Favorites" else "📌 Pin to Favorites"

        // Configure Uninstall button
        if (appItem.isSystemApp) {
            btnUninstall.visibility = View.GONE
        } else {
            btnUninstall.visibility = View.VISIBLE
        }

        // Attach focus engine for tvOS tactile feedback
        val buttons = listOf(btnTogglePin, btnReorder, btnAppInfo, btnHideApp, btnUninstall)
        for (btn in buttons) {
            btn.setOnFocusChangeListener { view, hasFocus ->
                val b = view as Button
                if (hasFocus) {
                    b.setTextColor(if (b == btnUninstall) Color.RED else Color.BLACK)
                } else {
                    b.setTextColor(if (b == btnUninstall) Color.parseColor("#FF453A") else Color.WHITE)
                }
            }
        }

        // Action listeners
        btnTogglePin.setOnClickListener {
            dismiss()
            onTogglePin(appItem)
        }

        btnReorder.setOnClickListener {
            dismiss()
            onReorder(appItem)
        }

        btnAppInfo.setOnClickListener {
            dismiss()
            onAppInfo(appItem)
        }

        btnHideApp.setOnClickListener {
            dismiss()
            onHideApp(appItem)
        }

        btnUninstall.setOnClickListener {
            dismiss()
            onUninstall(appItem)
        }

        // Default focus on first item
        btnTogglePin.requestFocus()
    }
}
