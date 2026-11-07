package dev.dsh.nfc;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 一点点设计系统：尺寸、圆角、文字、卡片。 */
public final class Ui {

    private Ui() {
    }

    public static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    public static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public static TextView mono(Context c, CharSequence s, float sp, int color) {
        TextView t = text(c, s, sp, color, true);
        t.setTypeface(Typeface.MONOSPACE);
        return t;
    }

    public static LinearLayout box(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static GradientDrawable round(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        g.setShape(GradientDrawable.RECTANGLE);
        return g;
    }

    public static GradientDrawable roundStroke(Context c, int color, int strokeColor, float radiusDp) {
        GradientDrawable g = round(c, color, radiusDp);
        g.setStroke(dp(c, 1), strokeColor);
        return g;
    }

    public static TextView chip(Context c, String s) {
        TextView t = text(c, s, 11, 0xFFFFFFFF, true);
        t.setPadding(dp(c, 9), dp(c, 3), dp(c, 9), dp(c, 3));
        return t;
    }

    public static ImageButton icon(Context c, int res, int tint) {
        return icon(c, res, tint, null);
    }

    public static ImageButton icon(Context c, int res, int tint, String desc) {
        ImageButton b = new ImageButton(c);
        b.setImageResource(res);
        b.setColorFilter(tint);
        b.setBackground(null);
        int p = dp(c, 10);
        b.setPadding(p, p, p, p);
        b.setContentDescription(desc);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 40), dp(c, 40)));
        return b;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lpw(float weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
    }

    public static LinearLayout.LayoutParams lpm(int w, int h, int topMarginDp, Context c) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = dp(c, topMarginDp);
        return p;
    }
}
