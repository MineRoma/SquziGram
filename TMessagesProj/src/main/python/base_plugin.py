"""SquziGram plugin SDK core, exteraGram-compatible (v1).

Covers the parts of the exteraGram SDK used by most plugins:
metadata constants, BasePlugin lifecycle, outgoing-message hook,
request/update hooks registry, menu items registry, persistent settings.
Low-level Xposed hooking and full client utils arrive in v2.
"""

import json
import os
import traceback


class HookStrategy:
    DEFAULT = 0
    CANCEL = 1
    MODIFY = 2
    MODIFY_FINAL = 3


class HookResult:
    def __init__(self, strategy=HookStrategy.DEFAULT, params=None,
                 response=None, update=None, updates=None):
        self.strategy = strategy
        self.params = params
        self.response = response
        self.update = update
        self.updates = updates


class MenuItemType:
    MESSAGE_CONTEXT_MENU = 0
    DRAWER_MENU = 1
    MAIN_MENU = 2
    CHAT_ACTION_MENU = 3
    PROFILE_ACTION_MENU = 4


class MenuItemData:
    def __init__(self, menu_type, text, on_click, item_id=None,
                 icon=None, subtext=None, condition=None, priority=0):
        self.menu_type = menu_type
        self.text = text
        self.on_click = on_click
        self.item_id = item_id
        self.icon = icon
        self.subtext = subtext
        self.condition = condition
        self.priority = priority


class MethodHook:
    """Xposed-style hook (v2: applied by the Java runtime)."""

    def before_hooked_method(self, param):
        pass

    def after_hooked_method(self, param):
        pass


class MethodReplacement:
    """Xposed-style replacement (v2: applied by the Java runtime)."""

    def replace_hooked_method(self, param):
        return None


class BasePlugin:
    # Set by squzi_loader after instantiation, do not touch.
    _data_dir = None

    def __init__(self):
        self._send_message_hooks = []
        self._hooks = {}
        self._menu_items = []
        self._settings = {}
        self._settings_path = None

    # -- internal ---------------------------------------------------------
    def _init_data(self, data_dir):
        try:
            self._data_dir = data_dir
            if data_dir:
                os.makedirs(data_dir, exist_ok=True)
                self._settings_path = os.path.join(data_dir, "settings.json")
                if os.path.isfile(self._settings_path):
                    with open(self._settings_path, "r", encoding="utf-8") as f:
                        loaded = json.load(f)
                        if isinstance(loaded, dict):
                            self._settings = loaded
        except Exception:
            traceback.print_exc()

    def _save_settings(self):
        try:
            if self._settings_path:
                with open(self._settings_path, "w", encoding="utf-8") as f:
                    json.dump(self._settings, f, ensure_ascii=False)
        except Exception:
            traceback.print_exc()

    # -- logging ----------------------------------------------------------
    def log(self, message):
        print("[squzi] " + str(message))

    # -- lifecycle (override in plugin) -----------------------------------
    def on_plugin_load(self):
        pass

    def on_plugin_unload(self):
        pass

    # -- hooks ------------------------------------------------------------
    def add_hook(self, name, match_substring=False, priority=0):
        self._hooks.setdefault(str(name), []).append(
            {"match_substring": bool(match_substring), "priority": int(priority)})

    def add_on_send_message_hook(self, priority=0):
        self._send_message_hooks.append(int(priority))

    def remove_hook(self, name):
        self._hooks.pop(str(name), None)

    def clear_hooks(self):
        self._hooks = {}
        self._send_message_hooks = []

    # -- events (override in plugin, must be registered above) ------------
    def on_send_message_hook(self, account, params):
        return HookResult()

    def on_request_hook(self, request_name, account, request):
        return HookResult()

    def on_response_hook(self, request_name, account, response):
        return HookResult()

    def on_update_hook(self, update_name, account, update):
        return HookResult()

    def on_updates_hook(self, container_name, account, updates):
        return HookResult()

    # -- settings storage -------------------------------------------------
    def get_setting(self, key, default=None):
        return self._settings.get(str(key), default)

    def set_setting(self, key, value, reload_settings=False):
        self._settings[str(key)] = value
        self._save_settings()

    def export_settings(self):
        return dict(self._settings)

    def import_settings(self, settings, reload_settings=True):
        if isinstance(settings, dict):
            self._settings = dict(settings)
            self._save_settings()

    # -- settings page (override in plugin) -------------------------------
    def create_settings(self):
        return []

    # -- menu items -------------------------------------------------------
    def add_menu_item(self, item):
        if item is not None:
            self._menu_items.append(item)

    def remove_menu_item(self, item_id):
        self._menu_items = [i for i in self._menu_items
                            if getattr(i, "item_id", None) != item_id]
