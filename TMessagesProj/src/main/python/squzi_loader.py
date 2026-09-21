"""SquziGram plugin loader: keeps isolated modules per plugin id,
instantiates the BasePlugin subclass, dispatches outgoing-message hooks.
"""

import importlib.util
import os
import sys
import traceback

import base_plugin

_plugins = {}
_files_dir = None
_plugins_dir = None
_data_root = None
_file_handlers = {}


def configure(files_dir, plugins_dir, data_root):
    global _files_dir, _plugins_dir, _data_root
    _files_dir = files_dir
    _plugins_dir = plugins_dir
    _data_root = data_root
    return True


def loaded_ids():
    return list(_plugins.keys())


def _find_plugin_class(module):
    for value in vars(module).values():
        if (isinstance(value, type)
                and issubclass(value, base_plugin.BasePlugin)
                and value is not base_plugin.BasePlugin):
            return value
    return None


def load_plugin_file(path, plugin_id):
    """Load (or reload) one .plugin file. Returns None on success,
    otherwise an error string."""
    unload_plugin(plugin_id)
    try:
        module_name = "squzi_plugin_" + str(plugin_id)
        sys.modules.pop(module_name, None)
        # .plugin — нестандартное расширение: spec_from_file_location
        # не находит лоадер, грузим явно через SourceFileLoader.
        from importlib.machinery import SourceFileLoader
        loader = SourceFileLoader(module_name, path)
        spec = importlib.util.spec_from_loader(module_name, loader)
        if spec is None:
            return "bad spec"
        module = importlib.util.module_from_spec(spec)
        sys.modules[module_name] = module
        loader.exec_module(module)
        cls = _find_plugin_class(module)
        if cls is None:
            sys.modules.pop(module_name, None)
            return "no BasePlugin subclass"
        instance = cls()
        data_dir = os.path.join(_data_root, str(plugin_id)) if _data_root else None
        instance._init_data(data_dir)
        _plugins[str(plugin_id)] = instance
        try:
            instance.on_plugin_load()
        except Exception:
            traceback.print_exc()
            return "on_plugin_load failed"
        return None
    except Exception:
        traceback.print_exc()
        sys.modules.pop("squzi_plugin_" + str(plugin_id), None)
        return "load failed"


def unload_plugin(plugin_id):
    plugin_id = str(plugin_id)
    instance = _plugins.pop(plugin_id, None)
    if instance is not None:
        try:
            instance.on_plugin_unload()
        except Exception:
            traceback.print_exc()
    sys.modules.pop("squzi_plugin_" + plugin_id, None)
    return True


class _SendParams:
    def __init__(self, message):
        self.message = message


def dispatch_on_send_message(account, text):
    """Run on_send_message_hook of all loaded plugins with a registered hook.
    Returns (action, text) where action is 'pass', 'modify' or 'cancel'."""
    current = text if isinstance(text, str) else ""
    try:
        ordered = sorted(_plugins.items(),
                         key=lambda kv: max(kv[1]._send_message_hooks or [0]))
        for _, instance in ordered:
            if not instance._send_message_hooks:
                continue
            try:
                params = _SendParams(current)
                result = instance.on_send_message_hook(int(account), params)
                strategy = result.strategy if result is not None else 0
                if strategy == base_plugin.HookStrategy.CANCEL:
                    return ("cancel", current)
                if strategy in (base_plugin.HookStrategy.MODIFY,
                                base_plugin.HookStrategy.MODIFY_FINAL):
                    new_text = getattr(result.params, "message", None)
                    if isinstance(new_text, str):
                        current = new_text
                    if strategy == base_plugin.HookStrategy.MODIFY_FINAL:
                        break
            except Exception:
                traceback.print_exc()
    except Exception:
        traceback.print_exc()
    if current != (text if isinstance(text, str) else ""):
        return ("modify", current)
    return ("pass", current)


def _row_dict(row, settings):
    cls = type(row).__name__
    get = lambda name, default=None: getattr(row, name, default)
    if cls == "Header":
        return {"type": "header", "text": str(get("text", ""))}
    if cls == "Text":
        return {"type": "text", "text": str(get("text", "")),
                "subtext": get("subtext"), "icon": get("icon")}
    if cls == "Input":
        key = str(get("key", ""))
        default = get("default", "")
        value = settings.get(key, default)
        return {"type": "input", "key": key, "text": str(get("text", "")),
                "subtext": get("subtext"), "icon": get("icon"),
                "value": value if isinstance(value, str) else str(value)}
    if cls == "Switch":
        key = str(get("key", ""))
        default = bool(get("default", False))
        value = settings.get(key, default)
        return {"type": "switch", "key": key, "text": str(get("text", "")),
                "subtext": get("subtext"), "icon": get("icon"),
                "value": bool(value)}
    return None


def describe_settings_json(plugin_id):
    """Settings rows of a loaded plugin as JSON: list of row dicts."""
    import json
    try:
        instance = _plugins.get(str(plugin_id))
        if instance is None:
            return json.dumps({"rows": [], "error": "not loaded"})
        try:
            rows = instance.create_settings()
        except Exception:
            traceback.print_exc()
            return json.dumps({"rows": [], "error": "create_settings failed"})
        out = []
        settings = dict(getattr(instance, "_settings", {}) or {})
        for row in rows or []:
            try:
                item = _row_dict(row, settings)
                if item is not None:
                    out.append(item)
            except Exception:
                traceback.print_exc()
        return json.dumps({"rows": out, "error": None}, ensure_ascii=False)
    except Exception:
        traceback.print_exc()
        return json.dumps({"rows": [], "error": "failed"})


