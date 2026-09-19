package org.telegram.squzi;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

// SquziGram: хранение и управление плагинами.
// Совместимо с exteraGram: файл лежит в files/squzi_plugins/<id>.plugin,
// состояние вкл/выкл — в SharedPreferences. Исполнение кода (Chaquopy-движок)
// сюда подключится следующим этапом.
public class SquziPluginsController {

    private static final String PREFS_NAME = "squzi_plugins";
    private static final String KEY_ENABLED_PREFIX = "enabled_";

    private static File pluginsDir() {
        Context ctx = ApplicationLoader.applicationContext;
        File dir = new File(ctx.getFilesDir(), "squzi_plugins");
        if (!dir.exists()) {
            // noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static List<SquziPlugin> getInstalled() {
        List<SquziPlugin> result = new ArrayList<>();
        File dir = pluginsDir();
        File[] files = dir.listFiles();
        if (files == null) {
            return result;
        }
        for (File f : files) {
            String name = f.getName().toLowerCase();
            if (f.isFile() && name.endsWith(".plugin")) {
                try {
                    SquziPlugin plugin = SquziPlugin.parse(f);
                    plugin.installedFileName = f.getName();
                    plugin.enabled = isEnabled(plugin.id);
                    result.add(plugin);
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            } else if (f.isDirectory() && name.endsWith(".elyxdir")) {
                try {
                    SquziPlugin plugin = SquziElyxInstaller.parseInstalled(f);
                    if (plugin != null) {
                        plugin.installedFileName = f.getName();
                        plugin.enabled = isEnabled(plugin.id);
                        result.add(plugin);
                    }
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            }
        }
        Collections.sort(result, new Comparator<SquziPlugin>() {
            @Override
            public int compare(SquziPlugin a, SquziPlugin b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        return result;
    }

    public static boolean isInstalled(String id) {
        if (id == null) {
            return false;
        }
        return new File(pluginsDir(), id + ".plugin").exists()
                || new File(pluginsDir(), id + ".elyxdir").exists();
    }

    // Главный исполняемый файл плагина: <id>.plugin или main.py внутри <id>.elyxdir.
    public static File getPluginMainFile(String id) {
        if (id == null) {
            return null;
        }
        File single = new File(pluginsDir(), id + ".plugin");
        if (single.exists()) {
            return single;
        }
        File dir = new File(pluginsDir(), id + ".elyxdir");
        if (dir.exists()) {
            File main = SquziElyxInstaller.findMainFile(dir);
            if (main != null && main.exists()) {
                return main;
            }
        }
        return null;
    }

    public static boolean isEnabled(String id) {
        if (id == null) {
            return false;
        }
        return prefs().getBoolean(KEY_ENABLED_PREFIX + id, true);
    }

    public static void setEnabled(String id, boolean enabled) {
        if (id == null) {
            return;
        }
        prefs().edit().putBoolean(KEY_ENABLED_PREFIX + id, enabled).apply();
    }

    // Копирует .plugin-файл в хранилище под именем <id>.plugin. Возвращает id или null при ошибке.
    public static String install(File src, SquziPlugin meta) {
        if (src == null || !src.exists() || meta == null || meta.id == null) {
            return null;
        }
        File dir = pluginsDir();
        File dst = new File(dir, meta.id + ".plugin");
        FileInputStream in = null;
        FileOutputStream out = null;
        try {
            in = new FileInputStream(src);
            out = new FileOutputStream(dst);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.getFD().sync();
            setEnabled(meta.id, true);
            return meta.id;
        } catch (IOException e) {
            FileLog.e(e);
            // noinspection ResultOfMethodCallIgnored
            dst.delete();
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public static boolean delete(String id) {
        if (id == null) {
            return false;
        }
        File f = new File(pluginsDir(), id + ".plugin");
        boolean ok = !f.exists() || f.delete();
        File dir = new File(pluginsDir(), id + ".elyxdir");
        if (dir.exists()) {
            ok = deleteRecursive(dir) && ok;
        }
        prefs().edit().remove(KEY_ENABLED_PREFIX + id).apply();
        return ok;
    }

    public static boolean deleteRecursive(File file) {
        boolean ok = true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    ok = deleteRecursive(child) && ok;
                }
            }
        }
        return (!file.exists() || file.delete()) && ok;
    }

    public static File pluginsDirPublic() {
        return pluginsDir();
    }
}
