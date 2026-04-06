# KagamiLink AirMirror (Windows)

KagamiLink AirMirror is a Windows GUI wrapper for `UxPlay` so you can receive iPhone Screen Mirroring (AirPlay) on PC.

## Features

- Start / stop UxPlay from GUI
- Connection status panel (`WAITING` / `LIVE` / `RECOVER` / `STOPPED`)
- Reconnect support button (`Reconnect Now`)
- Auto recovery after disconnect (`Auto recover after disconnect`)
- Launches UxPlay without popping up a command prompt window
- Saved settings (`settings.json`)
- Stable default args for Windows

## Quick start

1. Install `uxplay-windows` (already tested in this project).
2. Run:

```powershell
python .\mirror_app.py
```

3. Confirm `uxplay.exe` path.
4. Click `Start Receiver`.
5. On iPhone: Control Center -> Screen Mirroring -> select `MirrorPC` (or your configured name).

## Reconnect behavior

- If iPhone disconnects, app can auto-restart UxPlay and prepare for the next connection.
- You can also force a reset with `Reconnect Now`.

## Default stable args

By default this app uses:

```text
-vd avdec_h264 -vs glimagesink -as wasapisink -vsync no -fps 30 -nc -nohold
```

These are chosen to reduce freeze/stall issues on some Windows setups.

## Build distributable EXE

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\build_release.ps1
```

Output:

- `dist\KagamiLinkAirMirror\KagamiLinkAirMirror.exe`
- `release\KagamiLinkAirMirror-win64.zip`

## Notes

- `libgstcurl.dll` warning is usually non-fatal for mirroring.
- iPhone and PC must be on the same network.
