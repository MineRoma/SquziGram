package org.telegram.squzi;

import android.content.Context;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

// SquziGram: Python-движок плагинов (формат exteraGram) на Chaquopy.
// Старт — в фоне из ApplicationLoader.onCreate. Грузит только включённые
// плагины, дёргает on_plugin_load / on_plugin_unload, раздаёт хук
// исходящих сообщений (add_on_send_message_hook -> MODIFY / CANCEL).
public class SquziPluginRuntime {

    private static volatile boolean started = false;
    private static volatile boolean ready = false;
    private static volatile PyObject loader = null;

    public static class OutgoingResult {
        public boolean cancel;
        public String text;
    }

    public static class MenuItem {
        public String pluginId;
        public String itemId;
        public String text;
        public String subtext;
        public String icon;
    }

    public static void initInBackground() {
        new Thread(() -> {
            try {
                init();
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }, "squzi-plugins").start();
    }

    private static synchronized void init() {
        if (started) {
            return;
        }
        started = true;
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx == null) {
                return;
            }
            if (!Python.isStarted()) {
                Python.start(new AndroidPlatform(ctx));
            }
            PyObject module = Python.getInstance().getModule("squzi_loader");
            File filesDir = ctx.getFilesDir();
            File pluginsDir = new File(filesDir, "squzi_plugins");
            File dataRoot = new File(filesDir, "squzi_plugin_data");
            // noinspection ResultOfMethodCallIgnored
            pluginsDir.mkdirs();
            // noinspection ResultOfMethodCallIgnored
            dataRoot.mkdirs();
            module.callAttr("configure", filesDir.getAbsolutePath(),
                    pluginsDir.getAbsolutePath(), dataRoot.getAbsolutePath());
            loader = module;
            ready = true;
            for (SquziPlugin plugin : SquziPluginsController.getInstalled()) {
                if (plugin.enabled) {
                    loadPluginLocked(plugin.id);
                }
            }
            try {
                SquziDevServer.start();
            } catch (Throwable t) {
                FileLog.e(t);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public static boolean isReady() {
        return ready;
    }

    private static File pluginFile(String id) {
        if (id == null) {
            return null;
        }
        try {
            return SquziPluginsController.getPluginMainFile(id);
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    private static boolean loadPluginLocked(String id) {
        try {
            File file = pluginFile(id);
            if (file == null || !file.exists()) {
                return false;
            }
            PyObject error = loader.callAttr("load_plugin_file", file.getAbsolutePath(), id);
            return error == null || "None".equals(error.toString());
        } catch (Throwable t) {
            FileLog.e(t);
            return false;
        }
    }

    public static synchronized boolean loadPlugin(String id) {
        if (!ready || id == null) {
            return false;
        }
        return loadPluginLocked(id);
    }

    public static synchronized void unloadPlugin(String id) {
        if (!ready || id == null) {
            return;
        }
        try {
            loader.callAttr("unload_plugin", id);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public static void onPluginEnabled(String id) {
        loadPlugin(id);
    }

    public static void onPluginDisabled(String id) {
        unloadPlugin(id);
    }

    public static void onPluginInstalled(String id) {
        if (id != null && SquziPluginsController.isEnabled(id)) {
            loadPlugin(id);
        }
    }

    public static void onPluginDeleted(String id) {
        unloadPlugin(id);
    }

    // Хук исходящих сообщений. Возвращает null только если движок не готов
    // (тогда отправка идёт как обычно).
    public static OutgoingResult processOutgoingText(int account, String text) {
        OutgoingResult result = new OutgoingResult();
        result.cancel = false;
        result.text = text;
        if (!ready || text == null) {
            return result;
        }
        try {
            PyObject response = loader.callAttr("dispatch_on_send_message", account, text);
            if (response == null) {
                return result;
            }
            List<PyObject> parts = response.asList();
            if (parts == null || parts.size() < 2) {
                return result;
            }
            String action = parts.get(0).toString();
            String newText = parts.get(1).toString();
            if ("cancel".equals(action)) {
                result.cancel = true;
            } else if ("modify".equals(action)) {
                result.text = newText;
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        return result;
    }

    // JSON с рядами настроек плагина (create_settings). Null если движок
    // не готов или плагин не загружен (выключен).
    public static String getSettingsJson(String id) {
        if (!ready || id == null) {
            return null;
        }
        try {
            PyObject response = loader.callAttr("describe_settings_json", id);
            return response == null ? null : response.toString();
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    public static boolean setSetting(String id, String key, String valueJson) {
        if (!ready || id == null || key == null) {
            return false;
        }
        try {
            PyObject response = loader.callAttr("set_setting_json", id, key,
                    valueJson == null ? "null" : valueJson);
            return response != null && "True".equals(response.toString());
        } catch (Throwable t) {
            FileLog.e(t);
            return false;
        }
    }

    // Хук исходящих TL-запросов. 1 = отменить, 0 = пропустить
    // (изменения объекта применяются плагином на месте).
    public static int processOutgoingRequest(int account, TLObject request) {
        if (!ready || request == null) {
            return 0;
        }
        try {
            PyObject response = loader.callAttr("dispatch_request_hook", account, request);
            if (response == null) {
                return 0;
            }
            return "1".equals(response.toString()) ? 1 : 0;
        } catch (Throwable t) {
            FileLog.e(t);
            return 0;
        }
    }

    // Хук входящих апдейтов (изменения применяются на месте).
    public static void processUpdates(int account, TLRPC.Updates updates) {
        if (!ready || updates == null) {
            return;
        }
        try {
            loader.callAttr("dispatch_updates_hook", account, updates);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public static List<MenuItem> getMessageMenuItems() {
        List<MenuItem> result = new ArrayList<>();
        if (!ready) {
            return result;
        }
        try {
            PyObject response = loader.callAttr("list_menu_items_json");
            if (response == null) {
                return result;
            }
            JSONArray array = new JSONArray(response.toString());
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                MenuItem item = new MenuItem();
                item.pluginId = o.optString("plugin", "");
                item.itemId = o.optString("item_id", "");
                item.text = o.optString("text", "");
                item.subtext = o.optString("subtext", "");
                item.icon = o.optString("icon", "");
                if (item.text.isEmpty() || item.pluginId.isEmpty()) {
                    continue;
                }
                result.add(item);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        return result;
    }

    public static void dispatchMenuItem(String pluginId, String itemId, int account,
                                        long dialogId, int messageId, String messageText) {
        if (!ready || pluginId == null || itemId == null) {
            return;
        }
        try {
            JSONObject context = new JSONObject();
            context.put("account", account);
            context.put("dialog_id", dialogId);
            context.put("message_id", messageId);
            context.put("message_text", messageText != null ? messageText : "");
            loader.callAttr("activate_menu_item", pluginId, itemId, context.toString());
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public static boolean hasFileHandler(String ext) {
        if (!ready || ext == null) {
            return false;
        }
        try {
            PyObject response = loader.callAttr("has_file_handler", ext);
            return response != null && "True".equals(response.toString());
        } catch (Throwable t) {
            FileLog.e(t);
            return false;
        }
    }

    // Возвращает true если плагин поглотил открытие файла.
    public static boolean dispatchFileOpen(String ext, String path, String fileName) {
        if (!ready || ext == null || path == null) {
            return false;
        }
        try {
            PyObject response = loader.callAttr("dispatch_file_open", ext, path,
                    fileName != null ? fileName : "");
            return response != null && "True".equals(response.toString());
        } catch (Throwable t) {
            FileLog.e(t);
            return false;
        }
    }
}
