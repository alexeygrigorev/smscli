"""Fetch SMS from a connected Android phone over ADB.

Uses the standard telephony content provider, which the adb shell user
can read on most stock Android devices (verified on the target phone).
"""

import re
import subprocess

ROW_SPLIT_RE = re.compile(r"(?m)^Row: \d+ ")
ROW_RE = re.compile(r"_id=(\d*), address=(.*?), date=(\d*), body=(.*)")


def _run(args):
    proc = subprocess.run(["adb"] + args, capture_output=True)
    out = proc.stdout.decode("utf-8", "replace")
    err = proc.stderr.decode("utf-8", "replace")
    return proc.returncode, out, err


def require_device():
    code, out, err = _run(["devices"])
    if code != 0:
        raise SystemExit(
            "error: adb not found or not runnable.\n"
            "Install the Android platform-tools and make sure 'adb' is on PATH."
        )
    devices = [
        line.split("\t")[0]
        for line in out.splitlines()[1:]
        if line.strip() and line.split("\t")[-1].strip() == "device"
    ]
    if not devices:
        raise SystemExit(
            "error: no phone connected (or USB debugging not authorized).\n"
            "Plug in the phone and check the output of 'adb devices'."
        )
    return devices[0]


def fetch_inbox(since_ms=None):
    """Return new inbox messages as dicts: phone_id, address, date_ms, body.

    since_ms limits the query to messages newer than that epoch-millisecond
    timestamp; if that restricted query fails for any reason it falls back
    to fetching the whole inbox.
    """
    require_device()
    base = [
        "shell",
        "content",
        "query",
        "--uri",
        "content://sms/inbox",
        "--projection",
        "_id:address:date:body",
    ]
    args = list(base)
    if since_ms is not None:
        # The value is pre-quoted so '>' survives the shell on the phone.
        args += ["--where", '"date>%d"' % since_ms]
    code, out, err = _run(args)
    if code != 0 or "Permission Denial" in out + err or "Error" in err:
        if since_ms is not None:
            code, out, err = _run(base)
        if code != 0 or "Permission Denial" in out + err or "Error" in err:
            raise SystemExit(
                "error: could not read SMS from the phone:\n"
                + (err or out).strip()
            )
    return parse_rows(out)


def parse_rows(text):
    """Parse `content query` output into message dicts.

    Bodies can contain newlines, so rows are split on the "Row: N" markers
    and every continuation line belongs to the previous row's body.
    """
    messages = []
    for chunk in ROW_SPLIT_RE.split(text)[1:]:
        lines = chunk.rstrip("\r\n").split("\n")
        m = ROW_RE.match(lines[0])
        if not m:
            continue
        phone_id, address, date_ms, body = m.groups()
        rest = "\n".join(line.rstrip("\r") for line in lines[1:])
        if rest:
            body = body + "\n" + rest
        if body == "NULL":
            body = ""
        messages.append(
            {
                "phone_id": phone_id,
                "address": address.strip(),
                "date_ms": int(date_ms) if date_ms else 0,
                "body": body,
            }
        )
    return messages
