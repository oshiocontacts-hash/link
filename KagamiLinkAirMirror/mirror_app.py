#!/usr/bin/env python3
import json
import os
import queue
import shlex
import shutil
import subprocess
import threading
from pathlib import Path
import tkinter as tk
from tkinter import filedialog, messagebox, scrolledtext

if os.name == "nt":
    import ctypes
    from ctypes import wintypes

APP_NAME = "KagamiLink AirMirror"
SETTINGS_FILE = Path(__file__).with_name("settings.json")
DEFAULT_EXTRA_ARGS = "-vd avdec_h264 -vs glimagesink -as wasapisink -vsync no -fps 30 -nc -nohold"
DISCONNECT_MARKERS = (
    "TEARDOWN request,  96=0, 110=0",
    "raop_rtp_mirror exiting TCP thread",
)
CONNECTED_MARKERS = (
    "connection request from",
    "raop_rtp_mirror starting mirroring",
)


class UxPlayMirrorApp:
    def __init__(self, root: tk.Tk) -> None:
        self.root = root
        self.root.title(APP_NAME)
        self.root.geometry("920x620")
        self.root.minsize(780, 520)

        self.process: subprocess.Popen | None = None
        self.log_queue: queue.Queue[str] = queue.Queue()
        self.reader_thread: threading.Thread | None = None
        self.restart_pending = False
        self.manual_stop_requested = False
        self.current_status = "stopped"
        self.waiting_dots = 0

        self.uxplay_path_var = tk.StringVar(value=self._detect_uxplay_path())
        self.server_name_var = tk.StringVar(value="MirrorPC")
        self.use_nh_var = tk.BooleanVar(value=True)
        self.pin_var = tk.BooleanVar(value=False)
        self.hevc_var = tk.BooleanVar(value=False)
        self.vsync_var = tk.StringVar(value="default")
        self.auto_recover_var = tk.BooleanVar(value=True)
        self.extra_args_var = tk.StringVar(value=DEFAULT_EXTRA_ARGS)

        self._build_ui()
        self._load_settings()
        self._poll_logs()
        self._animate_waiting_status()
        self._set_status_stopped()
        self.root.protocol("WM_DELETE_WINDOW", self.on_close)

    def _build_ui(self) -> None:
        frame = tk.Frame(self.root, padx=12, pady=12)
        frame.pack(fill=tk.BOTH, expand=True)

        row = 0
        status_frame = tk.Frame(frame, bd=1, relief=tk.SOLID)
        status_frame.grid(row=row, column=0, columnspan=3, sticky="ew", pady=(0, 10))
        self.status_badge = tk.Label(status_frame, text="STOPPED", fg="white", bg="#616161", font=("Segoe UI", 10, "bold"), padx=8, pady=6)
        self.status_badge.pack(side=tk.LEFT, padx=8, pady=8)
        self.status_text = tk.Label(status_frame, text="Receiver is not running.", fg="#222", font=("Segoe UI", 10), padx=8, pady=6)
        self.status_text.pack(side=tk.LEFT, padx=(0, 4), pady=8)

        row += 1
        tk.Label(frame, text="UxPlay executable").grid(row=row, column=0, sticky="w", pady=(0, 6))
        tk.Entry(frame, textvariable=self.uxplay_path_var, width=86).grid(row=row, column=1, sticky="ew", pady=(0, 6))
        tk.Button(frame, text="Browse", command=self.pick_uxplay).grid(row=row, column=2, padx=(8, 0), pady=(0, 6))

        row += 1
        tk.Label(frame, text="AirPlay name").grid(row=row, column=0, sticky="w", pady=4)
        tk.Entry(frame, textvariable=self.server_name_var, width=34).grid(row=row, column=1, sticky="w", pady=4)

        row += 1
        options = tk.Frame(frame)
        options.grid(row=row, column=1, sticky="w", pady=4)
        tk.Checkbutton(options, text="Hide @hostname (recommended)", variable=self.use_nh_var).pack(anchor="w")
        tk.Checkbutton(options, text="Require PIN", variable=self.pin_var).pack(anchor="w")
        tk.Checkbutton(options, text="Enable HEVC / h265", variable=self.hevc_var).pack(anchor="w")
        tk.Checkbutton(options, text="Auto recover after disconnect", variable=self.auto_recover_var).pack(anchor="w")

        row += 1
        tk.Label(frame, text="A/V sync").grid(row=row, column=0, sticky="w", pady=4)
        sync_frame = tk.Frame(frame)
        sync_frame.grid(row=row, column=1, sticky="w", pady=4)
        tk.Radiobutton(sync_frame, text="Default", variable=self.vsync_var, value="default").pack(side=tk.LEFT)
        tk.Radiobutton(sync_frame, text="Force -vsync", variable=self.vsync_var, value="on").pack(side=tk.LEFT, padx=(8, 0))
        tk.Radiobutton(sync_frame, text="Disable -vsync", variable=self.vsync_var, value="off").pack(side=tk.LEFT, padx=(8, 0))

        row += 1
        tk.Label(frame, text="Extra args").grid(row=row, column=0, sticky="w", pady=4)
        tk.Entry(frame, textvariable=self.extra_args_var, width=86).grid(row=row, column=1, sticky="ew", pady=4)

        row += 1
        controls = tk.Frame(frame)
        controls.grid(row=row, column=1, sticky="w", pady=(8, 10))
        self.start_button = tk.Button(controls, text="Start Receiver", command=self.start_receiver)
        self.start_button.pack(side=tk.LEFT)
        self.stop_button = tk.Button(controls, text="Stop Receiver", command=self.stop_receiver, state=tk.DISABLED)
        self.stop_button.pack(side=tk.LEFT, padx=(8, 0))
        self.reconnect_button = tk.Button(controls, text="Reconnect Now", command=self.reconnect_now, state=tk.DISABLED)
        self.reconnect_button.pack(side=tk.LEFT, padx=(8, 0))

        row += 1
        help_text = "How to connect: iPhone and PC on same network -> Control Center -> Screen Mirroring -> choose this PC name."
        tk.Label(frame, text=help_text, fg="#333").grid(row=row, column=0, columnspan=3, sticky="w", pady=(0, 8))

        row += 1
        self.log = scrolledtext.ScrolledText(frame, height=20, wrap=tk.WORD, state=tk.DISABLED)
        self.log.grid(row=row, column=0, columnspan=3, sticky="nsew")

        frame.columnconfigure(1, weight=1)
        frame.rowconfigure(row, weight=1)

    def _detect_uxplay_path(self) -> str:
        for candidate in ("uxplay.exe", "uxplay"):
            hit = shutil.which(candidate)
            if hit:
                return hit
        path = Path(r"C:\Program Files (x86)\uxplay-windows\_internal\bin\uxplay.exe")
        return str(path) if path.exists() else ""

    def _settings_dict(self) -> dict:
        return {
            "uxplay_path": self.uxplay_path_var.get().strip(),
            "server_name": self.server_name_var.get().strip(),
            "use_nh": self.use_nh_var.get(),
            "pin": self.pin_var.get(),
            "hevc": self.hevc_var.get(),
            "vsync": self.vsync_var.get(),
            "auto_recover": self.auto_recover_var.get(),
            "extra_args": self.extra_args_var.get(),
        }

    def _load_settings(self) -> None:
        if not SETTINGS_FILE.exists():
            return
        try:
            data = json.loads(SETTINGS_FILE.read_text(encoding="utf-8"))
        except Exception:
            return
        self.uxplay_path_var.set(data.get("uxplay_path", self.uxplay_path_var.get()))
        self.server_name_var.set(data.get("server_name", self.server_name_var.get()))
        self.use_nh_var.set(bool(data.get("use_nh", True)))
        self.pin_var.set(bool(data.get("pin", False)))
        self.hevc_var.set(bool(data.get("hevc", False)))
        self.vsync_var.set(data.get("vsync", "default"))
        self.auto_recover_var.set(bool(data.get("auto_recover", True)))
        self.extra_args_var.set(data.get("extra_args", DEFAULT_EXTRA_ARGS))

    def _save_settings(self) -> None:
        SETTINGS_FILE.write_text(json.dumps(self._settings_dict(), ensure_ascii=False, indent=2), encoding="utf-8")

    def pick_uxplay(self) -> None:
        file_path = filedialog.askopenfilename(title="Select uxplay executable", filetypes=[("Executable", "*.exe"), ("All files", "*.*")])
        if file_path:
            self.uxplay_path_var.set(file_path)

    def _build_command(self) -> list[str]:
        uxplay_path = self.uxplay_path_var.get().strip()
        if not uxplay_path:
            raise ValueError("uxplay executable path is required.")
        uxplay_resolved = Path(uxplay_path)
        resolved_cmd = str(uxplay_resolved) if uxplay_resolved.exists() else shutil.which(uxplay_path)
        if not resolved_cmd:
            raise ValueError(f"uxplay executable was not found: {uxplay_path}")

        cmd = [resolved_cmd]
        if self.server_name_var.get().strip():
            cmd.extend(["-n", self.server_name_var.get().strip()])
        if self.use_nh_var.get():
            cmd.append("-nh")
        if self.pin_var.get():
            cmd.append("-pin")
        if self.hevc_var.get():
            cmd.append("-h265")
        if self.vsync_var.get().strip() == "on":
            cmd.append("-vsync")
        elif self.vsync_var.get().strip() == "off":
            cmd.extend(["-vsync", "no"])
        extra = self.extra_args_var.get().strip()
        if extra:
            cmd.extend(shlex.split(extra, posix=False))
        return cmd

    def start_receiver(self, is_auto_recover: bool = False) -> None:
        if self.process and self.process.poll() is None:
            return
        self.manual_stop_requested = False
        self.restart_pending = False
        try:
            cmd = self._build_command()
        except Exception as exc:
            messagebox.showerror("Invalid setup", str(exc))
            return

        self._save_settings()
        self._append_log(f"$ {' '.join(cmd)}")

        creationflags = subprocess.CREATE_NO_WINDOW if hasattr(subprocess, "CREATE_NO_WINDOW") else 0
        startupinfo = None
        if hasattr(subprocess, "CREATE_NO_WINDOW"):
            startupinfo = subprocess.STARTUPINFO()
            startupinfo.dwFlags |= subprocess.STARTF_USESHOWWINDOW
            startupinfo.wShowWindow = 0

        try:
            self.process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace", bufsize=1, creationflags=creationflags, startupinfo=startupinfo)
        except Exception as exc:
            messagebox.showerror("Launch failed", str(exc))
            return

        self.start_button.config(state=tk.DISABLED)
        self.stop_button.config(state=tk.NORMAL)
        self.reconnect_button.config(state=tk.NORMAL)
        self._set_status_waiting()
        self.root.after(1800, self._bring_mirror_window_to_front)
        self._append_log("Receiver auto-recovered. You can reconnect from iPhone." if is_auto_recover else "Receiver started. Connect from iPhone Screen Mirroring.")
        self.reader_thread = threading.Thread(target=self._read_output_loop, daemon=True)
        self.reader_thread.start()

    def _read_output_loop(self) -> None:
        if not self.process or not self.process.stdout:
            return
        try:
            for line in self.process.stdout:
                self.log_queue.put(line.rstrip("\n"))
        finally:
            self.log_queue.put("[uxplay exited]")

    def stop_receiver(self, manual: bool = True) -> None:
        if manual:
            self.manual_stop_requested = True
            self.restart_pending = False
        if self.process and self.process.poll() is None:
            self._append_log("Stopping receiver...")
            self.process.terminate()
            try:
                self.process.wait(timeout=4)
            except subprocess.TimeoutExpired:
                self._append_log("Force killing receiver...")
                self.process.kill()
        self.process = None
        self.start_button.config(state=tk.NORMAL)
        self.stop_button.config(state=tk.DISABLED)
        self.reconnect_button.config(state=tk.DISABLED)
        self._set_status_stopped() if manual else self._set_status_recovering()

    def reconnect_now(self) -> None:
        self._append_log("Manual reconnect requested...")
        self._set_status_recovering()
        self.stop_receiver(manual=False)
        self.root.after(500, lambda: self.start_receiver(is_auto_recover=True))

    def _append_log(self, text: str) -> None:
        self.log.config(state=tk.NORMAL)
        self.log.insert(tk.END, text + "\n")
        self.log.see(tk.END)
        self.log.config(state=tk.DISABLED)

    def _bring_mirror_window_to_front(self) -> None:
        if os.name != "nt" or not self.process or self.process.poll() is not None:
            return
        user32 = ctypes.WinDLL("user32", use_last_error=True)
        pid = self.process.pid

        EnumWindowsProc = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
        windows: list[tuple[int, bool, str]] = []

        @EnumWindowsProc
        def cb(hwnd: int, _: int) -> bool:
            owner = wintypes.DWORD()
            user32.GetWindowThreadProcessId(hwnd, ctypes.byref(owner))
            if owner.value != pid:
                return True
            title_buf = ctypes.create_unicode_buffer(512)
            user32.GetWindowTextW(hwnd, title_buf, len(title_buf))
            title = title_buf.value or ""
            if "IME" in title:
                return True
            windows.append((hwnd, bool(user32.IsWindowVisible(hwnd)), title))
            return True

        user32.EnumWindows(cb, 0)
        if not windows:
            return
        windows.sort(key=lambda w: (w[1], "renderer" in w[2].lower(), len(w[2])), reverse=True)
        hwnd = windows[0][0]
        user32.ShowWindow(hwnd, 5)
        user32.MoveWindow(hwnd, 120, 80, 1280, 720, True)
        user32.SetForegroundWindow(hwnd)

    def _set_status(self, badge: str, color: str, text: str, key: str) -> None:
        self.current_status = key
        self.status_badge.config(text=badge, bg=color)
        self.status_text.config(text=text)

    def _set_status_stopped(self) -> None:
        self._set_status("STOPPED", "#616161", "Receiver is not running.", "stopped")

    def _set_status_waiting(self) -> None:
        self.waiting_dots = 0
        self._set_status("WAITING", "#1E88E5", "Waiting for iPhone connection", "waiting")

    def _set_status_connected(self, info: str | None = None) -> None:
        self._set_status("LIVE", "#2E7D32", f"Mirroring connected: {info}" if info else "Mirroring is connected.", "connected")

    def _set_status_recovering(self) -> None:
        self._set_status("RECOVER", "#EF6C00", "Restarting receiver...", "recovering")

    def _animate_waiting_status(self) -> None:
        if self.current_status == "waiting":
            self.waiting_dots = (self.waiting_dots + 1) % 4
            self.status_text.config(text=f"Waiting for iPhone connection{'.' * self.waiting_dots}")
        self.root.after(450, self._animate_waiting_status)

    def _schedule_auto_recover(self, reason: str) -> None:
        if self.manual_stop_requested or self.restart_pending or not self.auto_recover_var.get():
            return
        self.restart_pending = True
        self._set_status_recovering()
        self._append_log(f"Auto recover: {reason}. Restarting receiver in 1 second...")
        self.root.after(1000, self._auto_recover_now)

    def _auto_recover_now(self) -> None:
        if self.manual_stop_requested:
            self.restart_pending = False
            return
        self.stop_receiver(manual=False)
        self.restart_pending = False
        self.root.after(350, lambda: self.start_receiver(is_auto_recover=True))

    def _poll_logs(self) -> None:
        while True:
            try:
                line = self.log_queue.get_nowait()
            except queue.Empty:
                break
            self._append_log(line)
            if any(marker in line for marker in CONNECTED_MARKERS):
                if "connection request from" in line:
                    self._set_status_connected(line.split("connection request from", 1)[1].strip())
                else:
                    self._set_status_connected()
                self.root.after(250, self._bring_mirror_window_to_front)
            if any(marker in line for marker in DISCONNECT_MARKERS):
                self._set_status_waiting()
                self._schedule_auto_recover("disconnect detected")
            if line == "[uxplay exited]":
                self.process = None
                self.start_button.config(state=tk.NORMAL)
                self.stop_button.config(state=tk.DISABLED)
                self.reconnect_button.config(state=tk.DISABLED)
                if not self.manual_stop_requested:
                    self._set_status_recovering()
                    self._schedule_auto_recover("uxplay exited")
                else:
                    self._set_status_stopped()
        self.root.after(120, self._poll_logs)

    def on_close(self) -> None:
        self.stop_receiver(manual=True)
        self.root.destroy()


def main() -> None:
    root = tk.Tk()
    app = UxPlayMirrorApp(root)
    app._append_log(f"{APP_NAME} ready.")
    app._append_log("Set uxplay.exe path and click Start Receiver.")
    root.mainloop()


if __name__ == "__main__":
    main()
