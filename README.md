# YinTerm

Lightweight Android terminal emulator with a persistent root/user shell and embedded Python 3.13 runtime.

## Features

- **Persistent Shell Session:** Keeps a single `su` or `/system/bin/sh` process alive across commands so `cd`, environment variables, and working directories stay intact.
- **Root-First Execution:** Automatically requests `su` on launch and falls back cleanly to standard user shell (`$`) on non-rooted devices.
- **Real-Time Output & ANSI Support:** Streams `stdout` and `stderr` separately with 16/256/RGB ANSI color parsing and system emoji rendering.
- **Python 3.13 Runtime:** Runs local Python scripts offline via Chaquopy without requiring external Termux packages.
- **Script & History Manager:** Save reusable shell snippets, swipe to delete entries, and re-run commands from local history.
- **Built-in Utilities:** Includes quick tools for hashing (`md5`, `sha1`, `sha256`), Base64/Hex/URL encoding, JSON formatting, regex testing, and system info.

## Requirements

- Android 8.0 (API 26) or higher (`arm64-v8a` / `x86_64`)
- Magisk / KernelSU / APatch (optional, required for `#` root shell)

## Download

Grab the latest signed APK from the [Releases](https://github.com/ditzabcd/YinTerminal/releases) page.

## Build from Source

Clone the repository and build using the Gradle wrapper:

```bash
git clone [https://github.com/ditzabcd/YinTerminal.git](https://github.com/ditzabcd/YinTerminal.git)
cd YinTerminal