def set_setting_json(plugin_id, key, value_json):
    """Set one plugin setting from Java (value passed as JSON)."""
    import json
    try:
        instance = _plugins.get(str(plugin_id))
        if instance is None:
            return False
        try:
            value = json.loads(value_json)
        except Exception:
            value = value_json
        instance.set_setting(str(key), value)
        return True
    except Exception:
        traceback.print_exc()
        return False


def _java_class_name(obj):
    try:
        return str(obj.getClass().getSimpleName())
    except Exception:
        try:
            return type(obj).__name__
        except Exception:
            return ""


def dispatch_request_hook(account, request):
    """Outgoing TL request hook. Returns 1 to cancel, else 0
    (modifications apply in place on the Java object)."""
    try:
        name = _java_class_name(request)
        for _, instance in list(_plugins.items()):
            hooks = getattr(instance, "_hooks", {}) or {}
            for hook_name, opts in list(hooks.items()):
                matched = (hook_name == name)
                if not matched:
                    for opt in opts or []:
                        try:
                            if opt.get("match_substring") and hook_name in name:
                                matched = True
                                break
                        except Exception:
                            pass
                if not matched:
                    continue
                try:
                    result = instance.on_request_hook(hook_name, int(account), request)
                    strategy = result.strategy if result is not None else 0
                    if strategy == base_plugin.HookStrategy.CANCEL:
                        return 1
                    if strategy == base_plugin.HookStrategy.MODIFY_FINAL:
                        break
                except Exception:
                    traceback.print_exc()
    except Exception:
        traceback.print_exc()
    return 0


def dispatch_updates_hook(account, updates):
    """Incoming updates hook (modify in place; cancel not supported)."""
    try:
        batch = []
        try:
            items = updates.updates
            for item in items:
                batch.append(item)
        except Exception:
            try:
                batch.append(updates.update)
            except Exception:
                return True
        for update in batch:
            uname = _java_class_name(update)
            for _, instance in list(_plugins.items()):
                hooks = getattr(instance, "_hooks", {}) or {}
                for hook_name, opts in list(hooks.items()):
                    matched = (hook_name == uname)
                    if not matched:
                        for opt in opts or []:
                            try:
                                if opt.get("match_substring") and hook_name in uname:
                                    matched = True
                                    break
                            except Exception:
                                pass
                    if not matched:
                        continue
                    try:
                        instance.on_update_hook(uname, int(account), update)
                    except Exception:
                        traceback.print_exc()
    except Exception:
        traceback.print_exc()
    return True


def list_menu_items_json():
    """MESSAGE_CONTEXT_MENU items of loaded plugins, priority first."""
    import json
    out = []
    try:
        for plugin_id, instance in list(_plugins.items()):
            for item in getattr(instance, "_menu_items", []) or []:
                try:
                    if getattr(item, "menu_type", -1) != base_plugin.MenuItemType.MESSAGE_CONTEXT_MENU:
                        continue
                    out.append({
                        "plugin": str(plugin_id),
                        "item_id": str(getattr(item, "item_id", None) or getattr(item, "text", "")),
                        "text": str(getattr(item, "text", "")),
                        "subtext": getattr(item, "subtext", None),
                        "icon": getattr(item, "icon", None),
                        "priority": int(getattr(item, "priority", 0) or 0),
                    })
                except Exception:
                    traceback.print_exc()
        out.sort(key=lambda r: -r["priority"])
    except Exception:
        traceback.print_exc()
    return json.dumps(out, ensure_ascii=False)


def activate_menu_item(plugin_id, item_id, context_json):
    """Call a menu item on_click with the context dict."""
    import json
    try:
        instance = _plugins.get(str(plugin_id))
        if instance is None:
            return False
        try:
            context = json.loads(context_json)
        except Exception:
            context = {}
        for item in getattr(instance, "_menu_items", []) or []:
            try:
                current = str(getattr(item, "item_id", None) or getattr(item, "text", ""))
                if current == str(item_id):
                    item.on_click(dict(context) if isinstance(context, dict) else {})
                    return True
            except Exception:
                traceback.print_exc()
                return False
        return False
    except Exception:
        traceback.print_exc()
        return False


def has_file_handler(ext):
    try:
        return bool(_file_handlers.get(str(ext).lower().lstrip(".")))
    except Exception:
        return False


def dispatch_file_open(ext, path, file_name, place="chat"):
    """Custom file-open handlers (FilesController). Returns True if
    any handler consumed the open."""
    try:
        handlers = list(_file_handlers.get(str(ext).lower().lstrip("."), []))
        from file_utils import OnClickArgs
        for _, callback in handlers:
            try:
                result = callback(OnClickArgs(str(file_name), str(path), str(place)))
                if result:
                    return True
            except Exception:
                traceback.print_exc()
        return False
    except Exception:
        traceback.print_exc()
        return False
