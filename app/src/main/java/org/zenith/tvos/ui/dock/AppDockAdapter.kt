package org.zenith.tvos.ui.dock

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.zenith.tvos.R
import org.zenith.tvos.core.focus.SpatialFocusEngine

/**
 * High-performance horizontal dock and grid adapter with zero-allocation onBind,
 * DiffUtil incremental list updates, directional 3D specular card tilt, multi-layer
 * inner icon/sheen parallax, tactile click compression, Parental PIN lock badge,
 * tvOS long-press contextual menus, and interactive remote reordering.
 */
class AppDockAdapter(
    private val apps: MutableList<AppItem>,
    private val focusEngine: SpatialFocusEngine,
    private val isPackageLockedProvider: (String) -> Boolean = { false },
    private val onAppFocused: (AppItem, Int) -> Unit,
    private val onAppClicked: (AppItem) -> Unit,
    private val onAppLongClicked: ((AppItem, Int) -> Unit)? = null
) : RecyclerView.Adapter<AppDockAdapter.AppViewHolder>() {

    var isReorderMode: Boolean = false
        private set

    var reorderPosition: Int = -1
        private set

    private var lastFocusedPosition: Int = -1

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return apps[position].packageName.hashCode().toLong()
    }

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
        val oldSnapshot = apps.toList()
        val diffResult = DiffUtil.calculateDiff(AppItemDiffCallback(oldSnapshot, newApps))
        apps.clear()
        apps.addAll(newApps)
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_card, parent, false)
        return AppViewHolder(view, focusEngine)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val item = apps[position]
        val isItemReordering = isReorderMode && position == reorderPosition
        val isLocked = isPackageLockedProvider(item.packageName)
        holder.bind(
            item = item,
            position = position,
            isReordering = isItemReordering,
            isLocked = isLocked,
            tiltProvider = {
                focusEngine.computeDirectionalTiltY(lastFocusedPosition, holder.bindingAdapterPosition.takeIf { it >= 0 } ?: position)
            },
            onFocused = { focusedItem, pos ->
                lastFocusedPosition = pos
                onAppFocused(focusedItem, pos)
            },
            onClicked = onAppClicked,
            onLongClicked = onAppLongClicked
        )
    }

    override fun getItemCount(): Int = apps.size

    class AppItemDiffCallback(
        private val oldList: List<AppItem>,
        private val newList: List<AppItem>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].packageName == newList[newItemPosition].packageName
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            return oldItem.packageName == newItem.packageName &&
                oldItem.label == newItem.label &&
                oldItem.category == newItem.category
        }
    }

    class AppViewHolder(
        itemView: View,
        private val focusEngine: SpatialFocusEngine
    ) : RecyclerView.ViewHolder(itemView) {
        private val cardContainer: View = itemView.findViewById(R.id.cardContainer)
        private val specularSheen: View? = itemView.findViewById(R.id.specularSheen)
        private val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        private val lockBadge: TextView = itemView.findViewById(R.id.lockBadge)
        private val appTitle: TextView = itemView.findViewById(R.id.appTitle)

        fun bind(
            item: AppItem,
            position: Int,
            isReordering: Boolean,
            isLocked: Boolean,
            tiltProvider: () -> Float,
            onFocused: (AppItem, Int) -> Unit,
            onClicked: (AppItem) -> Unit,
            onLongClicked: ((AppItem, Int) -> Unit)?
        ) {
            appTitle.text = item.label
            lockBadge.visibility = if (isLocked) View.VISIBLE else View.GONE

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

            focusEngine.attach(
                view = cardContainer,
                tiltAngleYProvider = tiltProvider
            ) { hasFocus ->
                val resolvedPos = bindingAdapterPosition.takeIf { it >= 0 } ?: position
                val tiltY = if (hasFocus) tiltProvider() else 0f
                focusEngine.animateCardInnerLayers(
                    iconView = appIcon,
                    sheenView = specularSheen,
                    hasFocus = hasFocus,
                    tiltAngleY = tiltY
                )
                if (hasFocus) {
                    onFocused(item, resolvedPos)
                    appTitle.visibility = View.VISIBLE
                } else {
                    appTitle.visibility = View.GONE
                }
            }

            cardContainer.setOnClickListener {
                focusEngine.animateClickCompression(cardContainer) {
                    onClicked(item)
                }
            }

            cardContainer.setOnLongClickListener {
                val resolvedPos = bindingAdapterPosition.takeIf { it >= 0 } ?: position
                onLongClicked?.invoke(item, resolvedPos)
                true
            }
        }
    }
}
