"""File helpers + FilesController: custom file-open handlers (v2).

Mirrors the exteraGram file_utils surface: directories plus
FilesController.register(FileInfo(ext, on_click, ...)) which the Java
runtime consults in AndroidUtilities.openForView.
"""

import os


def _dirs():
    import squzi_loader
    return (squzi_loader._files_dir or "", squzi_loader._plugins_dir or "")


def get_plugins_dir():
    return _dirs()[1]


def get_files_dir():
    return _dirs()[0]


def _join(base, *parts):
    return os.path.join(base, *[str(p) for p in parts])


def get_cache_dir():
    return _join(_dirs()[0], "cache")


def get_images_dir():
    return ""


def get_videos_dir():
    return ""


def get_audios_dir():
    return ""


def get_documents_dir():
    return ""


class Place:
    CHAT = "chat"
    DOWNLOADS = "downloads"
    FILES = "files"


class OnClickArgs:
    def __init__(self, file_name, path, place=Place.CHAT):
        self.file_name = file_name
        self.path = path
        self.place = place


class FileInfo:
    def __init__(self, ext, on_click, whitelist_places=None,
                 blacklist_places=None, get_icon=None):
        self.ext = str(ext).lower().lstrip(".")
        self.on_click = on_click
        self.whitelist_places = whitelist_places or []
        self.blacklist_places = blacklist_places or []
        self.get_icon = get_icon


class _Registration:
    def __init__(self, plugin_id, info):
        self.plugin_id = plugin_id
        self.info = info
        self.secret = "squzi:%s:%s" % (plugin_id, info.ext)


class FilesController:
    """Register handlers called instead of the default open flow."""

    @staticmethod
    def _caller_plugin_id(func):
        import sys
        try:
            for name, mod in list(sys.modules.items()):
                if name.startswith("squzi_plugin_") and mod is not None:
                    try:
                        if any(v is func for v in vars(mod).values()):
                            return name[len("squzi_plugin_"):]
                    except Exception:
                        pass
        except Exception:
            pass
        return None

    @staticmethod
    def register(file_info):
        import squzi_loader
        plugin_id = FilesController._caller_plugin_id(file_info.on_click)
        if not plugin_id:
            loaded = squzi_loader.loaded_ids()
            plugin_id = loaded[-1] if loaded else "unknown"
        reg = _Registration(plugin_id, file_info)
        squzi_loader._file_handlers.setdefault(file_info.ext, []).append(
            (plugin_id, file_info.on_click))
        return reg.secret

    @staticmethod
    def unregister(secret):
        import squzi_loader
        try:
            _, plugin_id, ext = str(secret).split(":", 2)
        except Exception:
            return False
        handlers = squzi_loader._file_handlers.get(ext, [])
        kept = [h for h in handlers if h[0] != plugin_id]
        if len(kept) != len(handlers):
            squzi_loader._file_handlers[ext] = kept
            return True
        return False
