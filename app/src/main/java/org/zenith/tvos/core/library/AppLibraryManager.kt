package org.zenith.tvos.core.library

import org.zenith.tvos.ui.dock.AppCategory
import org.zenith.tvos.ui.dock.AppItem

/**
 * Storage abstraction for persisting app configurations (favorites, custom ordering, hidden apps)
 * with zero Android Context dependency for deterministic JVM unit testability.
 */
interface AppPreferencesStorage {
    fun saveFavorites(packageNames: List<String>)
    fun loadFavorites(): List<String>?
    fun saveHidden(packageNames: Set<String>)
    fun loadHidden(): Set<String>?
}

/**
 * High-performance categorized grouping model for the tvOS App Library.
 */
data class CategorySection(
    val category: AppCategory,
    val title: String,
    val apps: List<AppItem>
)

/**
 * Domain engine managing application categorization, user favorites dock,
 * remote-driven reordering, and air-gapped app hiding.
 */
class AppLibraryManager(
    private val storage: AppPreferencesStorage? = null
) {
    private val allApps = mutableListOf<AppItem>()
    private val favoritePackageNames = mutableListOf<String>()
    private val hiddenPackageNames = mutableSetOf<String>()

    init {
        storage?.loadFavorites()?.let { favoritePackageNames.addAll(it) }
        storage?.loadHidden()?.let { hiddenPackageNames.addAll(it) }
    }

    /**
     * Updates the installed application inventory and initializes default favorites if empty.
     */
    fun setInstalledApps(apps: List<AppItem>) {
        allApps.clear()
        allApps.addAll(apps)

        if (favoritePackageNames.isEmpty() && apps.isNotEmpty()) {
            val defaults = apps.take(DEFAULT_FAVORITES_COUNT).map { it.packageName }
            favoritePackageNames.addAll(defaults)
            storage?.saveFavorites(favoritePackageNames)
        }
    }

    /**
     * Retrieves the user's customized favorites dock items in exact pinned order.
     */
    fun getFavorites(): List<AppItem> {
        val appMap = allApps.associateBy { it.packageName }
        return favoritePackageNames
            .filter { !hiddenPackageNames.contains(it) }
            .mapNotNull { appMap[it] }
    }

    /**
     * Checks if a package is currently pinned to the favorites dock.
     */
    fun isFavorite(packageName: String): Boolean = favoritePackageNames.contains(packageName)

    /**
     * Checks if a package is currently hidden from the launcher interface.
     */
    fun isHidden(packageName: String): Boolean = hiddenPackageNames.contains(packageName)

    /**
     * Pins an installed app to the end of the favorites dock.
     */
    fun pinToFavorites(packageName: String): Boolean {
        if (!favoritePackageNames.contains(packageName) && allApps.any { it.packageName == packageName }) {
            favoritePackageNames.add(packageName)
            storage?.saveFavorites(favoritePackageNames)
            return true
        }
        return false
    }

    /**
     * Removes an app from the favorites dock.
     */
    fun removeFromFavorites(packageName: String): Boolean {
        val removed = favoritePackageNames.remove(packageName)
        if (removed) {
            storage?.saveFavorites(favoritePackageNames)
        }
        return removed
    }

    /**
     * Reorders an item within the favorites dock via remote D-pad input.
     */
    fun moveFavorite(fromIndex: Int, toIndex: Int): Boolean {
        if (fromIndex in favoritePackageNames.indices && toIndex in favoritePackageNames.indices && fromIndex != toIndex) {
            val item = favoritePackageNames.removeAt(fromIndex)
            favoritePackageNames.add(toIndex, item)
            storage?.saveFavorites(favoritePackageNames)
            return true
        }
        return false
    }

    /**
     * Hides an app from the home screen and library grid.
     */
    fun hideApp(packageName: String): Boolean {
        val added = hiddenPackageNames.add(packageName)
        if (added) {
            storage?.saveHidden(hiddenPackageNames)
        }
        return added
    }

    /**
     * Restores a previously hidden app.
     */
    fun unhideApp(packageName: String): Boolean {
        val removed = hiddenPackageNames.remove(packageName)
        if (removed) {
            storage?.saveHidden(hiddenPackageNames)
        }
        return removed
    }

    /**
     * Returns all visible (non-hidden) installed applications.
     */
    fun getVisibleApps(): List<AppItem> {
        return allApps.filter { !hiddenPackageNames.contains(it.packageName) }
    }

    /**
     * Groups all visible applications into tvOS category sections.
     */
    fun getCategorySections(): List<CategorySection> {
        val visible = getVisibleApps()
        val sections = mutableListOf<CategorySection>()

        // 1. Favorites Dock
        val favs = getFavorites()
        if (favs.isNotEmpty()) {
            sections.add(CategorySection(AppCategory.OTHER, "Favorites Dock", favs))
        }

        // 2. Ordered Leanback Categories
        val grouped = visible.groupBy { it.category }
        val categoryOrder = listOf(
            AppCategory.STREAMING,
            AppCategory.MUSIC,
            AppCategory.GAMING,
            AppCategory.TOOLS,
            AppCategory.SYSTEM,
            AppCategory.OTHER
        )

        for (cat in categoryOrder) {
            val items = grouped[cat] ?: emptyList()
            if (items.isNotEmpty()) {
                sections.add(CategorySection(cat, cat.displayName, items))
            }
        }

        return sections
    }

    /**
     * Returns visible applications filtered by category, or all visible if category is null.
     */
    fun getFilteredApps(category: AppCategory?): List<AppItem> {
        val visible = getVisibleApps()
        return if (category == null) {
            visible
        } else {
            visible.filter { it.category == category }
        }
    }

    companion object {
        const val DEFAULT_FAVORITES_COUNT = 6
    }
}
