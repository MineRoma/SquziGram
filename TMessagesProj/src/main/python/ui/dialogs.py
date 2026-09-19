"""Plugin UI helpers: toasts and dialogs (v2).

Runs on the UI thread. Dialogs use the foreground activity
(LaunchActivity.instance) and fall back to a toast.
"""


def _on_ui(func):
    try:
        from org.telegram.messenger import AndroidUtilities
        from client_utils import _as_runnable
        AndroidUtilities.runOnUIThread(_as_runnable(func))
    except Exception:
        import traceback
        traceback.print_exc()


def toast(text):
    def _do():
        from org.telegram.messenger import ApplicationLoader
        from android.widget import Toast
        ctx = ApplicationLoader.applicationContext
        if ctx is not None:
            Toast.makeText(ctx, str(text), Toast.LENGTH_SHORT).show()

    _on_ui(_do)


def show_dialog(title, message, button="OK"):
    def _do():
        try:
            from org.telegram.ui import LaunchActivity
            activity = LaunchActivity.instance
        except Exception:
            activity = None
        if activity is None:
            toast(str(title) + ": " + str(message))
            return
        try:
            from org.telegram.ui.ActionBar import AlertDialog
            builder = AlertDialog.Builder(activity)
            builder.setTitle(str(title))
            builder.setMessage(str(message))
            builder.setPositiveButton(str(button), None)
            builder.create().show()
        except Exception:
            import traceback
            traceback.print_exc()

    _on_ui(_do)
