package dev.dsh.nfc;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import java.util.Locale;

/** 应用内语言：默认跟随系统，也可手动指定。 */
public final class Lang {

    public static final String SYSTEM = "";

    /** 可选语言：代码 + 本地名称。 */
    public static final String[][] OPTIONS = {
            {SYSTEM, ""},
            {"zh-CN", "简体中文"},
            {"zh-TW", "繁體中文"},
            {"en", "English"},
            {"ja", "日本語"},
            {"ko", "한국어"},
            {"es", "Español"},
            {"pt-BR", "Português"},
            {"fr", "Français"},
            {"de", "Deutsch"},
            {"it", "Italiano"},
            {"ru", "Русский"},
            {"ar", "العربية"},
            {"hi", "हिन्दी"},
            {"id", "Bahasa Indonesia"},
            {"vi", "Tiếng Việt"},
            {"th", "ไทย"},
            {"tr", "Türkçe"},
    };

    private Lang() {
    }

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("app", Context.MODE_PRIVATE);
    }

    public static String code(Context c) {
        String v = prefs(c).getString("lang", SYSTEM);
        return v == null ? SYSTEM : v;
    }

    public static void set(Context c, String code) {
        prefs(c).edit().putString("lang", code).apply();
    }

    /** 当前语言在列表中的显示名。 */
    public static String displayName(Context c) {
        String code = code(c);
        if (code.isEmpty()) return c.getString(R.string.language_system);
        for (String[] o : OPTIONS) {
            if (o[0].equals(code)) return o[1];
        }
        return c.getString(R.string.language_system);
    }

    public static Context wrap(Context base) {
        String code = code(base);
        if (code.isEmpty()) {
            Locale.setDefault(systemLocale());
            return base;
        }
        Locale locale;
        try {
            locale = Locale.forLanguageTag(code);
        } catch (Exception e) {
            locale = systemLocale();
        }
        Locale.setDefault(locale);
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        cfg.setLocale(locale);
        return base.createConfigurationContext(cfg);
    }

    @SuppressWarnings("deprecation")
    private static Locale systemLocale() {
        Configuration c = Resources.getSystem().getConfiguration();
        if (Build.VERSION.SDK_INT >= 24) {
            return c.getLocales().get(0);
        }
        return c.locale;
    }
}
