"""Real client helpers on the Chaquopy Java bridge (v2).

Mirrors the exteraGram client_utils surface used by most plugins:
controllers, queues, sending messages and requests.
"""

from java.chaquopy import dynamic_proxy


def _selected_account():
    from org.telegram.messenger import UserConfig
    return UserConfig.selectedAccount


def selected_account():
    return _selected_account()


def get_messages_controller(account=None):
    from org.telegram.messenger import MessagesController
    return MessagesController.getInstance(_selected_account() if account is None else account)


def get_user_config(account=None):
    from org.telegram.messenger import UserConfig
    return UserConfig.getInstance(_selected_account() if account is None else account)


def get_connections_manager(account=None):
    from org.telegram.tgnet import ConnectionsManager
    return ConnectionsManager.getInstance(_selected_account() if account is None else account)


def get_send_helper(account=None):
    from org.telegram.messenger import SendMessagesHelper
    return SendMessagesHelper.getInstance(_selected_account() if account is None else account)


def _as_runnable(func):
    from java.lang import Runnable

    class _R(dynamic_proxy(Runnable)):
        def run(self):
            func()

    return _R()


def run_on_ui_thread(func):
    from org.telegram.messenger import AndroidUtilities
    AndroidUtilities.runOnUIThread(_as_runnable(func))


def run_on_queue(func):
    from org.telegram.messenger import Utilities
    Utilities.stageQueue.postRunnable(_as_runnable(func))


def run_on_stage_queue(func):
    run_on_queue(func)


def send_text(peer, text, notify=True):
    """ExteraGram-style send: account defaults to the selected one."""
    send_message(selected_account(), peer, text, notify=notify)


def send_message(account, peer, text, notify=True):
    """Send a text message. Always hops to the UI thread."""
    def _do():
        from org.telegram.messenger import SendMessagesHelper
        helper = SendMessagesHelper.getInstance(account)
        params = SendMessagesHelper.SendMessageParams.of(
            str(text), peer, None, None, None, False,
            None, None, None, bool(notify), 0, 0, None, False)
        helper.sendMessage(params)

    run_on_ui_thread(_do)


def send_request(account, request, on_response=None):
    """Send a raw TL request. on_response(response, error) runs on a
    background thread (Telegram network thread)."""
    from org.telegram.tgnet import ConnectionsManager, RequestDelegate

    manager = ConnectionsManager.getInstance(account)
    if on_response is None:
        manager.sendRequest(request, None)
        return

    class _Cb(dynamic_proxy(RequestDelegate)):
        def run(self, response, error):
            try:
                on_response(response, error)
            except Exception:
                import traceback
                traceback.print_exc()

    manager.sendRequest(request, _Cb())
