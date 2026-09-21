"""ExteraGram-compatible Bulletin helper (toast-backed).

Extera plugins call BulletinHelper.show_success/show_info/show_error,
usually from background hook threads — delivery hops to the UI thread.
"""


def _show(text):
    try:
        from ui.dialogs import toast
        toast(text)
    except Exception:
        import traceback
        traceback.print_exc()


class BulletinHelper:
    @staticmethod
    def show_success(text):
        _show(text)

    @staticmethod
    def show_info(text):
        _show(text)

    @staticmethod
    def show_error(text):
        _show(text)
