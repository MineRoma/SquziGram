"""Settings row dataclasses for plugin settings pages (v1: data only,
rendered by the Java runtime in v2 settings UI)."""


class Header:
    def __init__(self, text):
        self.text = text


class Text:
    def __init__(self, text, subtext=None, icon=None):
        self.text = text
        self.subtext = subtext
        self.icon = icon


class Input:
    def __init__(self, key, text, default="", subtext=None, icon=None):
        self.key = key
        self.text = text
        self.default = default
        self.subtext = subtext
        self.icon = icon


class Switch:
    def __init__(self, key, text, default=False, subtext=None, icon=None):
        self.key = key
        self.text = text
        self.default = default
        self.subtext = subtext
        self.icon = icon
