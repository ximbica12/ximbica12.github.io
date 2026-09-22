package com.ximbica.tubelite;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

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
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(c, 10), dp(c, 8), dp(c, 10), dp(c, 8));
        row.setBackgroundColor(Color.rgb(15, 15, 15));

        ImageView thumb = new ImageView(c);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        row.addView(thumb, new LinearLayout.LayoutParams(dp(c, 152), dp(c, 86)));

        LinearLayout textWrap = new LinearLayout(c);
        textWrap.setOrientation(LinearLayout.VERTICAL);
        textWrap.setPadding(dp(c, 10), 0, 0, 0);
        row.addView(textWrap, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(c);
        title.setTextColor(Color.WHITE);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMaxLines(2);
        textWrap.addView(title);

        TextView meta = new TextView(c);
        meta.setTextColor(Color.rgb(170, 170, 170));
        meta.setTextSize(12);
        meta.setMaxLines(2);
        meta.setPadding(0, dp(c, 5), 0, 0);
        textWrap.addView(meta);

        return new Holder(row, thumb, title, meta);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        StreamInfoItem item = items.get(position);
        h.title.setText(item.getName());

        String uploader = item.getUploaderName() == null ? "" : item.getUploaderName();
        String duration = formatDuration(item.getDuration());
        String views = item.getViewCount() > 0
                ? String.format(Locale.US, "%,.0f views", (double) item.getViewCount())
                : "";
        String meta = uploader;
        if (!duration.isEmpty()) meta += "  •  " + duration;
        if (!views.isEmpty()) meta += "  •  " + views;
        h.meta.setText(meta);

        String thumbUrl = null;
        List<Image> thumbs = item.getThumbnails();
        if (thumbs != null && !thumbs.isEmpty()) {
            thumbUrl = thumbs.get(thumbs.size() - 1).getUrl();
        }
        Glide.with(h.thumb)
                .load(thumbUrl)
                .centerCrop()
                .into(h.thumb);

        h.itemView.setOnClickListener(v -> listener.onVideoClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
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

    private static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final TextView title;
        final TextView meta;

        Holder(@NonNull View itemView, ImageView thumb, TextView title, TextView meta) {
            super(itemView);
            this.thumb = thumb;
            this.title = title;
            this.meta = meta;
        }
    }
}
