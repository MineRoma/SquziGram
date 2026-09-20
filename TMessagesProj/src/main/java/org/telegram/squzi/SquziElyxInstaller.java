package org.telegram.squzi;

import android.app.Activity;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

// SquziGram: структурные плагины exteraGram (Elyx): архивы .elyx/.eaf
// (ZIP) с refmap + metainfo + main.py. Открытие по тапу, установка
// распаковкой в <id>.elyxdir, запуск через общий рантайм.
public class SquziElyxInstaller {

    public static class ElyxInfo {
        public SquziPlugin meta;
        public String mainRel = "main.py";
    }

    private static final String[] REFMAP_NAMES = {"refmap.yml", "refmap.yaml", "refmap.json"};
    private static final String[] META_NAMES = {"metainfo.yml", "metainfo.yaml", "metainfo.json"};

    public static boolean handleFile(Activity activity, Theme.ResourcesProvider rp, File file, String fileName) {
        if (activity == null || activity.isFinishing() || file == null || !file.exists()) {
            return false;
        }
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase();
        if (!lower.endsWith(".elyx") && !lower.endsWith(".eaf")) {
            return false;
        }
        try {
            ElyxInfo info = inspect(file);
            if (info == null || info.meta == null) {
                Toast.makeText(activity, "Не удалось прочитать архив плагина", Toast.LENGTH_SHORT).show();
                return true;
            }
            showInstall(activity, rp, file, info);
            return true;
        } catch (Throwable t) {
            FileLog.e(t);
            return false;
        }
    }

