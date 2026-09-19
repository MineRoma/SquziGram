"""Minimal reflection helpers (v1: Xposed hooking arrives in v2)."""

try:
    from java.chaquopy import dynamic_proxy  # noqa: F401
except Exception:
    dynamic_proxy = None


def find_class(name):
    try:
        parts = str(name).split(".")
        obj = __import__(parts[0])
        for part in parts[1:]:
            obj = getattr(obj, part)
        return obj
    except Exception:
        return None
