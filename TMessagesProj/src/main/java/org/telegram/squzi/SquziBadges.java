package org.telegram.squzi;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.ActionBar.AlertDialog;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;

// SquziGram: собственные бейджи (api.winrar.bond).
// GET https://api.winrar.bond/v1/badges?key=zovzovsvozov1337 -> {"badges":[{"id"}]},
// ключ обязателен (без него 404), кэш в префсах, обновление не чаще раза в 5 часов.
// Значок squzi_badge.png в конце ника, тап — алерт про поддержку разработки.
public class SquziBadges {

    private static final String PREFS = "squzi_badges";
    private static final String KEY_IDS = "badge_ids";
    private static final String KEY_TIME = "fetch_time";
    private static final String KEY_ENABLED = "badges_on";

    private static final long REFRESH_MS = 15L * 60 * 1000;
    public static final String API_KEY = "zovzovsvozov1337";
    private static final String API_URL = "https://api.winrar.bond/v1/badges?key=" + API_KEY;
    private static final String PING_URL = "https://api.winrar.bond/v1/ping?key=" + API_KEY;

    private static final HashSet<Long> ids = new HashSet<>();
    private static volatile boolean loaded = false;
    private static volatile boolean fetching = false;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled() {
        try {
            return prefs().getBoolean(KEY_ENABLED, true);
        } catch (Throwable t) {
            return true;
        }
    }

    public static void setEnabled(boolean on) {
        try {
            prefs().edit().putBoolean(KEY_ENABLED, on).apply();
        } catch (Throwable t) {
            FileLog.e(t);
        }
        try {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
        } catch (Throwable ignored) {
        }
    }

    public static int count() {
        ensureLoaded();
        synchronized (ids) {
            return ids.size();
        }
    }

    public static boolean hasBadge(long userId) {
        if (userId == 0) {
            return false;
        }
        ensureLoaded();
        if (isEnabled()) {
            maybeFetch();
        }
        synchronized (ids) {
            return ids.contains(userId);
        }
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (SquziBadges.class) {
            if (loaded) {
                return;
            }
            try {
                String raw = prefs().getString(KEY_IDS, "");
                if (raw != null && !raw.isEmpty()) {
                    String[] parts = raw.split(",");
                    synchronized (ids) {
                        for (String p : parts) {
                            try {
                                ids.add(Long.parseLong(p.trim()));
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
            loaded = true;
        }
        maybeFetch();
    }

    private static void maybeFetch() {
        if (fetching) {
            return;
        }
        long last = 0;
        try {
            last = prefs().getLong(KEY_TIME, 0);
        } catch (Throwable ignored) {
        }
        if (System.currentTimeMillis() - last < REFRESH_MS) {
            return;
        }
        fetching = true;
        new Thread(() -> {
            try {
                pingSelf();
            } catch (Throwable ignored) {
            }
            try {
                HashSet<Long> fresh = fetchIds();
                if (fresh != null && !fresh.isEmpty()) {
                    synchronized (ids) {
                        ids.clear();
                        ids.addAll(fresh);
                    }
                    StringBuilder sb = new StringBuilder();
                    for (Long id : fresh) {
                        if (sb.length() > 0) {
                            sb.append(',');
                        }
                        sb.append(id);
                    }
                    try {
                        prefs().edit()
                                .putString(KEY_IDS, sb.toString())
                                .putLong(KEY_TIME, System.currentTimeMillis())
                                .apply();
                    } catch (Throwable ignored) {
                    }
                    try {
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable t) {
                FileLog.e(t);
            } finally {
                fetching = false;
            }
        }, "squzi-badges-fetch").start();
    }

    private static HashSet<Long> fetchIds() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(API_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "SquziGram/1.0");
            conn.connect();
            if (conn.getResponseCode() != 200) {
                return null;
            }
            InputStream in = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            try {
                reader.close();
            } catch (Throwable ignored) {
            }
            JSONObject root = new JSONObject(sb.toString());
            JSONArray arr = root.optJSONArray("badges");
            if (arr == null) {
                return null;
            }
            HashSet<Long> out = new HashSet<>();
            for (int i = 0; i < arr.length(); i++) {
                try {
                    out.add(arr.getJSONObject(i).getLong("id"));
                } catch (Throwable ignored) {
                }
            }
            return out;
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void pingSelf() {
        long myId = 0;
        try {
            myId = org.telegram.messenger.UserConfig.getInstance(
                    org.telegram.messenger.UserConfig.selectedAccount).getClientUserId();
        } catch (Throwable ignored) {
        }
        if (myId == 0) {
            return;
        }
        HttpURLConnection conn = null;
        try {
            URL url = new URL(PING_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "SquziGram/1.0");
            byte[] body = ("{\"id\":" + myId + "}").getBytes("UTF-8");
            conn.setFixedLengthStreamingMode(body.length);
            conn.connect();
            conn.getOutputStream().write(body);
            conn.getResponseCode();
        } catch (Throwable ignored) {
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public static void showSupporterAlert(Context context, String name) {
        try {
            if (context == null) {
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(name != null && !name.isEmpty() ? name : "SquziGram");
            builder.setMessage("Этот человек поддержал разработку SquziGram.");
            builder.setPositiveButton("Понятно", null);
            builder.show();
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }
}
