package org.zenith.tvos.ui

import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.zenith.tvos.R
import org.zenith.tvos.core.audio.TactileAudioEngine
import org.zenith.tvos.core.cache.UltraLightIconCache
import org.zenith.tvos.core.focus.SpatialFocusEngine
import org.zenith.tvos.core.input.DPadDebounceDispatcher
import org.zenith.tvos.core.library.AppLibraryManager
import org.zenith.tvos.core.library.SharedPreferencesAppStorage
import org.zenith.tvos.core.menu.AppContextMenuEngine
import org.zenith.tvos.core.parallax.ParallaxBackdropEngine
import org.zenith.tvos.core.performance.FramePacer
import org.zenith.tvos.core.performance.SystemHealthEngine
import org.zenith.tvos.core.recommendation.OfflineTopShelfContentEngine
import org.zenith.tvos.core.remote.ZenithRemoteServer
import org.zenith.tvos.core.screensaver.IdleScreensaverEngine
import org.zenith.tvos.core.search.FuzzyAppSearchEngine
import org.zenith.tvos.core.security.IntentSanitizer
import org.zenith.tvos.core.security.PackageChangeReceiver
import org.zenith.tvos.core.settings.SharedPreferencesSettingsStorage
import org.zenith.tvos.core.settings.WallpaperMode
import org.zenith.tvos.core.settings.ZenithSettingsManager
import org.zenith.tvos.core.wallpaper.DynamicWallpaperEngine
import org.zenith.tvos.ui.dock.AppCategory
import org.zenith.tvos.ui.dock.AppCategoryClassifier
import org.zenith.tvos.ui.dock.AppDockAdapter
import org.zenith.tvos.ui.dock.AppItem
import org.zenith.tvos.ui.menu.AerialScreensaverDialog
import org.zenith.tvos.ui.menu.AppContextMenuDialog
import org.zenith.tvos.ui.menu.PhoneRemotePairingDialog
import org.zenith.tvos.ui.menu.QuickAppSwitcherDialog
import org.zenith.tvos.ui.menu.SpotlightSearchDialog
import org.zenith.tvos.ui.menu.ZenithSettingsDialog
import org.zenith.tvos.ui.topshelf.TopShelfHeroView

/**
 * Flagship Zenith TV Main Launcher Activity.
 *
 * Core Architecture & Features:
 * - Pixel-perfect width alignment between TopShelf Hero and Frosted Dock
 * - Authentic Apple TV pill action buttons with fluid focus scaling
 * - Zero-install local Wi-Fi Phone Remote Web Server & Companion App Support
 * - Procedural PCM 16-bit Tactile Audio Engine (zero asset bloat)
 * - Apple TV Aerial Ambient Screensaver with OLED pixel-shift protection
 * - Glassmorphic Zenith Settings, 60FPS Telemetry HUD, Smart RAM Boost & Multitasking Switcher
 */
class MainActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val iconCache = UltraLightIconCache(maxCacheBytes = 12 * 1024 * 1024)
    private val focusEngine = SpatialFocusEngine(useSpringPhysics = true)
    private val parallaxEngine = ParallaxBackdropEngine()
    private val wallpaperEngine = DynamicWallpaperEngine()
    private val topShelfEngine = OfflineTopShelfContentEngine()
    private val dPadDebouncer = DPadDebounceDispatcher()
    private val framePacer = FramePacer()
    private val healthEngine = SystemHealthEngine(framePacer, iconCache)
    private val menuEngine = AppContextMenuEngine()
    private val remoteServer = ZenithRemoteServer(port = 8080)
    private val audioEngine = TactileAudioEngine()
    private val screensaverEngine = IdleScreensaverEngine()
    private val searchEngine = FuzzyAppSearchEngine()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var libraryManager: AppLibraryManager
    private lateinit var settingsManager: ZenithSettingsManager
    private lateinit var rootContainer: View
    private lateinit var topShelfHero: TopShelfHeroView
    private lateinit var tvTelemetryHud: TextView
    private lateinit var btnSpotlightSearch: Button
    private lateinit var btnPhoneRemote: Button
    private lateinit var btnZenithSettings: Button
    private lateinit var focusedAppName: TextView
    private lateinit var appRecyclerView: RecyclerView
    private var packageReceiver: PackageChangeReceiver? = null
    private var activeScreensaverDialog: AerialScreensaverDialog? = null
    private var activeSpotlightDialog: SpotlightSearchDialog? = null

    private val displayedApps = mutableListOf<AppItem>()
    private var currentCategoryFilter: AppCategory? = null
    private var isViewingFavorites: Boolean = true
    private var isViewingRecent: Boolean = false

    // Currently focused app tracker for Top Shelf action dispatch
    private var currentFocusedApp: AppItem? = null
    private var currentFocusedPos: Int = 0

    // Interactive Remote Reorder State
    private var isReordering: Boolean = false
    private var reorderCurrentPos: Int = -1

    // Category Pill Buttons
    private lateinit var pillFavorites: Button
    private lateinit var pillRecent: Button
    private lateinit var pillAll: Button
    private lateinit var pillStreaming: Button
    private lateinit var pillMusic: Button
    private lateinit var pillGaming: Button
    private lateinit var pillTools: Button
    private lateinit var pillSystem: Button

    private val idleWatcherRunnable = object : Runnable {
        override fun run() {
            if (screensaverEngine.checkIdleState()) {
                launchAerialScreensaver()
            }
            mainHandler.postDelayed(this, 5_000L)
        }
    }

    private val hudTickerRunnable = object : Runnable {
        override fun run() {
            if (settingsManager.isHudOverlayEnabled) {
                val snap = healthEngine.captureSnapshot()
                tvTelemetryHud.text = snap.formatHudLine()
                mainHandler.postDelayed(this, 1_000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        libraryManager = AppLibraryManager(SharedPreferencesAppStorage(this))
        settingsManager = ZenithSettingsManager(SharedPreferencesSettingsStorage(this))

        rootContainer = findViewById(R.id.rootContainer)
        topShelfHero = findViewById(R.id.topShelfHero)
        tvTelemetryHud = findViewById(R.id.tvTelemetryHud)
        btnSpotlightSearch = findViewById(R.id.btnSpotlightSearch)
        btnPhoneRemote = findViewById(R.id.btnPhoneRemote)
        btnZenithSettings = findViewById(R.id.btnZenithSettings)
        focusedAppName = findViewById(R.id.focusedAppName)
        appRecyclerView = findViewById(R.id.appRecyclerView)

        syncSettingsState()
        initCategoryPills()
        setupTopShelfActions()
        initPhoneRemoteServer()
        initUtilityButtons()

        setupRecyclerView()
        loadInstalledApps()
        registerPackageReceiver()
    }

    private fun syncSettingsState() {
        audioEngine.isEnabled = settingsManager.isSoundEnabled
        screensaverEngine.timeoutMs = settingsManager.screensaverTimeoutSec * 1000L
        applyCurrentWallpaper(currentFocusedApp?.category)

        if (settingsManager.isHudOverlayEnabled) {
            tvTelemetryHud.visibility = View.VISIBLE
            tvTelemetryHud.text = healthEngine.captureSnapshot().formatHudLine()
            mainHandler.removeCallbacks(hudTickerRunnable)
            mainHandler.postDelayed(hudTickerRunnable, 1_000L)
        } else {
            tvTelemetryHud.visibility = View.GONE
            mainHandler.removeCallbacks(hudTickerRunnable)
        }
    }

    private fun applyCurrentWallpaper(category: AppCategory?) {
        val theme = when (settingsManager.wallpaperMode) {
            WallpaperMode.DYNAMIC_CATEGORY -> {
                if (category != null) {
                    wallpaperEngine.getThemeForCategory(category.name)
                } else {
                    DynamicWallpaperEngine.Theme.MIDNIGHT_OBSIDIAN
                }
            }
            WallpaperMode.MIDNIGHT_OBSIDIAN -> DynamicWallpaperEngine.Theme.MIDNIGHT_OBSIDIAN
            WallpaperMode.COSMIC_AURORA -> DynamicWallpaperEngine.Theme.COSMIC_AURORA
            WallpaperMode.DEEP_OCEAN -> DynamicWallpaperEngine.Theme.AERIAL_EARTH
            WallpaperMode.SOLAR_NEBULA -> DynamicWallpaperEngine.Theme.SOLAR_ECLIPSE
        }
        rootContainer.background = wallpaperEngine.createWallpaperDrawable(theme)
    }

    private fun initUtilityButtons() {
        btnSpotlightSearch.setOnClickListener {
            audioEngine.playSelectPop()
            showSpotlightSearch("")
        }

        btnSpotlightSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                audioEngine.playFocusTick()
                btnSpotlightSearch.setTextColor(Color.BLACK)
            } else {
                btnSpotlightSearch.setTextColor(Color.parseColor("#F5F5F7"))
            }
        }

        btnZenithSettings.setOnClickListener {
            audioEngine.playSelectPop()
            showSettingsDialog()
        }

        btnZenithSettings.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                audioEngine.playFocusTick()
                btnZenithSettings.setTextColor(Color.BLACK)
            } else {
                btnZenithSettings.setTextColor(Color.parseColor("#F5F5F7"))
            }
        }
    }

    private fun showSpotlightSearch(initialQuery: String = "") {
        val existing = activeSpotlightDialog
        if (existing != null && existing.isShowing) {
            existing.updateExternalQuery(initialQuery)
            return
        }
        val dialog = SpotlightSearchDialog(
            context = this,
            allVisibleApps = libraryManager.getVisibleApps(),
            searchEngine = searchEngine,
            audioEngine = audioEngine,
            onLaunchApp = { app ->
                launchAppSafely(app)
            }
        )
        activeSpotlightDialog = dialog
        dialog.show()
        if (initialQuery.isNotEmpty()) {
            dialog.updateExternalQuery(initialQuery)
        }
    }

    private fun showQuickAppSwitcher() {
        val recents = libraryManager.getRecentApps().ifEmpty { libraryManager.getFavorites() }
        if (recents.isEmpty()) return
        QuickAppSwitcherDialog(
            context = this,
            recentApps = recents,
            focusEngine = focusEngine,
            audioEngine = audioEngine,
            onSelectApp = { app ->
                launchAppSafely(app)
            }
        ).show()
    }

    private fun showSettingsDialog() {
        ZenithSettingsDialog(
            context = this,
            settingsManager = settingsManager,
            libraryManager = libraryManager,
            audioEngine = audioEngine,
            onSettingsChanged = {
                syncSettingsState()
                updateDisplayedApps()
            },
            onSmartRamBoost = {
                val reclaimedBytes = healthEngine.performSmartRamBoost()
                val reclaimedKb = (reclaimedBytes / 1024L).coerceAtLeast(128L)
                syncSettingsState()
                Toast.makeText(this, "⚡ Smart RAM Boost complete (~${reclaimedKb} KB freed)", Toast.LENGTH_SHORT).show()
            },
            onStartScreensaverNow = {
                screensaverEngine.activateNow()
                launchAerialScreensaver()
            }
        ).show()
    }

    private fun launchAerialScreensaver() {
        if (activeScreensaverDialog?.isShowing == true) return
        val dialog = AerialScreensaverDialog(
            context = this,
            wallpaperEngine = wallpaperEngine,
            onWakeUp = {
                screensaverEngine.dismiss()
                activeScreensaverDialog = null
            }
        )
        activeScreensaverDialog = dialog
        dialog.show()
    }

    private fun initPhoneRemoteServer() {
        val ip = ZenithRemoteServer.getLocalIpAddress() ?: "127.0.0.1"
        val remoteUrl = "http://$ip:8080"
        btnPhoneRemote.text = "📱 Remote: $ip:8080"

        btnPhoneRemote.setOnClickListener {
            audioEngine.playSelectPop()
            PhoneRemotePairingDialog(this, remoteUrl).show()
        }

        btnPhoneRemote.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                audioEngine.playFocusTick()
                btnPhoneRemote.setTextColor(Color.BLACK)
            } else {
                btnPhoneRemote.setTextColor(Color.parseColor("#F5F5F7"))
            }
        }

        remoteServer.appListProvider = {
            libraryManager.getVisibleApps().map { app ->
                ZenithRemoteServer.RemoteAppEntry(
                    packageName = app.packageName,
                    label = app.label,
                    category = app.category.displayName
                )
            }
        }

        remoteServer.onLaunchPackageRequested = { pkg ->
            val targetApp = libraryManager.getVisibleApps().find { it.packageName == pkg }
            if (targetApp != null) {
                runOnUiThread {
                    screensaverEngine.recordInteraction()
                    audioEngine.playSelectPop()
                    launchAppSafely(targetApp)
                }
                true
            } else {
                false
            }
        }

        remoteServer.onKeyReceived = { keyCode ->
            runOnUiThread {
                screensaverEngine.recordInteraction()
                if (activeScreensaverDialog?.isShowing == true) {
                    activeScreensaverDialog?.dismiss()
                    activeScreensaverDialog = null
                } else {
                    injectKeyEvent(keyCode)
                }
            }
        }

        remoteServer.onTextReceived = { text ->
            runOnUiThread {
                screensaverEngine.recordInteraction()
                audioEngine.playSelectPop()
                showSpotlightSearch(text)
            }
        }

        remoteServer.start()
    }

    private fun injectKeyEvent(keyCode: Int) {
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun setupTopShelfActions() {
        topShelfHero.primaryActionBtn.nextFocusDownId = R.id.pillFavorites
        topShelfHero.secondaryActionBtn.nextFocusDownId = R.id.pillFavorites
        topShelfHero.primaryActionBtn.nextFocusUpId = R.id.btnSpotlightSearch
        topShelfHero.secondaryActionBtn.nextFocusUpId = R.id.btnPhoneRemote

        topShelfHero.onPlayActionClicked = {
            audioEngine.playSelectPop()
            currentFocusedApp?.let { app ->
                launchAppSafely(app)
            }
        }

        topShelfHero.onDetailsActionClicked = {
            audioEngine.playSelectPop()
            currentFocusedApp?.let { app ->
                showContextMenu(app, currentFocusedPos)
            }
        }
    }

    private fun initCategoryPills() {
        pillFavorites = findViewById(R.id.pillFavorites)
        pillRecent = findViewById(R.id.pillRecent)
        pillAll = findViewById(R.id.pillAll)
        pillStreaming = findViewById(R.id.pillStreaming)
        pillMusic = findViewById(R.id.pillMusic)
        pillGaming = findViewById(R.id.pillGaming)
        pillTools = findViewById(R.id.pillTools)
        pillSystem = findViewById(R.id.pillSystem)

        val pills = listOf(
            pillFavorites to { filterFavorites() },
            pillRecent to { filterRecent() },
            pillAll to { filterCategory(null) },
            pillStreaming to { filterCategory(AppCategory.STREAMING) },
            pillMusic to { filterCategory(AppCategory.MUSIC) },
            pillGaming to { filterCategory(AppCategory.GAMING) },
            pillTools to { filterCategory(AppCategory.TOOLS) },
            pillSystem to { filterCategory(AppCategory.SYSTEM) }
        )

        for ((pill, action) in pills) {
            pill.setOnClickListener {
                audioEngine.playSelectPop()
                resetPillSelection()
                pill.isSelected = true
                action()
            }
            pill.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    audioEngine.playFocusTick()
                    pill.setTextColor(Color.BLACK)
                } else {
                    pill.setTextColor(Color.parseColor("#F5F5F7"))
                }
            }
        }
        pillFavorites.isSelected = true
    }

    private fun resetPillSelection() {
        pillFavorites.isSelected = false
        pillRecent.isSelected = false
        pillAll.isSelected = false
        pillStreaming.isSelected = false
        pillMusic.isSelected = false
        pillGaming.isSelected = false
        pillTools.isSelected = false
        pillSystem.isSelected = false
    }

    override fun onResume() {
        super.onResume()
        framePacer.start()
        screensaverEngine.recordInteraction()
        mainHandler.removeCallbacks(idleWatcherRunnable)
        mainHandler.postDelayed(idleWatcherRunnable, 5_000L)
        if (settingsManager.isHudOverlayEnabled) {
            mainHandler.removeCallbacks(hudTickerRunnable)
            mainHandler.postDelayed(hudTickerRunnable, 1_000L)
        }
    }

    override fun onPause() {
        super.onPause()
        framePacer.stop()
        dPadDebouncer.reset()
        mainHandler.removeCallbacks(idleWatcherRunnable)
        mainHandler.removeCallbacks(hudTickerRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(idleWatcherRunnable)
        mainHandler.removeCallbacks(hudTickerRunnable)
        remoteServer.stop()
        packageReceiver?.let { unregisterReceiver(it) }
        iconCache.evictAll()
        scope.cancel()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        iconCache.onTrimMemory(level)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        screensaverEngine.recordInteraction()
        if (!dPadDebouncer.shouldDispatchKeyEvent(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun setupRecyclerView() {
        appRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        appRecyclerView.setHasFixedSize(true)
        appRecyclerView.setItemViewCacheSize(10)
    }

    private fun loadInstalledApps() {
        scope.launch {
            val apps = withContext(Dispatchers.IO) {
                queryInstalledTvApps()
            }
            libraryManager.setInstalledApps(apps)
            updateDisplayedApps()
        }
    }

    private fun filterFavorites() {
        if (isReordering) commitReorder()
        isViewingFavorites = true
        isViewingRecent = false
        currentCategoryFilter = null
        updateDisplayedApps()
    }

    private fun filterRecent() {
        if (isReordering) commitReorder()
        isViewingFavorites = false
        isViewingRecent = true
        currentCategoryFilter = null
        updateDisplayedApps()
        showQuickAppSwitcher()
    }

    private fun filterCategory(category: AppCategory?) {
        if (isReordering) commitReorder()
        isViewingFavorites = false
        isViewingRecent = false
        currentCategoryFilter = category
        updateDisplayedApps()
    }

    private fun updateDisplayedApps() {
        val apps = when {
            isViewingFavorites -> libraryManager.getFavorites()
            isViewingRecent -> libraryManager.getRecentApps().ifEmpty { libraryManager.getFavorites() }
            else -> libraryManager.getFilteredApps(currentCategoryFilter)
        }

        displayedApps.clear()
        displayedApps.addAll(apps)

        val adapter = AppDockAdapter(
            apps = displayedApps,
            focusEngine = focusEngine,
            onAppFocused = { item, position ->
                audioEngine.playFocusTick()
                if (!isReordering) {
                    currentFocusedApp = item
                    currentFocusedPos = position
                    focusedAppName.text = item.label

                    // Offline contextual Top Shelf content resolution
                    val heroContent = topShelfEngine.resolveContent(item)
                    topShelfHero.updateHero(heroContent)

                    // Apple TV Dynamic 2.5D Parallax Shift (Internal content translation)
                    val ratio = parallaxEngine.computeNormalizedRatio(position, displayedApps.size)
                    val transX = parallaxEngine.computeBackdropTranslationX(ratio)
                    topShelfHero.applyParallax(transX)

                    // Apply selected or dynamic category wallpaper
                    applyCurrentWallpaper(item.category)
                }
            },
            onAppClicked = { item ->
                audioEngine.playSelectPop()
                if (isReordering) {
                    commitReorder()
                } else {
                    launchAppSafely(item)
                }
            },
            onAppLongClicked = { item, position ->
                if (!isReordering) {
                    audioEngine.playSelectPop()
                    showContextMenu(item, position)
                }
            }
        )
        appRecyclerView.adapter = adapter

        if (displayedApps.isNotEmpty()) {
            val first = displayedApps[0]
            currentFocusedApp = first
            currentFocusedPos = 0
            focusedAppName.text = first.label
            val heroContent = topShelfEngine.resolveContent(first)
            topShelfHero.updateHero(heroContent)
            applyCurrentWallpaper(first.category)
        } else {
            currentFocusedApp = null
            focusedAppName.text = "No applications in this category"
        }
    }

    private fun showContextMenu(item: AppItem, position: Int) {
        val isPinned = libraryManager.isFavorite(item.packageName)
        val dialog = AppContextMenuDialog(
            context = this,
            appItem = item,
            isPinned = isPinned,
            focusEngine = focusEngine,
            onTogglePin = { app ->
                audioEngine.playSelectPop()
                if (isPinned) {
                    libraryManager.removeFromFavorites(app.packageName)
                    Toast.makeText(this, "Removed ${app.label} from Favorites", Toast.LENGTH_SHORT).show()
                } else {
                    libraryManager.pinToFavorites(app.packageName)
                    Toast.makeText(this, "Pinned ${app.label} to Favorites", Toast.LENGTH_SHORT).show()
                }
                updateDisplayedApps()
            },
            onReorder = { _ ->
                audioEngine.playSelectPop()
                startReorder(position)
            },
            onAppInfo = { app ->
                audioEngine.playSelectPop()
                val intent = menuEngine.createAppInfoIntent(app.packageName)
                if (intent != null) {
                    try {
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Cannot open App Info", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onHideApp = { app ->
                audioEngine.playSelectPop()
                libraryManager.hideApp(app.packageName)
                Toast.makeText(this, "Hidden ${app.label} from Home", Toast.LENGTH_SHORT).show()
                updateDisplayedApps()
            },
            onUninstall = { app ->
                audioEngine.playSelectPop()
                val intent = menuEngine.createUninstallIntent(app.packageName, app.isSystemApp)
                if (intent != null) {
                    try {
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Cannot prompt uninstall", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        dialog.show()
    }

    private fun startReorder(position: Int) {
        isReordering = true
        reorderCurrentPos = position
        focusedAppName.text = "⇄ REORDERING: D-Pad ◀/▶ to move, Center/Back to confirm"
        val adapter = appRecyclerView.adapter as? AppDockAdapter
        adapter?.setReorderMode(true, position)
    }

    private fun commitReorder() {
        if (!isReordering) return
        isReordering = false
        val adapter = appRecyclerView.adapter as? AppDockAdapter
        adapter?.setReorderMode(false)
        if (reorderCurrentPos in displayedApps.indices) {
            focusedAppName.text = displayedApps[reorderCurrentPos].label
        }
        reorderCurrentPos = -1
        Toast.makeText(this, "Layout arrangement saved", Toast.LENGTH_SHORT).show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isReordering) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (reorderCurrentPos > 0) {
                        audioEngine.playFocusTick()
                        val targetPos = reorderCurrentPos - 1
                        if (isViewingFavorites) {
                            libraryManager.moveFavorite(reorderCurrentPos, targetPos)
                        }
                        val adapter = appRecyclerView.adapter as? AppDockAdapter
                        adapter?.swapItems(reorderCurrentPos, targetPos)
                        reorderCurrentPos = targetPos
                        appRecyclerView.scrollToPosition(targetPos)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (reorderCurrentPos < displayedApps.size - 1) {
                        audioEngine.playFocusTick()
                        val targetPos = reorderCurrentPos + 1
                        if (isViewingFavorites) {
                            libraryManager.moveFavorite(reorderCurrentPos, targetPos)
                        }
                        val adapter = appRecyclerView.adapter as? AppDockAdapter
                        adapter?.swapItems(reorderCurrentPos, targetPos)
                        reorderCurrentPos = targetPos
                        appRecyclerView.scrollToPosition(targetPos)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK -> {
                    audioEngine.playSelectPop()
                    commitReorder()
                    return true
                }
            }
        }

        // Open Multitasking App Switcher on APP_SWITCH key
        if (keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
            audioEngine.playSelectPop()
            showQuickAppSwitcher()
            return true
        }

        // Open Zenith Spotlight Search on remote SEARCH key
        if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            audioEngine.playSelectPop()
            showSpotlightSearch("")
            return true
        }

        // Open Zenith Settings on remote MENU key
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            audioEngine.playSelectPop()
            showSettingsDialog()
            return true
        }

        // Intercept back key to prevent launcher destruction
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!isViewingFavorites) {
                audioEngine.playFocusTick()
                resetPillSelection()
                pillFavorites.isSelected = true
                filterFavorites()
                pillFavorites.requestFocus()
                return true
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun queryInstalledTvApps(): List<AppItem> {
        val pm = packageManager
        val items = mutableListOf<AppItem>()

        val tvIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        var resolveList = pm.queryIntentActivities(tvIntent, 0)

        if (resolveList.isEmpty()) {
            val standardIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            resolveList = pm.queryIntentActivities(standardIntent, 0)
        }

        for (resolveInfo in resolveList) {
            val pkg = resolveInfo.activityInfo.packageName
            val cls = resolveInfo.activityInfo.name

            if (pkg == packageName) continue

            val label = resolveInfo.loadLabel(pm).toString()
            val cachedBitmap = iconCache.get(pkg) ?: run {
                val drawable = resolveInfo.loadIcon(pm)
                val bmp = iconCache.drawableToOptimizedBitmap(drawable, targetSize = 96)
                iconCache.put(pkg, bmp)
                bmp
            }

            val isSystem = (resolveInfo.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val category = AppCategoryClassifier.classify(pkg, label, isSystem)

            items.add(
                AppItem(
                    packageName = pkg,
                    activityClassName = cls,
                    label = label,
                    iconBitmap = cachedBitmap,
                    isSystemApp = isSystem,
                    category = category
                )
            )
        }

        if (items.isEmpty()) {
            items.addAll(getShowcaseMockApps())
        }

        return items
    }

    private fun launchAppSafely(item: AppItem) {
        libraryManager.recordAppLaunch(item.packageName)

        val safeIntent = IntentSanitizer.createSafeLaunchIntent(
            context = this,
            packageName = item.packageName,
            activityClassName = item.activityClassName
        )

        if (safeIntent != null) {
            try {
                startActivity(safeIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to launch ${item.label}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Security policy blocked launch of ${item.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun registerPackageReceiver() {
        packageReceiver = PackageChangeReceiver {
            loadInstalledApps()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter)
    }

    private fun getShowcaseMockApps(): List<AppItem> {
        return listOf(
            AppItem("com.netflix.ninja", "com.netflix.ninja.MainActivity", "Netflix", category = AppCategory.STREAMING),
            AppItem("com.google.android.youtube.tv", "com.google.android.youtube.tv.MainActivity", "YouTube", category = AppCategory.STREAMING),
            AppItem("com.plexapp.android", "com.plexapp.android.MainActivity", "Plex", category = AppCategory.STREAMING),
            AppItem("com.amazon.amazonvideo.livingroom", "com.amazon.amazonvideo.livingroom.MainActivity", "Prime Video", category = AppCategory.STREAMING),
            AppItem("com.android.tv.settings", "com.android.tv.settings.MainSettings", "Settings", isSystemApp = true, category = AppCategory.SYSTEM),
            AppItem("com.lonelycatgames.Xplore", "com.lonelycatgames.Xplore.MainActivity", "Files", category = AppCategory.TOOLS)
        )
    }
}
