package dev.dsh.nfc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HistoryActivity extends Activity {

    private final List<File> files = new ArrayList<>();
    private final List<Dump> dumps = new ArrayList<>();
    private ListView list;
    private TextView emptyView;

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
        bar.addView(Ui.text(this, s(R.string.history_title), 19, c(R.color.text_primary), true),
                Ui.lpw(1f));
        root.addView(bar);

        list = new ListView(this);
        list.setDivider(null);
        list.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 16));
        list.setClipToPadding(false);
        android.widget.FrameLayout holder = new android.widget.FrameLayout(this);
        holder.addView(list, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        emptyView = Ui.text(this, s(R.string.history_empty), 14, c(R.color.text_secondary), false);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(android.view.View.GONE);
        holder.addView(emptyView, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(holder, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0,
                    insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);
        load();
    }

    private int c(int id) {
        return getResources().getColor(id);
    }

    private String s(int id) {
        return getString(id);
    }

    private void load() {
        File dir = new File(getExternalFilesDir(null), "dumps");
        File[] fs = dir.listFiles((d, n) -> n.endsWith(".json"));
        files.clear();
        dumps.clear();
        if (fs != null) {
            Arrays.sort(fs, (a, b) -> b.getName().compareTo(a.getName()));
            for (File f : fs) {
                try {
                    Dump d = Dump.load(this, f);
                    dumps.add(d);
                    files.add(f);
                } catch (IOException ignored) {
                }
            }
        }
        if (files.isEmpty()) {
            list.setAdapter(null);
            if (emptyView != null) emptyView.setVisibility(android.view.View.VISIBLE);
            return;
        }
        if (emptyView != null) emptyView.setVisibility(android.view.View.GONE);
        list.setAdapter(new Adapter());
    }

    private String pretty(String stamp) {
        if (stamp == null || stamp.length() < 13) return "";
        return stamp.substring(0, 4) + "-" + stamp.substring(4, 6) + "-" + stamp.substring(6, 8)
                + "  " + stamp.substring(9, 11) + ":" + stamp.substring(11, 13);
    }

    private class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return dumps.size();
        }

        @Override
        public Object getItem(int position) {
            return dumps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout card = Ui.box(HistoryActivity.this);
            card.setPadding(Ui.dp(HistoryActivity.this, 16), Ui.dp(HistoryActivity.this, 14),
                    Ui.dp(HistoryActivity.this, 16), Ui.dp(HistoryActivity.this, 14));
            card.setBackgroundResource(R.drawable.bg_card);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.bottomMargin = Ui.dp(HistoryActivity.this, 10);
            card.setLayoutParams(p);

            Dump d = dumps.get(position);
            card.addView(Ui.mono(HistoryActivity.this, d.uidHex, 17,
                    HistoryActivity.this.getResources().getColor(R.color.text_primary)));

            String meta = getString(R.string.history_meta,
                    d.sak == 0x18 || d.sak == 0x38 ? "Classic 4K" : "Classic 1K",
                    d.readSectors(), d.sectors, d.raw.length / 16, pretty(d.time));
            TextView m = Ui.text(HistoryActivity.this, meta, 11,
                    HistoryActivity.this.getResources().getColor(R.color.text_secondary), false);
            m.setPadding(0, Ui.dp(HistoryActivity.this, 4), 0, 0);
            card.addView(m);

            card.setOnClickListener(v -> showDetail(position));
            card.setOnLongClickListener(v -> {
                confirmDelete(position);
                return true;
            });
            return card;
        }
    }

    private void showDetail(int pos) {
        Dump d = dumps.get(pos);
        int ok = 0;
        for (int i = 0; i < d.sectors; i++) if (d.ok[i]) ok++;
        StringBuilder sb = new StringBuilder();
        sb.append("UID  ").append(d.uidHex).append("\n");
        sb.append("SAK  ").append(String.format("%02X", d.sak)).append("   ATQA  ")
                .append(String.format("%04X", d.atqa)).append("\n");
        sb.append(getString(R.string.history_sector_label)).append("  ")
                .append(ok).append("/").append(d.sectors).append("\n");
        sb.append(getString(R.string.history_bytes_label)).append("  ")
                .append(d.raw.length).append("\n\n");
        sb.append("Key A\n");
        for (int i = 0; i < Math.min(4, d.sectors); i++) {
            sb.append("  s").append(i).append("  ")
                    .append(d.keyA[i] == null ? getString(R.string.key_unknown) : Dump.hex(d.keyA[i]))
                    .append("\n");
        }
        if (d.sectors > 4) sb.append("  …\n");
        new AlertDialog.Builder(this)
                .setTitle(d.uidHex)
                .setMessage(sb.toString())
                .setPositiveButton(s(android.R.string.ok), null)
                .setNegativeButton(s(R.string.delete), (dlg, w) -> confirmDelete(pos))
                .show();
    }

    private void confirmDelete(int pos) {
        new AlertDialog.Builder(this)
                .setTitle(dumps.get(pos).uidHex)
                .setMessage(s(R.string.delete) + "?")
                .setPositiveButton(s(R.string.delete), (dlg, w) -> doDelete(pos))
                .setNegativeButton(s(R.string.cancel), null)
                .show();
    }

    private void doDelete(int pos) {
        File json = files.get(pos);
        String base = json.getName().substring(0, json.getName().length() - 5);
        File mfd = new File(json.getParentFile(), base + ".mfd");
        json.delete();
        mfd.delete();
        load();
    }
}
