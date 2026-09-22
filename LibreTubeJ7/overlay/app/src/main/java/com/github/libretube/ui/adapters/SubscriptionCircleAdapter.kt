package com.github.libretube.ui.adapters

import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.libretube.api.obj.Subscription
import com.github.libretube.helpers.ImageHelper
import com.github.libretube.helpers.NavigationHelper
import com.github.libretube.ui.adapters.callbacks.DiffUtilItemCallback

class SubscriptionCircleAdapter :
    ListAdapter<Subscription, SubscriptionCircleAdapter.Holder>(DiffUtilItemCallback()) {

    class Holder(
        val root: LinearLayout,
        val image: ImageView,
        val title: TextView
    ) : RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val density = parent.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = RecyclerView.LayoutParams(dp(82), ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(5), dp(3), dp(5), dp(3))
            isClickable = true
            isFocusable = true
        }

        val image = ImageView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = null
        }

        val title = TextView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER
            maxLines = 2
            textSize = 11f
            typeface = Typeface.DEFAULT
        }

        root.addView(image)
        root.addView(title)
        return Holder(root, image, title)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        holder.title.text = item.name
        ImageHelper.loadImage(item.avatar, holder.image, true)

        holder.root.setOnClickListener {
            NavigationHelper.navigateChannel(holder.root.context, item.url)
        }
    }
}
