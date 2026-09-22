package com.ximbica.tubelite;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.Holder> {
    public interface Listener {
        void onVideoClick(StreamInfoItem item);
    }

    private final List<StreamInfoItem> items = new ArrayList<>();
    private final Listener listener;

    public VideoAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(false);
    }

    public void setItems(List<StreamInfoItem> data) {
        items.clear();
        items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context c = parent.getContext();

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(Ui.dp(c, 12), Ui.dp(c, 7), Ui.dp(c, 12), Ui.dp(c, 7));
        row.setBackgroundColor(Ui.BG);

        RecyclerView.LayoutParams rowParams = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        row.setLayoutParams(rowParams);

        FrameLayout thumbCard = new FrameLayout(c);
        Ui.clipRounded(thumbCard, c, 11, Ui.SURFACE_2);
        row.addView(thumbCard, new LinearLayout.LayoutParams(Ui.dp(c, 154), Ui.dp(c, 88)));

        ImageView thumb = new ImageView(c);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbCard.addView(thumb, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        TextView duration = new TextView(c);
        duration.setTextColor(0xFFFFFFFF);
        duration.setTextSize(10);
        duration.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        duration.setGravity(Gravity.CENTER);
        duration.setPadding(Ui.dp(c, 6), Ui.dp(c, 2), Ui.dp(c, 6), Ui.dp(c, 2));
        duration.setBackground(Ui.round(c, Ui.DURATION, 7));
        FrameLayout.LayoutParams durationLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.BOTTOM
        );
        durationLp.setMargins(0, 0, Ui.dp(c, 5), Ui.dp(c, 5));
        thumbCard.addView(duration, durationLp);

        LinearLayout textWrap = new LinearLayout(c);
        textWrap.setOrientation(LinearLayout.VERTICAL);
        textWrap.setPadding(Ui.dp(c, 11), 0, 0, 0);
        row.addView(textWrap, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ));

        TextView title = new TextView(c);
        title.setTextColor(Ui.TEXT);
        title.setTextSize(14.5f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMaxLines(2);
        title.setLineSpacing(0f, 1.02f);
        textWrap.addView(title);

        LinearLayout channelRow = new LinearLayout(c);
        channelRow.setOrientation(LinearLayout.HORIZONTAL);
        channelRow.setGravity(Gravity.CENTER_VERTICAL);
        channelRow.setPadding(0, Ui.dp(c, 8), 0, 0);
        textWrap.addView(channelRow);

        TextView avatar = new TextView(c);
        avatar.setTextColor(Ui.ON_PRIMARY_CONTAINER);
        avatar.setTextSize(11);
        avatar.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(Ui.circle(Ui.PRIMARY_CONTAINER));
        channelRow.addView(avatar, new LinearLayout.LayoutParams(Ui.dp(c, 26), Ui.dp(c, 26)));

        LinearLayout metaWrap = new LinearLayout(c);
        metaWrap.setOrientation(LinearLayout.VERTICAL);
        metaWrap.setPadding(Ui.dp(c, 8), 0, 0, 0);
        channelRow.addView(metaWrap, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ));

        TextView channel = new TextView(c);
        channel.setTextColor(Ui.MUTED);
        channel.setTextSize(11.5f);
        channel.setMaxLines(1);
        metaWrap.addView(channel);

        TextView meta = new TextView(c);
        meta.setTextColor(0xFF938F99);
        meta.setTextSize(10.5f);
        meta.setMaxLines(1);
        metaWrap.addView(meta);

        return new Holder(row, thumb, duration, title, avatar, channel, meta);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        StreamInfoItem item = items.get(position);
        h.title.setText(item.getName());

        String uploader = item.getUploaderName() == null ? "Canal" : item.getUploaderName();
        h.channel.setText(uploader);
        h.avatar.setText(initialOf(uploader));

        String duration = formatDuration(item.getDuration());
        h.duration.setText(duration);
        h.duration.setVisibility(duration.isEmpty() ? View.GONE : View.VISIBLE);

        String views = item.getViewCount() > 0 ? compactViews(item.getViewCount()) : "";
        h.meta.setText(views);

        String thumbUrl = null;
        List<Image> thumbs = item.getThumbnails();
        if (thumbs != null && !thumbs.isEmpty()) {
            int index = Math.min(thumbs.size() - 1, 2);
            thumbUrl = thumbs.get(index).getUrl();
        }

        Glide.with(h.thumb)
                .load(thumbUrl)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade(120))
                .into(h.thumb);

        h.itemView.setOnClickListener(v -> listener.onVideoClick(item));
    }

    @Override
    public void onViewRecycled(@NonNull Holder holder) {
        Glide.with(holder.thumb).clear(holder.thumb);
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static String initialOf(String name) {
        if (name == null || name.trim().isEmpty()) return "•";
        return name.trim().substring(0, 1).toUpperCase(Locale.getDefault());
    }

    private static String compactViews(long value) {
        if (value >= 1_000_000_000L) return oneDecimal(value / 1_000_000_000d) + " bi visualizações";
        if (value >= 1_000_000L) return oneDecimal(value / 1_000_000d) + " mi visualizações";
        if (value >= 1_000L) return oneDecimal(value / 1_000d) + " mil visualizações";
        return value + " visualizações";
    }

    private static String oneDecimal(double value) {
        if (value >= 100 || Math.abs(value - Math.rint(value)) < 0.05) {
            return String.format(Locale.US, "%.0f", value);
        }
        return String.format(Locale.US, "%.1f", value).replace('.', ',');
    }

    private static String formatDuration(long seconds) {
        if (seconds <= 0) return "";
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return h > 0
                ? String.format(Locale.US, "%d:%02d:%02d", h, m, s)
                : String.format(Locale.US, "%d:%02d", m, s);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final TextView duration;
        final TextView title;
        final TextView avatar;
        final TextView channel;
        final TextView meta;

        Holder(
                @NonNull View itemView,
                ImageView thumb,
                TextView duration,
                TextView title,
                TextView avatar,
                TextView channel,
                TextView meta
        ) {
            super(itemView);
            this.thumb = thumb;
            this.duration = duration;
            this.title = title;
            this.avatar = avatar;
            this.channel = channel;
            this.meta = meta;
        }
    }
}
