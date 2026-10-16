# smscli

Read the text messages of a connected Android phone from your computer's
command line, over ADB. Messages you have read are archived locally so you
can always come back to them.

```console
$ smscli              # capture new SMS from the phone, list unread ones
     1  2026-10-14 09:12  +491701234567     Your code is 4821…
     2  2026-10-15 18:40  DHL               Your parcel is on its…
-- 2 message(s); read one with: smscli read <id>

$ smscli read 1       # show the full message, archives it automatically
$ smscli archive      # list everything you already read
$ smscli read 1       # archived messages stay readable forever
$ smscli sync         # just capture, don't list
```

## How it works

- The phone is queried over ADB via `adb shell content query --uri
  content://sms/inbox` — no app needs to be installed on the phone, only
  USB debugging (Developer options) enabled.
- New messages are captured into a local JSON store at `~/.smscli/store.json`
  and get small sequential ids. Capturing is incremental: only messages newer
  than the last one seen are pulled from the phone.
- `read <id>` prints the full message and flips it to archived. Archived
  messages remain in the store and stay readable; `smscli archive` lists them.

## Setup

Requirements: Python 3.8+, [adb](https://developer.android.com/tools/releases/platform-tools)
on PATH, and a phone connected with USB debugging authorized
(check with `adb devices`).

```console
$ pip install -e .
```

If you have several phones attached, the first one listed by `adb devices` is used.

## Commands

| Command | Effect |
| --- | --- |
| `smscli` / `smscli list` | Capture new SMS and list unread ones (`--no-sync` to skip the phone) |
| `smscli read <id>` | Print a message in full; unread messages are archived automatically |
| `smscli archive` | List archived messages |
| `smscli sync` | Capture new messages without listing |

Set `SMSCLI_HOME` to keep the store somewhere other than `~/.smscli`
(useful for tests).

## Privacy

Messages are stored only on your computer, in `~/.smscli/store.json` — nothing
is ever uploaded anywhere, and this repository contains no message data.

## Troubleshooting

- **"no phone connected"** — run `adb devices`; accept the USB-debugging prompt
  on the phone if it is listed as `unauthorized`.
- **"could not read SMS"** — some Android builds deny the adb shell access to
  the SMS provider. This was verified to work on the target Motorola device;
  if your device refuses, there is currently no fallback.
