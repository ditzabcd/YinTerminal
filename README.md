# YinTerm

Lightweight Android terminal emulator and offline Python 3.13 environment refactored from [DTerminal](https://github.com/dedeadend/DTerminal).

Built for daily shell (`sh`/`su`) and scripting workflows on `arm64-v8a` devices with minimal background overhead and fast cold-start times.

## What Changed from Upstream

- **No Hilt / Room / KSP:** Replaced Dagger Hilt with manual dependency injection (`AppContainer`) and migrated SQLite/Room storage to an in-memory circular buffer (1,000-line cap) plus atomic JSON/SharedPreferences persistence.
- **Lazy Python Worker:** Python 3.13 runtime (`:python_worker` process) initializes on demand only when executing Python blocks instead of blocking app startup.
- **Stripped UI Dependencies:** Removed `material-icons-extended` and adaptive navigation suite wrappers in favor of local vector paths and standard Material 3 navigation components.
- **Shrink & ABI Target:** Locked to `arm64-v8a` with R8 code shrinking and resource optimization enabled on release builds.

## Features

- **Shell Execution:** Run standard `sh` or root `su` commands (toggleable via top-right menu) with multi-line batch execution.
- **Python 3.13 Engine:** Embedded Chaquopy runtime pre-bundled with native `cryptography` C-extensions.
- **Runtime Package Manager:** Install, list, and remove pure-Python wheels (`none-any.whl`) directly from PyPI.
- **Script & History Manager:** Save reusable command blocks and re-run or swipe-delete past executions.
- **Built-in Utilities:** Offline helpers for hashing, encoding, JSON formatting, regex matching, device info, and terminal appearance.

## Quick Usage

### Python Mode
Start the first line of an input block with `py` to switch the block into the Python 3.13 interpreter:

```python
py
import cryptography
print("Python 3.13 ready")
