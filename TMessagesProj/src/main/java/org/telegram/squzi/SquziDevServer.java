package org.telegram.squzi;

import org.json.JSONObject;
import org.telegram.messenger.FileLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

// SquziGram: сервер разработки для десктопных инструментов (порт 42690,
// только localhost). Команды — JSON одной строкой, ответ — JSON одной строкой:
// ping, get_plugins, enable_plugin, disable_plugin, reload_plugin,
// write_plugin {plugin_id, content}, remove_plugin.
public class SquziDevServer {

    private static volatile boolean running = false;

    public static void start() {
        if (running) {
            return;
        }
        running = true;
        new Thread(() -> {
            ServerSocket server = null;
            try {
                server = new ServerSocket(42690, 4, InetAddress.getByName("127.0.0.1"));
                while (running) {
                    try {
                        final Socket socket = server.accept();
                        new Thread(() -> handle(socket), "squzi-dev-conn").start();
                    } catch (Throwable t) {
                        FileLog.e(t);
                    }
                }
            } catch (Throwable t) {
                FileLog.e(t);
            } finally {
                if (server != null) {
                    try {
                        server.close();
                    } catch (Throwable ignored) {
                    }
                }
                running = false;
            }
        }, "squzi-dev-server").start();
    }

    private static void handle(Socket socket) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = socket.getOutputStream();
            String line = in.readLine();
            JSONObject response = new JSONObject();
            try {
                if (line == null) {
                    response.put("ok", false).put("error", "empty");
                } else {
                    response = execute(new JSONObject(line));
                }
            } catch (Throwable t) {
                FileLog.e(t);
                response = new JSONObject().put("ok", false).put("error", "bad json");
            }
            out.write((response.toString() + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (Throwable t) {
            FileLog.e(t);
        } finally {
            try {
                socket.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static JSONObject execute(JSONObject cmd) {
        JSONObject response = new JSONObject();
        try {
            String command = cmd.optString("command", cmd.optString("cmd", ""));
            switch (command) {
                case "ping":
                    response.put("ok", true).put("result", "pong");
                    break;
                case "get_plugins": {
                    org.json.JSONArray array = new org.json.JSONArray();
                    List<SquziPlugin> plugins = SquziPluginsController.getInstalled();
                    for (SquziPlugin p : plugins) {
                        JSONObject o = new JSONObject();
                        o.put("id", p.id);
                        o.put("name", p.name);
                        o.put("version", p.version);
                        o.put("enabled", p.enabled);
                        array.put(o);
                    }
                    response.put("ok", true).put("plugins", array);
                    break;
                }
                case "enable_plugin": {
                    String id = cmd.optString("plugin_id", "");
                    SquziPluginsController.setEnabled(id, true);
                    SquziPluginRuntime.onPluginEnabled(id);
                    response.put("ok", true);
                    break;
                }
                case "disable_plugin": {
                    String id = cmd.optString("plugin_id", "");
                    SquziPluginsController.setEnabled(id, false);
                    SquziPluginRuntime.onPluginDisabled(id);
                    response.put("ok", true);
                    break;
                }
                case "reload_plugin": {
                    String id = cmd.optString("plugin_id", "");
                    SquziPluginRuntime.unloadPlugin(id);
                    if (SquziPluginsController.isEnabled(id)) {
                        SquziPluginRuntime.loadPlugin(id);
                    }
                    response.put("ok", true);
                    break;
                }
                case "write_plugin": {
                    String id = SquziPlugin.sanitizeId(cmd.optString("plugin_id", ""));
                    String content = cmd.optString("content", "");
                    File dst = new File(SquziPluginsController.pluginsDirPublic(), id + ".plugin");
                    FileOutputStream fos = new FileOutputStream(dst);
                    try {
                        fos.write(content.getBytes(StandardCharsets.UTF_8));
                    } finally {
                        fos.close();
                    }
                    SquziPluginsController.setEnabled(id, true);
                    SquziPluginRuntime.unloadPlugin(id);
                    SquziPluginRuntime.loadPlugin(id);
                    response.put("ok", true).put("plugin_id", id);
                    break;
                }
                case "remove_plugin": {
                    String id = cmd.optString("plugin_id", "");
                    SquziPluginRuntime.unloadPlugin(id);
                    SquziPluginsController.delete(id);
                    response.put("ok", true);
                    break;
                }
                default:
                    response.put("ok", false).put("error", "unknown command");
                    break;
            }
        } catch (Throwable t) {
            FileLog.e(t);
            try {
                response.put("ok", false).put("error", "failed");
            } catch (Throwable ignored) {
            }
        }
        return response;
    }
}
