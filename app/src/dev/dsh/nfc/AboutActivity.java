package dev.dsh.nfc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class AboutActivity extends Activity {

    private int c(int id) {
        return getResources().getColor(id);
    }

    private String s(int id) {
        return getString(id);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(Lang.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = Ui.box(this);
        root.setBackgroundColor(c(R.color.bg));

        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 8));
        TextView back = Ui.text(this, "‹", 28, c(R.color.text_primary), false);
        back.setGravity(Gravity.CENTER);
        back.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 12), 0);
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        bar.addView(Ui.text(this, s(R.string.about_title), 19, c(R.color.text_primary), true),
                Ui.lpw(1f));
        root.addView(bar);

        ScrollView sc = new ScrollView(this);
        LinearLayout box = Ui.box(this);
        box.setPadding(Ui.dp(this, 24), Ui.dp(this, 10), Ui.dp(this, 24), Ui.dp(this, 32));
        sc.addView(box);
        root.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        box.addView(icon, Ui.lp(Ui.dp(this, 72), Ui.dp(this, 72)));

        TextView name = Ui.text(this, s(R.string.app_name), 22, c(R.color.text_primary), true);
        name.setPadding(0, Ui.dp(this, 16), 0, 0);
        box.addView(name);

        box.addView(Ui.text(this, s(R.string.about_version) + " " + version(), 12,
                c(R.color.text_tertiary), false));

        TextView desc = Ui.text(this, s(R.string.about_desc), 14, c(R.color.text_secondary), false);
        desc.setPadding(0, Ui.dp(this, 16), 0, 0);
        desc.setLineSpacing(Ui.dp(this, 3), 1f);
        box.addView(desc);

        box.addView(section(s(R.string.about_device_title), deviceInfo()));
        box.addView(languageRow());
        box.addView(section(s(R.string.about_privacy_title), s(R.string.about_privacy)));
        box.addView(section(s(R.string.about_disclaimer_title), s(R.string.about_disclaimer)));
        box.addView(Ui.text(this, s(R.string.about_oss), 11, c(R.color.text_tertiary), false));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0,
                    insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);
    }

    private String deviceInfo() {
        boolean nfc = false;
        try {
            android.nfc.NfcAdapter a = android.nfc.NfcAdapter.getDefaultAdapter(this);
            nfc = a != null;
        } catch (Exception ignored) {
        }
        return android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
                + "  ·  Android " + android.os.Build.VERSION.RELEASE
                + "  ·  NFC " + s(nfc ? R.string.about_nfc_on : R.string.about_nfc_off);
    }

    private String version() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pi.versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private LinearLayout languageRow() {
        LinearLayout box = Ui.box(this);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = Ui.dp(this, 24);
        box.setLayoutParams(p);
        box.addView(Ui.text(this, s(R.string.language_title), 13, c(R.color.text_primary), true));
        TextView value = Ui.text(this, Lang.displayName(this), 13, c(R.color.brand), false);
        value.setPadding(0, Ui.dp(this, 6), 0, 0);
        value.setOnClickListener(v -> pickLanguage());
        box.addView(value);
        return box;
    }

    private void pickLanguage() {
        String[] names = new String[Lang.OPTIONS.length];
        names[0] = s(R.string.language_system);
        int checked = 0;
        String cur = Lang.code(this);
        for (int i = 1; i < Lang.OPTIONS.length; i++) {
            names[i] = Lang.OPTIONS[i][1];
            if (Lang.OPTIONS[i][0].equals(cur)) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(s(R.string.language_title))
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    Lang.set(this, Lang.OPTIONS[which][0]);
                    d.dismiss();
                    restart();
                })
                .setNegativeButton(s(R.string.cancel), null)
                .show();
    }

    private void restart() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finishAffinity();
    }

    private LinearLayout section(String title, String body) {
        LinearLayout box = Ui.box(this);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = Ui.dp(this, 24);
        box.setLayoutParams(p);
        box.addView(Ui.text(this, title, 13, c(R.color.text_primary), true));
        TextView b = Ui.text(this, body, 12, c(R.color.text_secondary), false);
        b.setPadding(0, Ui.dp(this, 6), 0, 0);
        b.setLineSpacing(Ui.dp(this, 3), 1f);
        box.addView(b);
        return box;
    }
}
