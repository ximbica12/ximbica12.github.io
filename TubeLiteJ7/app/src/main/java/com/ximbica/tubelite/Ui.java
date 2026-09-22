package com.ximbica.tubelite;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

final class Ui {
    static final int BG = 0xFF101014;
    static final int SURFACE = 0xFF1C1B1F;
    static final int SURFACE_2 = 0xFF27252C;
    static final int SURFACE_3 = 0xFF302D36;
    static final int TEXT = 0xFFE6E1E5;
    static final int MUTED = 0xFFCAC4D0;
    static final int PRIMARY = 0xFFD0BCFF;
    static final int PRIMARY_CONTAINER = 0xFF4F378B;
    static final int ON_PRIMARY_CONTAINER = 0xFFEADDFF;
    static final int OUTLINE = 0xFF49454F;
    static final int DURATION = 0xD9000000;

    private Ui() {}

    static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable round(Context c, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(c.getResources().getDisplayMetrics().density * radiusDp);
        return d;
    }

    static GradientDrawable roundStroke(Context c, int color, float radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable d = round(c, color, radiusDp);
        d.setStroke(dp(c, strokeDp), strokeColor);
        return d;
    }

    static GradientDrawable circle(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    static void clipRounded(View view, Context c, float radiusDp, int backgroundColor) {
        view.setBackground(round(c, backgroundColor, radiusDp));
        view.setClipToOutline(true);
    }
}
