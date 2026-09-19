package org.telegram.squzi;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// SquziGram: метаданные плагина в формате exteraGram.
// .plugin-файл — это Python-исходник (.py, переименованный в .plugin),
// метаданные — плоские константы верхнего уровня: __id__, __name__,
// __description__, __author__, __version__, __icon__, __app_version__,
// __sdk_version__, __requirements__. Парсим так же, как лоадер exteraGram:
// статически, регулярками (у них — через AST, суть та же).
public class SquziPlugin {

    public String id;
    public String name;
    public String description;
    public String author;
    public String version;
    public String icon;
    public String appVersion;
    public String sdkVersion;
    public String requirements;

    // Имя установленного файла (например "weather.plugin"), null если файл ещё не установлен.
    public String installedFileName;
    public boolean enabled;

    private static String str(String src, String key) {
        Pattern p = Pattern.compile("__" + key + "__\\s*=\\s*['\"]([^'\"]*)['\"]");
        Matcher m = p.matcher(src);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private static String listRaw(String src, String key) {
        Pattern p = Pattern.compile("__" + key + "__\\s*=\\s*\\[([^\\]]*)\\]", Pattern.DOTALL);
        Matcher m = p.matcher(src);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    public static SquziPlugin parse(File file) {
        String head = readHead(file, 128 * 1024);
        SquziPlugin plugin = new SquziPlugin();
        String fallbackId = file != null ? sanitizeId(stripExtension(file.getName())) : "plugin";

        plugin.id = str(head, "id");
        if (!isValidId(plugin.id)) {
            plugin.id = fallbackId;
        }
        plugin.name = str(head, "name");
        if (plugin.name == null || plugin.name.isEmpty()) {
            plugin.name = plugin.id;
        }
        plugin.description = str(head, "description");
        if (plugin.description == null) {
            plugin.description = "";
        }
        plugin.author = str(head, "author");
        if (plugin.author == null) {
            plugin.author = "";
        }
        plugin.version = str(head, "version");
        if (plugin.version == null || plugin.version.isEmpty()) {
            plugin.version = "1.0";
        }
        plugin.icon = str(head, "icon");
        if (plugin.icon == null) {
            plugin.icon = "";
        }
        plugin.appVersion = str(head, "app_version");
        if (plugin.appVersion == null) {
            plugin.appVersion = "";
        }
        plugin.sdkVersion = str(head, "sdk_version");
        if (plugin.sdkVersion == null) {
            plugin.sdkVersion = "";
        }
        plugin.requirements = listRaw(head, "requirements");
        if (plugin.requirements == null) {
            plugin.requirements = "";
        }
        return plugin;
    }

    // Совместимая версия SDK: заявляем поверхность exteraGram 1.4.4.3
    // для реализованных частей API.
    public static final String SQUIZI_SDK_VERSION = "1.4.4.3";

    // Проверка выражений вида ">=12.5.1" (можно несколько через запятую/пробел).
    // Пустое выражение = без ограничений.
    public static boolean checkVersion(String expr, String current) {
        if (expr == null || expr.trim().isEmpty() || current == null) {
            return true;
        }
        String[] clauses = expr.split("[,\\s]+");
        for (String clause : clauses) {
            if (clause.isEmpty()) {
                continue;
            }
            String op;
            String ver;
            if (clause.startsWith(">=") || clause.startsWith("<=") || clause.startsWith("==")) {
                op = clause.substring(0, 2);
                ver = clause.substring(2);
            } else if (clause.startsWith(">") || clause.startsWith("<") || clause.startsWith("=")) {
                op = clause.substring(0, 1);
                ver = clause.substring(1);
            } else {
                op = "==";
                ver = clause;
            }
            int cmp = compareVersions(current.trim(), ver.trim());
            boolean ok;
            if ("==".equals(op) || "=".equals(op)) {
                ok = cmp == 0;
            } else if (">=".equals(op)) {
                ok = cmp >= 0;
            } else if ("<=".equals(op)) {
                ok = cmp <= 0;
            } else if (">".equals(op)) {
                ok = cmp > 0;
            } else {
                ok = cmp < 0;
            }
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    public static int compareVersions(String a, String b) {
        String[] pa = a.split("[.\\-]");
        String[] pb = b.split("[.\\-]");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int va = i < pa.length ? parsePart(pa[i]) : 0;
            int vb = i < pb.length ? parsePart(pb[i]) : 0;
            if (va != vb) {
                return va < vb ? -1 : 1;
            }
        }
        return 0;
    }

    private static int parsePart(String s) {
        try {
            StringBuilder digits = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c >= '0' && c <= '9') {
                    digits.append(c);
                } else {
                    break;
                }
            }
            return digits.length() == 0 ? 0 : Integer.parseInt(digits.toString());
        } catch (Throwable t) {
            return 0;
        }
    }

    public static boolean isValidId(String id) {
        if (id == null || id.length() < 2 || id.length() > 32) {
            return false;
        }
        if (!Character.isLetter(id.charAt(0))) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_' || c == '-')) {
                return false;
            }
        }
        return true;
    }

    public static String sanitizeId(String raw) {
        if (raw == null) {
            raw = "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_' || c == '-') {
                sb.append(c);
            }
        }
        String id = sb.toString();
        while (!id.isEmpty() && !Character.isLetter(id.charAt(0))) {
            id = id.substring(1);
        }
        if (id.length() > 32) {
            id = id.substring(0, 32);
        }
        if (id.length() < 2) {
            id = "plugin";
        }
        return id;
    }

    private static String stripExtension(String name) {
        if (name == null) {
            return "";
        }
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(0, idx) : name;
    }

    private static String readHead(File file, int maxBytes) {
        if (file == null || !file.exists()) {
            return "";
        }
        FileInputStream in = null;
        try {
            long len = Math.min(file.length(), maxBytes);
            if (len <= 0) {
                return "";
            }
            byte[] buf = new byte[(int) len];
            in = new FileInputStream(file);
            int read = 0;
            while (read < buf.length) {
                int n = in.read(buf, read, buf.length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            return new String(buf, 0, read, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
