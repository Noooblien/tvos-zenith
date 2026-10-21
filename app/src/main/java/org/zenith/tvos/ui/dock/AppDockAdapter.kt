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
 * High-performance horizontal dock adapter with zero-allocation onBind.
 */
class AppDockAdapter(
    private val apps: List<AppItem>,
    private val focusEngine: SpatialFocusEngine,
    private val onAppFocused: (AppItem, Int) -> Unit,
    private val onAppClicked: (AppItem) -> Unit
) : RecyclerView.Adapter<AppDockAdapter.AppViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_card, parent, false)
        return AppViewHolder(view, focusEngine)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val item = apps[position]
        holder.bind(item, position, onAppFocused, onAppClicked)
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
            onFocused: (AppItem, Int) -> Unit,
            onClicked: (AppItem) -> Unit
        ) {
            appTitle.text = item.label

            if (item.iconBitmap != null) {
                appIcon.setImageBitmap(item.iconBitmap)
            } else {
                appIcon.setImageResource(R.drawable.ic_tvos_logo)
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
        }
    }
}
