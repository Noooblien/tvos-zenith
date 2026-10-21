package org.zenith.tvos.ui.dock

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.zenith.tvos.R
import org.zenith.tvos.core.focus.SpatialFocusEngine

/**
 * High-performance horizontal dock and grid adapter with zero-allocation onBind,
 * tvOS long-press contextual menus, and interactive remote reordering.
 */
class AppDockAdapter(
    private val apps: MutableList<AppItem>,
    private val focusEngine: SpatialFocusEngine,
    private val onAppFocused: (AppItem, Int) -> Unit,
    private val onAppClicked: (AppItem) -> Unit,
    private val onAppLongClicked: ((AppItem, Int) -> Unit)? = null
) : RecyclerView.Adapter<AppDockAdapter.AppViewHolder>() {

    var isReorderMode: Boolean = false
        private set

    var reorderPosition: Int = -1
        private set

    fun setReorderMode(active: Boolean, position: Int = -1) {
        isReorderMode = active
        reorderPosition = position
        notifyDataSetChanged()
    }

    fun swapItems(fromPos: Int, toPos: Int): Boolean {
        if (fromPos in apps.indices && toPos in apps.indices && fromPos != toPos) {
            val item = apps.removeAt(fromPos)
            apps.add(toPos, item)
            reorderPosition = toPos
            notifyItemMoved(fromPos, toPos)
            return true
        }
        return false
    }

    fun updateList(newApps: List<AppItem>) {
        apps.clear()
        apps.addAll(newApps)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_card, parent, false)
        return AppViewHolder(view, focusEngine)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val item = apps[position]
        val isItemReordering = isReorderMode && position == reorderPosition
        holder.bind(item, position, isItemReordering, onAppFocused, onAppClicked, onAppLongClicked)
    }

    override fun getItemCount(): Int = apps.size

    class AppViewHolder(
        itemView: View,
        private val focusEngine: SpatialFocusEngine
    ) : RecyclerView.ViewHolder(itemView) {
        private val cardContainer: View = itemView.findViewById(R.id.cardContainer)
        private val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        private val appTitle: TextView = itemView.findViewById(R.id.appTitle)

        fun bind(
            item: AppItem,
            position: Int,
            isReordering: Boolean,
            onFocused: (AppItem, Int) -> Unit,
            onClicked: (AppItem) -> Unit,
            onLongClicked: ((AppItem, Int) -> Unit)?
        ) {
            appTitle.text = item.label

            if (item.iconBitmap != null) {
                appIcon.setImageBitmap(item.iconBitmap)
            } else {
                appIcon.setImageResource(R.drawable.ic_tvos_logo)
            }

            if (isReordering) {
                cardContainer.setBackgroundResource(R.drawable.card_reorder_ring)
                cardContainer.scaleX = 1.12f
                cardContainer.scaleY = 1.12f
            } else {
                cardContainer.setBackgroundResource(R.drawable.card_default_background)
                cardContainer.scaleX = 1.0f
                cardContainer.scaleY = 1.0f
            }

            focusEngine.attach(cardContainer) { hasFocus ->
                if (hasFocus) {
                    onFocused(item, position)
                    appTitle.visibility = View.VISIBLE
                } else {
                    appTitle.visibility = View.GONE
                }
            }

            cardContainer.setOnClickListener {
                onClicked(item)
            }

            cardContainer.setOnLongClickListener {
                onLongClicked?.invoke(item, position)
                true
            }
        }
    }
}