    public static ElyxInfo inspect(File zipFile) {
        ZipFile zip = null;
        try {
            zip = new ZipFile(zipFile);
            List<String> names = new ArrayList<>();
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                names.add(entries.nextElement().getName());
            }
            String refmapName = findRoot(names, REFMAP_NAMES);
            Map<String, String> refmap = new HashMap<>();
            if (refmapName != null) {
                refmap = parseMap(readEntry(zip, refmapName, 32 * 1024));
            }
            String metaName = refmap.get("metainfo");
            if (metaName == null || getEntry(zip, metaName) == null) {
                metaName = findRoot(names, META_NAMES);
            }
            if (metaName == null) {
                return null;
            }
            SquziPlugin meta = parseMeta(readEntry(zip, metaName, 64 * 1024));
            if (meta == null) {
                return null;
            }
            if (!SquziPlugin.isValidId(meta.id)) {
                meta.id = SquziPlugin.sanitizeId(stripExtension(zipFile.getName()));
            }
            if (meta.name == null || meta.name.isEmpty()) {
                meta.name = meta.id;
            }
            if (meta.version == null || meta.version.isEmpty()) {
                meta.version = "1.0";
            }
            String main = refmap.get("main");
            if (main == null || main.isEmpty() || getEntry(zip, main) == null) {
                main = "main.py";
            }
            if (getEntry(zip, main) == null) {
                return null;
            }
            ElyxInfo info = new ElyxInfo();
            info.meta = meta;
            info.mainRel = main;
            return info;
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // Установка: распаковка в squzi_plugins/<id>.elyxdir. Возвращает id.
    public static String install(File zipFile, ElyxInfo info) {
        if (zipFile == null || info == null || info.meta == null || info.meta.id == null) {
            return null;
        }
        File dir = new File(SquziPluginsController.pluginsDirPublic(), info.meta.id + ".elyxdir");
        ZipFile zip = null;
        try {
            SquziPluginsController.deleteRecursive(dir);
            // noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            zip = new ZipFile(zipFile);
            Enumeration<? extends ZipEntry> entries = zip.entries();
            byte[] buf = new byte[8192];
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || name.contains("..")) {
                    continue;
                }
                File out = new File(dir, name);
                if (!out.getCanonicalPath().startsWith(dir.getCanonicalPath())) {
                    continue;
                }
                if (entry.isDirectory()) {
                    // noinspection ResultOfMethodCallIgnored
                    out.mkdirs();
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null) {
                    // noinspection ResultOfMethodCallIgnored
                    parent.mkdirs();
                }
                InputStream in = null;
                FileOutputStream fos = null;
                try {
                    in = zip.getInputStream(entry);
                    fos = new FileOutputStream(out);
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        fos.write(buf, 0, n);
                    }
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (Throwable ignored) {
                        }
                    }
                    if (fos != null) {
                        try {
                            fos.close();
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
            // refmap для поиска main.py при загрузке
            FileOutputStream refOut = null;
            try {
                refOut = new FileOutputStream(new File(dir, "refmap.yml"));
                String content = "main: " + info.mainRel + "\n";
                refOut.write(content.getBytes("UTF-8"));
            } catch (Throwable ignored) {
            } finally {
                if (refOut != null) {
                    try {
                        refOut.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
            SquziPluginsController.setEnabled(info.meta.id, true);
            return info.meta.id;
        } catch (Throwable t) {
            FileLog.e(t);
            SquziPluginsController.deleteRecursive(dir);
            return null;
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public static File findMainFile(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return null;
        }
        try {
            File refmap = new File(dir, "refmap.yml");
            if (!refmap.exists()) {
                refmap = new File(dir, "refmap.yaml");
            }
            if (!refmap.exists()) {
                refmap = new File(dir, "refmap.json");
            }
            String main = "main.py";
            if (refmap.exists()) {
                Map<String, String> map = parseMap(readFile(refmap, 32 * 1024));
                if (map.containsKey("main") && !map.get("main").isEmpty()) {
                    main = map.get("main");
                }
            }
            File mainFile = new File(dir, main);
            if (mainFile.exists()) {
                return mainFile;
            }
            File fallback = new File(dir, "main.py");
            return fallback.exists() ? fallback : null;
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    public static SquziPlugin parseInstalled(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return null;
        }
        try {
            String metaName = null;
            File refmap = new File(dir, "refmap.yml");
            if (refmap.exists()) {
                Map<String, String> map = parseMap(readFile(refmap, 32 * 1024));
                if (map.containsKey("metainfo")) {
                    metaName = map.get("metainfo");
                }
            }
            File metaFile = null;
            if (metaName != null) {
                metaFile = new File(dir, metaName);
            }
            if (metaFile == null || !metaFile.exists()) {
                for (String candidate : META_NAMES) {
                    File f = new File(dir, candidate);
                    if (f.exists()) {
                        metaFile = f;
                        break;
                    }
                }
            }
            SquziPlugin meta;
            if (metaFile != null && metaFile.exists()) {
                meta = parseMeta(readFile(metaFile, 64 * 1024));
            } else {
                meta = new SquziPlugin();
            }
            if (meta == null) {
                return null;
            }
            String dirId = dir.getName();
            if (dirId.endsWith(".elyxdir")) {
                dirId = dirId.substring(0, dirId.length() - 8);
            }
            if (!SquziPlugin.isValidId(meta.id)) {
                meta.id = SquziPlugin.sanitizeId(dirId);
            }
            if (meta.name == null || meta.name.isEmpty()) {
                meta.name = meta.id;
            }
            if (meta.version == null || meta.version.isEmpty()) {
                meta.version = "1.0";
            }
            return meta;
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    // -- парсинг ----------------------------------------------------------

    private static ZipEntry getEntry(ZipFile zip, String name) {
        try {
            return zip.getEntry(name);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String findRoot(List<String> names, String[] candidates) {
        for (String candidate : candidates) {
            if (names.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static String readEntry(ZipFile zip, String name, int maxBytes) {
        InputStream in = null;
        try {
            ZipEntry entry = zip.getEntry(name);
            if (entry == null) {
                return "";
            }
            in = zip.getInputStream(entry);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int total = 0;
            int n;
            while ((n = in.read(buf)) > 0 && total < maxBytes) {
                int take = Math.min(n, maxBytes - total);
                out.write(buf, 0, take);
                total += take;
            }
            return out.toString("UTF-8");
        } catch (Throwable t) {
            return "";
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static String readFile(File file, int maxBytes) {
        java.io.FileInputStream in = null;
        try {
            long len = Math.min(file.length(), maxBytes);
            if (len <= 0) {
                return "";
            }
            byte[] buf = new byte[(int) len];
            in = new java.io.FileInputStream(file);
            int read = 0;
            while (read < buf.length) {
                int n = in.read(buf, read, buf.length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            return new String(buf, 0, read, "UTF-8");
        } catch (Throwable t) {
            return "";
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // Плоский YAML "key: value" или JSON-объект. Ключи вида __name__ нормализуются.
    public static Map<String, String> parseMap(String text) {
        Map<String, String> map = new HashMap<>();
        if (text == null) {
            return map;
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("{")) {
            try {
                JSONObject o = new JSONObject(trimmed);
                Iterator<String> keys = o.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    map.put(normalizeKey(key), o.optString(key, ""));
                }
                return map;
            } catch (Throwable ignored) {
            }
        }
        String[] lines = text.split("\n");
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            int colon = t.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = normalizeKey(t.substring(0, colon).trim());
            String value = t.substring(colon + 1).trim();
            if ((value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2)
                    || (value.startsWith("'") && value.endsWith("'") && value.length() >= 2)) {
                value = value.substring(1, value.length() - 1);
            }
            int hash = value.indexOf(" #");
            if (hash != -1 && !value.startsWith("http")) {
                value = value.substring(0, hash).trim();
            }
            map.put(key, value);
        }
        return map;
    }

    private static String normalizeKey(String key) {
        if (key == null) {
            return "";
        }
        String k = key.trim();
        while (k.startsWith("_")) {
            k = k.substring(1);
        }
        while (k.endsWith("_")) {
            k = k.substring(0, k.length() - 1);
        }
        return k;
    }

    public static SquziPlugin parseMeta(String text) {
        Map<String, String> map = parseMap(text);
        if (map.isEmpty()) {
            return null;
        }
        SquziPlugin meta = new SquziPlugin();
        meta.id = map.containsKey("id") ? map.get("id") : "";
        meta.name = map.containsKey("name") ? map.get("name") : "";
        meta.description = map.containsKey("description") ? map.get("description") : "";
        meta.author = map.containsKey("author") ? map.get("author") : "";
        meta.version = map.containsKey("version") ? map.get("version") : "1.0";
        meta.icon = map.containsKey("icon") ? map.get("icon") : "";
        meta.appVersion = map.containsKey("app_version") ? map.get("app_version") : map.getOrDefault("min_version", "");
        meta.sdkVersion = map.containsKey("sdk_version") ? map.get("sdk_version") : "";
        meta.requirements = map.containsKey("requirements") ? map.get("requirements") : "";
        return meta;
    }

    private static String stripExtension(String name) {
        if (name == null) {
            return "";
        }
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(0, idx) : name;
    }

    // -- диалог установки ---------------------------------------------------

    private static void showInstall(final Activity activity, Theme.ResourcesProvider rp,
                                    final File zipFile, final ElyxInfo info) {
        final SquziPlugin meta = info.meta;
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(16), dp(24), dp(8));

        ImageView iconView = new ImageView(activity);
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iconView.setImageResource(R.drawable.settings_features);
        iconView.setBackground(Theme.createCircleDrawable(dp(64), 0xFF7B61FF));
        iconView.setPadding(dp(14), dp(14), dp(14), dp(14));
        content.addView(iconView, LayoutHelper.createLinear(64, 64, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));

        TextView nameView = new TextView(activity);
        nameView.setText(meta.name);
        nameView.setTextSize(17);
        nameView.setGravity(Gravity.CENTER);
        nameView.setTypeface(AndroidUtilities.bold());
        nameView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
        content.addView(nameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0));

        StringBuilder infoText = new StringBuilder();
        if (!TextUtils.isEmpty(meta.author)) {
            infoText.append(meta.author);
        }
        if (!TextUtils.isEmpty(meta.version)) {
            if (infoText.length() > 0) {
                infoText.append(" • ");
            }
            infoText.append("v").append(meta.version);
        }
        infoText.append(" • Elyx");
        TextView infoView = new TextView(activity);
        infoView.setText(infoText.toString());
        infoView.setTextSize(13);
        infoView.setGravity(Gravity.CENTER);
        infoView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, rp));
        content.addView(infoView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));

        if (!TextUtils.isEmpty(meta.description)) {
            TextView descView = new TextView(activity);
            descView.setText(meta.description);
            descView.setTextSize(14);
            descView.setGravity(Gravity.CENTER);
            descView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
            content.addView(descView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));
        }

        String warning = versionWarning(meta);
        if (warning != null) {
            TextView warnView = new TextView(activity);
            warnView.setText(warning);
            warnView.setTextSize(12);
            warnView.setGravity(Gravity.CENTER);
            warnView.setTextColor(0xFFE77512);
            content.addView(warnView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));
        }

        final boolean alreadyInstalled = SquziPluginsController.isInstalled(meta.id);
        if (alreadyInstalled) {
            TextView installedView = new TextView(activity);
            installedView.setText("Уже установлен — повторная установка обновит файлы");
            installedView.setTextSize(12);
            installedView.setGravity(Gravity.CENTER);
            installedView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, rp));
            content.addView(installedView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity, rp);
        builder.setTitle("Установка плагина");
        builder.setView(content);
        builder.setPositiveButton(alreadyInstalled ? "Обновить" : "Установить", (dialog, which) -> {
            String id = install(zipFile, info);
            if (id != null) {
                Toast.makeText(activity, "Плагин «" + meta.name + "» установлен", Toast.LENGTH_SHORT).show();
                try {
                    SquziPluginRuntime.onPluginInstalled(id);
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            } else {
                Toast.makeText(activity, "Не удалось установить плагин", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.create().show();
    }

    private static String versionWarning(SquziPlugin meta) {
        try {
            if (!TextUtils.isEmpty(meta.appVersion)
                    && !SquziPlugin.checkVersion(meta.appVersion, BuildVars.BUILD_VERSION_STRING)) {
                return "Внимание: требует app " + meta.appVersion + " (у нас " + BuildVars.BUILD_VERSION_STRING + ")";
            }
            if (!TextUtils.isEmpty(meta.requirements)) {
                return "Зависимости pip: " + meta.requirements + " (ставятся вручную)";
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static int dp(float value) {
        return AndroidUtilities.dp(value);
    }
}
