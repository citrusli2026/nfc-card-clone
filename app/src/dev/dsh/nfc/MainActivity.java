package dev.dsh.nfc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements CardOps.Log {

    private static final int ARM_NONE = 0;
    private static final int ARM_READ = 1;
    private static final int ARM_WRITE = 2;

    private NfcAdapter adapter;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private Dump baseline;
    private Dump copyDump;
    private CardOps.CompareResult lastCompare;
    private int arm = ARM_NONE;
    private volatile boolean busy = false;
    private boolean logOpen = false;

    private LinearLayout content;
    private LinearLayout nfcBanner;
    private TextView step1, step2, step3;
    private TextView baseUid, baseMeta, baseChip;
    private TextView copyUid, copyMeta, copyChip;
    private LinearLayout verdictBox;
    private TextView verdictTitle, verdictSub;
    private TextView primary;
    private LinearLayout logWrap;
    private TextView logView;
    private FrameLayout rootFrame;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(Lang.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        adapter = NfcAdapter.getDefaultAdapter(this);
        rootFrame = new FrameLayout(this);
        rootFrame.setBackgroundColor(c(R.color.bg));
        rootFrame.addView(buildRoot());
        rootFrame.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0,
                    insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(rootFrame);
        if (!prefs().getBoolean("welcomed", false)) showWelcome();
        render();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("app", MODE_PRIVATE);
    }

    private int c(int id) {
        return getResources().getColor(id);
    }

    private String s(int id) {
        return getString(id);
    }

    private String s(int id, Object... a) {
        return getString(id, a);
    }

    // ---------------- 结构 ----------------

    private View buildRoot() {
        LinearLayout root = Ui.box(this);
        root.setBackgroundColor(c(R.color.bg));
        root.addView(buildTopBar());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scroll, slp);

        content = Ui.box(this);
        content.setPadding(Ui.dp(this, 20), Ui.dp(this, 6), Ui.dp(this, 20), Ui.dp(this, 24));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(buildNfcBanner());
        content.addView(buildSteps());
        content.addView(buildCard(true), Ui.lpm(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 14, this));
        content.addView(buildCard(false), Ui.lpm(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 10, this));
        content.addView(buildVerdict(), Ui.lpm(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 14, this));
        content.addView(buildPrimary(), Ui.lpm(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 54), 16, this));
        content.addView(buildSecondary(), Ui.lpm(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 10, this));
        content.addView(buildLog(), Ui.lpm(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 10, this));
        return root;
    }

    private View buildTopBar() {
        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 20), Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 6));
        TextView title = Ui.text(this, s(R.string.app_name), 21, c(R.color.text_primary), true);
        bar.addView(title, Ui.lpw(1f));

        ImageView hist = Ui.icon(this, R.drawable.ic_history, c(R.color.icon),
                s(R.string.action_history));
        hist.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        bar.addView(hist);
        ImageView info = Ui.icon(this, R.drawable.ic_info, c(R.color.icon),
                s(R.string.action_about));
        info.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
        bar.addView(info);
        return bar;
    }

    private View buildNfcBanner() {
        nfcBanner = Ui.row(this);
        nfcBanner.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10));
        nfcBanner.setBackground(Ui.round(this, c(R.color.warning_soft), 14));
        TextView t = Ui.text(this, s(R.string.nfc_off_title), 13, c(R.color.warning), true);
        nfcBanner.addView(t, Ui.lpw(1f));
        TextView go = Ui.text(this, s(R.string.nfc_off_action), 12, c(R.color.brand), true);
        go.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        go.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
            }
        });
        nfcBanner.addView(go);
        nfcBanner.setVisibility(View.GONE);
        return nfcBanner;
    }

    private View buildSteps() {
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dp(this, 8), 0, 0);
        step1 = stepPill(s(R.string.step_base), true);
        step2 = stepPill(s(R.string.step_copy), false);
        step3 = stepPill(s(R.string.step_compare), false);
        LinearLayout.LayoutParams a = Ui.lpw(1f);
        a.rightMargin = Ui.dp(this, 6);
        LinearLayout.LayoutParams b = Ui.lpw(1f);
        b.rightMargin = Ui.dp(this, 6);
        row.addView(step1, a);
        row.addView(step2, b);
        row.addView(step3, Ui.lpw(1f));
        return row;
    }

    private TextView stepPill(String label, boolean active) {
        TextView t = Ui.text(this, label, 12, c(R.color.text_secondary), active);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(this, 9), 0, Ui.dp(this, 9));
        t.setBackground(Ui.round(this, c(R.color.surface_alt), 99));
        return t;
    }

    private View buildCard(boolean isBase) {
        LinearLayout card = Ui.box(this);
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        card.setBackgroundResource(R.drawable.bg_card);

        LinearLayout head = Ui.row(this);
        TextView label = Ui.text(this, isBase ? s(R.string.card_base) : s(R.string.card_copy),
                12, c(R.color.text_secondary), false);
        head.addView(label, Ui.lpw(1f));
        TextView chip = Ui.chip(this, "-");
        chip.setText(s(R.string.status_none));
        chip.setBackground(Ui.round(this, c(R.color.surface_alt), 99));
        chip.setTextColor(c(R.color.text_secondary));
        head.addView(chip);
        card.addView(head);

        TextView uid = Ui.mono(this, isBase ? "—" : "—", 24,
                isBase ? c(R.color.text_primary) : c(R.color.text_primary));
        uid.setPadding(0, Ui.dp(this, 8), 0, 0);
        card.addView(uid);

        TextView meta = Ui.text(this, isBase ? s(R.string.card_empty_hint) : s(R.string.card_empty_hint_copy),
                12, c(R.color.text_tertiary), false);
        meta.setPadding(0, Ui.dp(this, 3), 0, 0);
        card.addView(meta);

        if (isBase) {
            baseUid = uid;
            baseMeta = meta;
            baseChip = chip;
        } else {
            copyUid = uid;
            copyMeta = meta;
            copyChip = chip;
        }
        return card;
    }

    private View buildVerdict() {
        verdictBox = Ui.box(this);
        verdictBox.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        verdictBox.setBackground(Ui.round(this, c(R.color.surface_alt), 18));
        verdictTitle = Ui.text(this, s(R.string.verdict_idle), 17, c(R.color.text_primary), true);
        verdictSub = Ui.text(this, "", 12, c(R.color.text_secondary), false);
        verdictSub.setPadding(0, Ui.dp(this, 4), 0, 0);
        verdictSub.setLineSpacing(Ui.dp(this, 2), 1f);
        verdictBox.addView(verdictTitle);
        verdictBox.addView(verdictSub);
        return verdictBox;
    }

    private View buildPrimary() {
        primary = Ui.text(this, s(R.string.action_read), 16, 0xFFFFFFFF, true);
        primary.setGravity(Gravity.CENTER);
        primary.setBackgroundResource(R.drawable.btn_primary);
        primary.setOnClickListener(v -> onPrimary());
        return primary;
    }

    private View buildSecondary() {
        LinearLayout row = Ui.row(this);
        String[] labels = {s(R.string.action_reset), s(R.string.action_details)};
        View.OnClickListener[] actions = {
                v -> resetAll(),
                v -> toggleLog()
        };
        for (int i = 0; i < labels.length; i++) {
            TextView b = Ui.text(this, labels[i], 13, c(R.color.text_primary), false);
            b.setGravity(Gravity.CENTER);
            b.setBackgroundResource(R.drawable.btn_ghost);
            LinearLayout.LayoutParams p = Ui.lpw(1f);
            p.height = Ui.dp(this, 44);
            if (i < labels.length - 1) p.rightMargin = Ui.dp(this, 8);
            b.setOnClickListener(actions[i]);
            row.addView(b, p);
        }
        return row;
    }

    private View buildLog() {
        logWrap = Ui.box(this);
        logWrap.setVisibility(View.GONE);
        logWrap.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        logWrap.setBackground(Ui.round(this, c(R.color.surface_alt), 16));
        TextView head = Ui.text(this, s(R.string.details_title), 12, c(R.color.text_secondary), true);
        logWrap.addView(head);
        ScrollView sc = new ScrollView(this);
        sc.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 170)));
        logView = Ui.mono(this, "", 10, c(R.color.log_text));
        logView.setTextIsSelectable(true);
        sc.addView(logView);
        logWrap.addView(sc, Ui.lpm(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 160), 6, this));
        return logWrap;
    }

    // ---------------- 状态 ----------------

    private void render() {
        boolean hasBase = baseline != null;
        boolean hasCopy = copyDump != null;

        if (hasBase) {
            baseUid.setText(baseline.uidHex);
            baseMeta.setText(metaOf(baseline));
            setChip(baseChip, s(R.string.status_read), true);
        } else {
            baseUid.setText("—");
            baseMeta.setText(s(R.string.card_empty_hint));
            setChip(baseChip, s(R.string.status_none), false);
        }
        if (hasCopy) {
            copyUid.setText(copyDump.uidHex);
            copyMeta.setText(metaOf(copyDump));
            setChip(copyChip, s(R.string.status_written), true);
        } else {
            copyUid.setText("—");
            copyMeta.setText(s(R.string.card_empty_hint_copy));
            setChip(copyChip, s(R.string.status_none), false);
        }

        step1.setText("1  " + s(R.string.step_base));
        step2.setText("2  " + s(R.string.step_copy));
        step3.setText("3  " + s(R.string.step_compare));
        styleStep(step1, hasBase, arm == ARM_READ);
        styleStep(step2, hasCopy, arm == ARM_WRITE);
        styleStep(step3, hasCopy, false);

        if (busy) {
            setVerdict(s(R.string.hint_busy), "", c(R.color.brand), true);
        } else if (hasCopy && lastCompare != null) {
            if (lastCompare.identical()) {
                setVerdict(s(R.string.verdict_match),
                        s(R.string.cmp_identical_sub, lastCompare.blocks, lastCompare.blocks * 16)
                                + "\nUID " + lastCompare.uidA + "  ·  " + s(R.string.fmt_sectors,
                                baseline.readSectors(), baseline.sectors),
                        c(R.color.success), true);
            } else {
                StringBuilder d = new StringBuilder();
                d.append(s(R.string.cmp_diff_sub, lastCompare.byteDiff, lastCompare.blockDiff));
                for (int i = 0; i < lastCompare.details.size() && i < 3; i++) {
                    CardOps.Detail det = lastCompare.details.get(i);
                    Object[] args = det.args.clone();
                    for (int k = 0; k < args.length; k++) {
                        if (args[k] == null) args[k] = getString(R.string.key_unknown);
                    }
                    d.append("\n· ").append(getString(det.resId, args));
                }
                setVerdict(s(R.string.verdict_diff), d.toString(), c(R.color.warning), true);
            }
        } else if (arm == ARM_READ) {
            setVerdict(s(R.string.hint_tap_base), "", c(R.color.brand), true);
        } else if (arm == ARM_WRITE) {
            setVerdict(s(R.string.hint_tap_copy), "", c(R.color.brand), true);
        } else if (hasBase) {
            setVerdict(s(R.string.verdict_ready), s(R.string.hint_tap_copy), c(R.color.brand), false);
        } else {
            setVerdict(s(R.string.verdict_idle), s(R.string.hint_tap_base), c(R.color.surface_alt), false);
        }

        primary.setText(busy ? s(R.string.hint_busy)
                : (hasBase ? (hasCopy ? s(R.string.action_rewrite) : s(R.string.action_write))
                : s(R.string.action_read)));
        primary.setAlpha(busy ? 0.6f : 1f);
        primary.setEnabled(!busy);

        boolean nfcOk = adapter != null && adapter.isEnabled();
        nfcBanner.setVisibility(nfcOk ? View.GONE : View.VISIBLE);
    }

    private String metaOf(Dump d) {
        return (d.sak == 0x18 || d.sak == 0x38 ? "MIFARE Classic 4K" : "MIFARE Classic 1K")
                + "  ·  " + s(R.string.fmt_sectors, d.readSectors(), d.sectors);
    }

    private void styleStep(TextView t, boolean done, boolean active) {
        if (done) {
            t.setTextColor(c(R.color.success));
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setBackground(Ui.round(this, c(R.color.success_soft), 99));
        } else if (active) {
            t.setTextColor(c(R.color.brand));
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setBackground(Ui.round(this, c(R.color.brand_soft), 99));
        } else {
            t.setTextColor(c(R.color.text_secondary));
            t.setTypeface(Typeface.DEFAULT);
            t.setBackground(Ui.round(this, c(R.color.surface_alt), 99));
        }
    }

    private void setChip(TextView chip, String text, boolean on) {
        chip.setText(text);
        chip.setBackground(Ui.round(this, c(on ? R.color.success_soft : R.color.surface_alt), 99));
        chip.setTextColor(c(on ? R.color.success : R.color.text_secondary));
    }

    private void setVerdict(String title, String sub, int bg, boolean lightText) {
        verdictTitle.setText(title);
        verdictTitle.setTextColor(lightText ? 0xFFFFFFFF : c(R.color.text_primary));
        verdictSub.setText(sub);
        verdictSub.setTextColor(lightText ? 0xD9FFFFFF : c(R.color.text_secondary));
        verdictBox.setBackground(Ui.round(this, bg, 18));
    }

    // ---------------- 交互 ----------------

    private void onPrimary() {
        if (baseline == null) {
            arm = ARM_READ;
            render();
        } else {
            arm = ARM_WRITE;
            render();
        }
        vibrate(20);
    }

    private void resetAll() {
        if (busy) return;
        baseline = null;
        copyDump = null;
        lastCompare = null;
        arm = ARM_NONE;
        logView.setText("");
        logOpen = false;
        logWrap.setVisibility(View.GONE);
        render();
        toast(s(R.string.reset_done));
    }

    private void toggleLog() {
        logOpen = !logOpen;
        logWrap.setVisibility(logOpen ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null && adapter.isEnabled()) {
            Bundle opts = new Bundle();
            opts.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250);
            adapter.enableReaderMode(this, this::onTagDiscovered,
                    NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B
                            | NfcAdapter.FLAG_READER_NFC_F | NfcAdapter.FLAG_READER_NFC_V
                            | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, opts);
        }
        render();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (adapter != null) {
            try {
                adapter.disableReaderMode(this);
            } catch (Exception ignored) {
            }
        }
    }

    private void onTagDiscovered(Tag tag) {
        if (busy) {
            toast(s(R.string.hint_busy));
            return;
        }
        if (arm == ARM_NONE) {
            toast(s(R.string.hint_idle));
            return;
        }
        busy = true;
        ui.post(this::render);
        vibrate(30);
        exec.execute(() -> {
            try {
                if (arm == ARM_READ) doRead(tag);
                else doWrite(tag);
            } catch (Throwable t) {
                line(R.string.log_exception, String.valueOf(t));
            } finally {
                busy = false;
                arm = ARM_NONE;
                ui.post(this::render);
            }
        });
    }

    private void doRead(Tag tag) {
        CardOps.Info info = CardOps.identify(tag);
        line(R.string.log_read_base, getString(info.typeRes), info.uid);
        Dump d = CardOps.read(tag, this);
        if (d == null) {
            ui.post(() -> setVerdict(s(R.string.verdict_fail), s(R.string.read_failed),
                    c(R.color.warning), true));
            return;
        }
        save(d);
        baseline = d;
        copyDump = null;
        lastCompare = null;
        ui.post(this::render);
        vibrate(50);
    }

    private void doWrite(Tag tag) {
        line(R.string.log_write_copy, baseline.uidHex);
        CardOps.write(tag, baseline, this);
        line(R.string.log_verify_pass);
        Dump back = CardOps.read(tag, this);
        if (back == null) {
            ui.post(() -> setVerdict(s(R.string.verdict_fail), s(R.string.read_failed),
                    c(R.color.warning), true));
            return;
        }
        save(back);
        copyDump = back;
        lastCompare = CardOps.compare(baseline, back);
        ui.post(this::render);
        vibrate(lastCompare.identical() ? 70 : 160);
    }

    private void save(Dump d) {
        try {
            d.save(this, new File(getExternalFilesDir(null), "dumps"));
        } catch (IOException e) {
            line(R.string.log_save_failed, String.valueOf(e.getMessage()));
        }
    }

    private void log(final String s) {
        ui.post(() -> logView.append(s + "\n"));
    }

    @Override
    public void line(String s) {
        log(s);
    }

    @Override
    public void line(int resId, Object... args) {
        log(getString(resId, args));
    }

    private void toast(final String s) {
        ui.post(() -> Toast.makeText(this, s, Toast.LENGTH_SHORT).show());
    }

    private void vibrate(int ms) {
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null) v.vibrate(ms);
        } catch (Exception ignored) {
        }
    }

    // ---------------- 引导 ----------------

    private void showWelcome() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(c(R.color.bg));
        ScrollView sc = new ScrollView(this);
        LinearLayout box = Ui.box(this);
        box.setPadding(Ui.dp(this, 28), Ui.dp(this, 56), Ui.dp(this, 28), Ui.dp(this, 28));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        box.addView(icon, Ui.lp(Ui.dp(this, 84), Ui.dp(this, 84)));

        TextView title = Ui.text(this, s(R.string.welcome_title), 28, c(R.color.text_primary), true);
        title.setPadding(0, Ui.dp(this, 22), 0, 0);
        box.addView(title);

        TextView sub = Ui.text(this, s(R.string.welcome_sub), 14, c(R.color.text_secondary), false);
        sub.setPadding(0, Ui.dp(this, 8), 0, 0);
        sub.setLineSpacing(Ui.dp(this, 3), 1f);
        box.addView(sub);

        box.addView(welcomePoint("1", s(R.string.welcome_p1_title), s(R.string.welcome_p1_sub)));
        box.addView(welcomePoint("2", s(R.string.welcome_p2_title), s(R.string.welcome_p2_sub)));
        box.addView(welcomePoint("3", s(R.string.welcome_p3_title), s(R.string.welcome_p3_sub)));

        final CheckBox agree = new CheckBox(this);
        agree.setText(s(R.string.welcome_agree));
        agree.setTextSize(12);
        agree.setTextColor(c(R.color.text_secondary));
        agree.setButtonTintList(android.content.res.ColorStateList.valueOf(c(R.color.brand)));
        agree.setPadding(0, Ui.dp(this, 18), 0, 0);
        box.addView(agree);

        final TextView start = Ui.text(this, s(R.string.welcome_start), 16, 0xFFFFFFFF, true);
        start.setGravity(Gravity.CENTER);
        start.setBackgroundResource(R.drawable.btn_primary);
        start.setAlpha(0.4f);
        start.setOnClickListener(v -> {
            if (!agree.isChecked()) {
                toast(s(R.string.welcome_agree));
                return;
            }
            prefs().edit().putBoolean("welcomed", true).apply();
            rootFrame.removeView(overlay);
            render();
        });
        agree.setOnCheckedChangeListener((b, checked) -> start.setAlpha(checked ? 1f : 0.4f));
        box.addView(start, Ui.lpm(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 54), 18, this));

        TextView note = Ui.text(this, s(R.string.welcome_note), 11, c(R.color.text_tertiary), false);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, Ui.dp(this, 12), 0, 0);
        box.addView(note);

        sc.addView(box);
        overlay.addView(sc);
        rootFrame.addView(overlay);
    }

    private View welcomePoint(String n, String t, String d) {
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dp(this, 18), 0, 0);
        TextView num = Ui.text(this, n, 13, c(R.color.brand), true);
        num.setGravity(Gravity.CENTER);
        num.setBackground(Ui.round(this, c(R.color.brand_soft), 99));
        row.addView(num, Ui.lp(Ui.dp(this, 26), Ui.dp(this, 26)));
        LinearLayout col = Ui.box(this);
        col.setPadding(Ui.dp(this, 12), 0, 0, 0);
        col.addView(Ui.text(this, t, 14, c(R.color.text_primary), true));
        TextView dd = Ui.text(this, d, 12, c(R.color.text_secondary), false);
        dd.setPadding(0, Ui.dp(this, 3), 0, 0);
        dd.setLineSpacing(Ui.dp(this, 2), 1f);
        col.addView(dd);
        row.addView(col, Ui.lpw(1f));
        return row;
    }
}
