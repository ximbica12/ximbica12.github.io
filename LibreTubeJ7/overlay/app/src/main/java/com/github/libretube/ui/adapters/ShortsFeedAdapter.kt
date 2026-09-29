package com.github.libretube.ui.adapters

import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.libretube.api.obj.StreamItem
import com.github.libretube.helpers.ImageHelper
import com.github.libretube.ui.adapters.callbacks.DiffUtilItemCallback
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.ShapeAppearanceModel

class ShortsFeedAdapter(
    private val onTap: (position: Int, item: StreamItem) -> Unit
) : ListAdapter<StreamItem, ShortsFeedAdapter.Holder>(DiffUtilItemCallback()) {

    class Holder(
        val root: FrameLayout,
        val thumbnail: ShapeableImageView,
        val playerHost: FrameLayout,
        val loading: ProgressBar,
        val title: TextView,
        val channel: TextView
    ) : RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val density = parent.resources.displayMetrics.density
        val screenHeight = parent.resources.displayMetrics.heightPixels
        fun dp(value: Int) = (value * density).toInt()

        val itemHeight = (screenHeight - dp(145)).coerceAtLeast(dp(430))

        val root = FrameLayout(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                itemHeight
            )
            setPadding(dp(6), dp(3), dp(6), dp(7))
            setBackgroundColor(Color.BLACK)
            isClickable = true
            isFocusable = true
        }

        val thumbnail = ShapeableImageView(parent.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            shapeAppearanceModel = ShapeAppearanceModel.builder()
                .setAllCornerSizes(dp(18).toFloat())
                .build()
        }
        root.addView(thumbnail)

        val playerHost = FrameLayout(parent.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            clipToOutline = true
        }
        root.addView(playerHost)

        val info = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(18))
            setBackgroundColor(Color.argb(120, 0, 0, 0))
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
            setShadowLayer(4f, 0f, 1f, Color.BLACK)
        }

        val channel = TextView(parent.context).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            maxLines = 1
            alpha = 0.9f
            setPadding(0, dp(5), 0, 0)
            setShadowLayer(4f, 0f, 1f, Color.BLACK)
        }

        info.addView(title)
        info.addView(channel)
        root.addView(info)

        val loading = ProgressBar(parent.context).apply {
            isIndeterminate = true
            visibility = android.view.View.GONE
            layoutParams = FrameLayout.LayoutParams(
                dp(42),
                dp(42),
                Gravity.CENTER
            )
        }
        root.addView(loading)

        return Holder(root, thumbnail, playerHost, loading, title, channel)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)

        holder.title.text = item.title.orEmpty()
        holder.channel.text = item.uploaderName.orEmpty()
        holder.loading.visibility = android.view.View.GONE

        // The shared PlayerView is detached/re-attached by ShortsFragment. Never
        // remove arbitrary children here because RecyclerView can rebind the active
        // holder while playback is running.
        ImageHelper.loadImage(item.thumbnail, holder.thumbnail)

        holder.root.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                onTap(adapterPosition, getItem(adapterPosition))
            }
        }
    }
}
