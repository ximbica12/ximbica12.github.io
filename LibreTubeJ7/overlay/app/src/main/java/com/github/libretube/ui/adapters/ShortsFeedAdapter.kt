package com.github.libretube.ui.adapters

import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.libretube.api.obj.StreamItem
import com.github.libretube.extensions.toID
import com.github.libretube.helpers.ImageHelper
import com.github.libretube.helpers.NavigationHelper
import com.github.libretube.parcelable.PlayerData
import com.github.libretube.ui.adapters.callbacks.DiffUtilItemCallback

class ShortsFeedAdapter :
    ListAdapter<StreamItem, ShortsFeedAdapter.Holder>(DiffUtilItemCallback()) {

    class Holder(
        val root: FrameLayout,
        val thumbnail: ImageView,
        val title: TextView,
        val channel: TextView
    ) : RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val density = parent.resources.displayMetrics.density
        val screenHeight = parent.resources.displayMetrics.heightPixels
        fun dp(value: Int) = (value * density).toInt()

        val itemHeight = (screenHeight - dp(150)).coerceAtLeast(dp(420))

        val root = FrameLayout(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                itemHeight
            )
            setPadding(dp(8), dp(4), dp(8), dp(8))
            isClickable = true
            isFocusable = true
        }

        val thumbnail = ImageView(parent.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        root.addView(thumbnail)

        val info = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
            setBackgroundColor(Color.argb(150, 0, 0, 0))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }

        val title = TextView(parent.context).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            maxLines = 2
        }
        val channel = TextView(parent.context).apply {
            setTextColor(Color.LTGRAY)
            textSize = 13f
            maxLines = 1
            setPadding(0, dp(4), 0, 0)
        }

        info.addView(title)
        info.addView(channel)
        root.addView(info)

        return Holder(root, thumbnail, title, channel)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        holder.title.text = item.title.orEmpty()
        holder.channel.text = item.uploaderName.orEmpty()
        ImageHelper.loadImage(item.thumbnail, holder.thumbnail)

        holder.root.setOnClickListener {
            NavigationHelper.navigateVideo(
                holder.root.context,
                PlayerData(item.url.orEmpty().toID())
            )
        }
    }
}
