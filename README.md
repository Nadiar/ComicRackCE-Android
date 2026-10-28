# ComicRack-Android — Subagent Flow Audit

## Subagent-flow cleanup audit (2025-12-26)
- **Action:** Backed up and removed `registered_functions` entries that referenced `.copilot` paths.
- **Backup commit:** local-only commit `b2ff31778f82845386c514d9adb08a561807766b` (no remote push performed).
- **Backup file:** `.copilot/subagent-flow/backups/registered_functions_copilot_backup_1766796400.json`
- **Restore helper:** `.copilot/subagent-flow/scripts/restore_registered_functions_from_backup.py`
- **Policy:** This backup is committed locally only; do **not** initialize or push a remote for the subagent flow until it is validated and reviewed.

If you need to restore the backed-up rows into the local DB, run:

```
python .copilot/subagent-flow/scripts/restore_registered_functions_from_backup.py .copilot/subagent-flow/backups/registered_functions_copilot_backup_1766796400.json
```

(Commit made locally by automation; this entry records the audit for traceability.)
---

## Development & Smoke-test Prerequisites

**Primary project priority (MVP):** Make the app boot, enumerate local comics, show basic metadata, and open a comic file so images can be viewed; wireless sync is LOW priority until this is working.

**Required locally for development and smoke testing:**

- Java JDK (as required by Gradle)
- Android SDK (command-line tools)
- **Android platform-tools (adb)** installed and available on PATH
  - Typical Windows user install: `C:\Users\<username>\AppData\Local\Android\Sdk\platform-tools\adb.exe`
  - Verify with:

```powershell
adb version
# or
Get-Command adb -ErrorAction SilentlyContinue
```

- If `adb` is not on PATH, add it temporarily for the current shell:

```powershell
$env:Path = "C:\Users\$env:USERNAME\AppData\Local\Android\Sdk\platform-tools;" + $env:Path
adb version
```

or add it permanently via System → Environment Variables → Path.

---

## Wireless Sync with Emulator

The Android Emulator runs behind a virtual network, making standard WiFi synchronization difficult. To enable sync with the Desktop app, use the provided helper script.

**Prerequisites:**
- Android Emulator running with ComicRack installed and open.
- `adb` available on your PATH.

**Instructions:**
1.  Run the sync helper script:
    ```bash
    python tools/emulator_sync.py
    ```
2.  Open ComicRack Desktop on your PC.
3.  Wait for the new device (e.g., `ComicRackAndroid:<UUID>`) to appear in the "Devices" list.
4.  Sync as usual.

**How it works:**
- Forwards TCP port 7614 (data transfer) via ADB.
- Spoofs UDP Multicast beacons (port 7615) so the desktop app can discover the emulator on `127.0.0.1`.
