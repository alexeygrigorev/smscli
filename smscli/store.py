"""Local JSON store for captured messages.

Everything lives in ~/.smscli/store.json (override with SMSCLI_HOME).
The store never leaves this machine.
"""

import json
import os

DEFAULT_HOME = os.path.join(os.path.expanduser("~"), ".smscli")


def home():
    return os.environ.get("SMSCLI_HOME") or DEFAULT_HOME


def store_path():
    return os.path.join(home(), "store.json")


def load():
    try:
        with open(store_path(), "r", encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError:
        return {"next_id": 1, "messages": []}


def save(db):
    os.makedirs(home(), exist_ok=True)
    tmp = store_path() + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(db, f, ensure_ascii=False, indent=2)
    os.replace(tmp, store_path())
