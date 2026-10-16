"""smscli — read SMS from a connected Android phone over ADB.

Commands:
  smscli            capture new SMS from the phone and list unread ones
  smscli sync       capture new SMS without listing
  smscli read ID    show a message in full; unread messages get archived
  smscli archive    list archived messages
  smscli list       same as the default command
"""

import argparse
import sys
from datetime import datetime

from . import __version__, adb, store


def fmt_date(ms):
    if not ms:
        return "?"
    return datetime.fromtimestamp(ms / 1000).strftime("%Y-%m-%d %H:%M")


def snippet(body, width=48):
    text = " ".join(body.split())
    if len(text) > width:
        return text[: width - 1] + "…"
    return text


def merge(db):
    """Capture new inbox messages from the phone into the local store."""
    seen = {m["phone_id"] for m in db["messages"]}
    since = max((m["date_ms"] for m in db["messages"]), default=None)
    rows = adb.fetch_inbox(since)
    added = 0
    for row in sorted(rows, key=lambda r: (r["date_ms"], r["phone_id"])):
        if row["phone_id"] in seen:
            continue
        db["messages"].append(
            {
                "id": db["next_id"],
                "phone_id": row["phone_id"],
                "address": row["address"],
                "date_ms": row["date_ms"],
                "body": row["body"],
                "archived": False,
            }
        )
        db["next_id"] += 1
        added += 1
    if added:
        store.save(db)
    return added


def find(db, msg_id):
    for m in db["messages"]:
        if str(m["id"]) == str(msg_id):
            return m
    return None


def print_list(messages, empty_hint):
    if not messages:
        print(empty_hint)
        return
    for m in sorted(messages, key=lambda m: (m["date_ms"], m["id"])):
        print(
            "%5d  %s  %-16s  %s"
            % (m["id"], fmt_date(m["date_ms"]), m["address"], snippet(m["body"]))
        )
    print("-- %d message(s); read one with: smscli read <id>" % len(messages))


def cmd_list(args):
    db = store.load()
    if not args.no_sync:
        merge(db)
    unread = [m for m in db["messages"] if not m["archived"]]
    print_list(unread, "no unread messages")


def cmd_sync(args):
    db = store.load()
    added = merge(db)
    unread = sum(1 for m in db["messages"] if not m["archived"])
    print("captured %d new message(s), %d unread" % (added, unread))


def cmd_read(args):
    db = store.load()
    m = find(db, args.id)
    if m is None:
        merge(db)
        m = find(db, args.id)
    if m is None:
        print("no message with id %s (run 'smscli list' to see ids)" % args.id)
        raise SystemExit(1)
    print("From: %s" % m["address"])
    print("Date: %s" % fmt_date(m["date_ms"]))
    print()
    print(m["body"])
    if not m["archived"]:
        m["archived"] = True
        store.save(db)
        print("\n(archived — read it again anytime with 'smscli archive' / 'smscli read %d')" % m["id"])


def cmd_archive(args):
    db = store.load()
    archived = [m for m in db["messages"] if m["archived"]]
    print_list(archived, "the archive is empty")


def main(argv=None):
    # Windows consoles may not be UTF-8; never crash on unprintable bodies.
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(
        prog="smscli",
        description="Read SMS from a connected Android phone over ADB. "
        "Read messages are archived locally in ~/.smscli.",
    )
    parser.add_argument("--version", action="version", version="smscli " + __version__)
    sub = parser.add_subparsers(dest="cmd")

    p_list = sub.add_parser("list", help="capture new SMS and list unread ones")
    p_list.add_argument("--no-sync", action="store_true", help="skip querying the phone")
    p_list.set_defaults(func=cmd_list)

    sub.add_parser("sync", help="capture new SMS without listing").set_defaults(func=cmd_sync)

    p_read = sub.add_parser("read", help="show a message in full and archive it")
    p_read.add_argument("id", help="message id from 'smscli list' or 'smscli archive'")
    p_read.set_defaults(func=cmd_read)

    sub.add_parser("archive", help="list archived messages").set_defaults(func=cmd_archive)

    args = parser.parse_args(argv)
    if args.cmd is None:
        args = parser.parse_args(["list"])
    args.func(args)
