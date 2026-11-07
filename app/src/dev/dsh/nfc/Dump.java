package dev.dsh.nfc;

import android.content.Context;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 一次读卡的完整结果：原始数据 + 每个扇区的密钥状态。 */
public class Dump {
    public static final int MAX_SECTORS = 40;

    public byte[] raw = new byte[0];
    public int sectors = 16;
    public String uidHex = "";
    public int sak = -1;
    public int atqa = -1;
    public String[] techs = new String[0];
    public String time = "";

    public byte[][] keyA = new byte[MAX_SECTORS][];
    public byte[][] keyB = new byte[MAX_SECTORS][];
    public byte[][] access = new byte[MAX_SECTORS][];
    public byte[][] usedKey = new byte[MAX_SECTORS][];
    public boolean[] ok = new boolean[MAX_SECTORS];

    public int readSectors() {
        int n = 0;
        for (int i = 0; i < sectors; i++) if (ok[i]) n++;
        return n;
    }

    public File save(Context ctx, File dir) throws IOException {
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException(ctx.getString(R.string.err_mkdir, dir.toString()));
        }
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String base = "dump_" + (uidHex.isEmpty() ? "unknown" : uidHex) + "_" + stamp;
        time = stamp;

        File mfd = new File(dir, base + ".mfd");
        FileOutputStream out = new FileOutputStream(mfd);
        try {
            out.write(raw);
        } finally {
            out.close();
        }

        File json = new File(dir, base + ".json");
        FileOutputStream jout = new FileOutputStream(json);
        try {
            jout.write(pretty(toJson()).getBytes(StandardCharsets.UTF_8));
        } finally {
            jout.close();
        }
        return mfd;
    }

    private static String pretty(JSONObject o) {
        try {
            return o.toString(2);
        } catch (Exception e) {
            return o.toString();
        }
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("uid", uidHex);
            o.put("sak", sak);
            o.put("atqa", atqa);
            o.put("sectors", sectors);
            o.put("time", time);
            JSONArray t = new JSONArray();
            for (String s : techs) t.put(s);
            o.put("techs", t);
            JSONArray okArr = new JSONArray();
            for (int i = 0; i < sectors; i++) okArr.put(ok[i]);
            o.put("ok", okArr);
            o.put("keyA", hexArray(keyA));
            o.put("keyB", hexArray(keyB));
            o.put("access", hexArray(access));
            o.put("usedKey", hexArray(usedKey));
            o.put("raw", Base64.encodeToString(raw, Base64.NO_WRAP));
        } catch (Exception ignored) {
        }
        return o;
    }

    private static JSONArray hexArray(byte[][] src) {
        JSONArray a = new JSONArray();
        for (int i = 0; i < MAX_SECTORS; i++) {
            a.put(src[i] == null ? JSONObject.NULL : hex(src[i]));
        }
        return a;
    }

    public static Dump load(Context ctx, File json) throws IOException {
        RandomAccessFile f = new RandomAccessFile(json, "r");
        byte[] buf = new byte[(int) f.length()];
        try {
            f.readFully(buf);
        } finally {
            f.close();
        }
        try {
            JSONObject o = new JSONObject(new String(buf, StandardCharsets.UTF_8));
            Dump d = new Dump();
            d.uidHex = o.optString("uid", "");
            d.sak = o.optInt("sak", -1);
            d.atqa = o.optInt("atqa", -1);
            d.sectors = o.optInt("sectors", 16);
            d.time = o.optString("time", "");
            JSONArray t = o.optJSONArray("techs");
            if (t != null) {
                d.techs = new String[t.length()];
                for (int i = 0; i < t.length(); i++) d.techs[i] = t.optString(i);
            }
            JSONArray okArr = o.optJSONArray("ok");
            if (okArr != null) {
                for (int i = 0; i < okArr.length() && i < MAX_SECTORS; i++) d.ok[i] = okArr.optBoolean(i);
            }
            d.keyA = unhexArray(o.optJSONArray("keyA"));
            d.keyB = unhexArray(o.optJSONArray("keyB"));
            d.access = unhexArray(o.optJSONArray("access"));
            d.usedKey = unhexArray(o.optJSONArray("usedKey"));
            String rawB64 = o.optString("raw", "");
            d.raw = Base64.decode(rawB64, Base64.NO_WRAP);
            return d;
        } catch (Exception e) {
            throw new IOException(ctx.getString(R.string.err_dump_parse, String.valueOf(e.getMessage())));
        }
    }

    private static byte[][] unhexArray(JSONArray a) {
        byte[][] out = new byte[MAX_SECTORS][];
        if (a == null) return out;
        for (int i = 0; i < a.length() && i < MAX_SECTORS; i++) {
            if (a.isNull(i)) continue;
            out[i] = unhex(a.optString(i));
        }
        return out;
    }

    public static String hex(byte[] b) {
        if (b == null) return "";
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format(Locale.US, "%02X", x & 0xff));
        return sb.toString();
    }

    public static byte[] unhex(String s) {
        if (s == null) return null;
        s = s.replaceAll("\\s+", "");
        if (s.length() % 2 != 0) return null;
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
