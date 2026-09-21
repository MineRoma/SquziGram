"""Minimal android helpers (v1)."""


def log(message):
    print("[squzi] " + str(message))


def run_on_ui_thread(func):
    try:
        from client_utils import run_on_ui_thread as _run
        return _run(func)
    except Exception:
        import traceback
        traceback.print_exc()
