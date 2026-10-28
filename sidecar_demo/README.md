# ComicRack Sidecar Demo

Minimal FastAPI-based sidecar to demonstrate discovery (multicast) and basic pairing/notify features.

Features
- Background multicast listener for `ComicRack:<key>[:Sync]` announcements on 224.34.123.90:7615
- GET /api/wireless/devices — list discovered devices
- POST /api/wireless/pair — pair with a device (in-memory state)
- POST /api/wireless/notify-available — connect to device and send a `ServerAvailable` opcode and control port

Quickstart

Docker (recommended for local testing)

1) Build the image from repo root:
   docker build -f sidecar_demo/Dockerfile -t comicrack-sidecar-demo:latest sidecar_demo

2) Run it:
   docker run --rm -p 8000:8000 comicrack-sidecar-demo:latest

Local venv (alternative)

1. Create a virtual env and install requirements:
   python -m venv .venv
   .venv\Scripts\activate
   pip install -r sidecar_demo/requirements.txt
2. Run the sidecar:
   uvicorn sidecar_demo.app:app --host 0.0.0.0 --port 8000

Testing discovery
- Use the provided test harness (`.copilot/test-harness/server.py`) or an Android device to send the multicast announcement `ComicRack:<key>:Sync` to 224.34.123.90:7615.

> Tip: If testing from an Android device or emulator, ensure **ADB (platform-tools)** is installed and available. On Windows the typical path is `C:\Users\<username>\AppData\Local\Android\Sdk\platform-tools\adb.exe`; add it to your PATH or set `ANDROID_SDK_ROOT`.

Security note
- This demo uses an in-memory device store and no authentication — do not expose on public networks.
