#!/usr/bin/env python3
"""Exercise the real AlarmManager -> FGS -> window path on a disposable API 34+ emulator.

Install app-debug.apk first. This replaces ONLY com.vocalarm.app.debug's alarm/settings fixtures
on the named emulator. No instrumentation or broadcast is used to fire the alarm. Reports are
written to app/build/alarm-verification/. A foreground guardian is deliberately disabled.
"""

import argparse
import json
from pathlib import Path
import re
import shlex
import subprocess
import time

PACKAGE = "com.vocalarm.app.debug"
ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--scenario", choices=["locked", "unlocked"], required=True)
    parser.add_argument("--kind", choices=["Regular", "Snooze", "Test"], default="Test")
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+", args.serial):
        parser.error("Use a disposable emulator, never a physical device with personal alarm data.")
    report = ROOT / "app/build/alarm-verification" / f"{args.scenario}-{args.kind}"
    report.mkdir(parents=True, exist_ok=True)

    def adb(*command, data=None, check=True):
        result = subprocess.run(
            [args.adb, "-s", args.serial, *command], input=data, capture_output=True, timeout=45,
        )
        if check and result.returncode:
            raise RuntimeError(result.stderr.decode(errors="replace") + result.stdout.decode(errors="replace"))
        return result.stdout

    def shell(*command, **kwargs):
        return adb("shell", *command, **kwargs).decode(errors="replace").strip()

    def write_private(name, data):
        shell("run-as", PACKAGE, "sh", "-c", shlex.quote("cat > " + shlex.quote("files/" + name)), data=data)

    def read_log():
        raw = shell("run-as", PACKAGE, "cat", "files/trigger.log", check=False)
        return {k: int(v) for k, v in re.findall(r"^(\w+)=(\d+)$", raw, re.MULTILINE)}

    try:
        assert int(shell("getprop", "ro.build.version.sdk")) >= 34, "Use an API 34+ emulator"
        shell("am", "force-stop", PACKAGE)
        shell("dumpsys", "deviceidle", "unforce")
        shell("input", "keyevent", "KEYCODE_WAKEUP")
        shell("wm", "dismiss-keyguard")
        shell("pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS")
        shell("appops", "set", PACKAGE, "USE_FULL_SCREEN_INTENT", "allow")
        shell("appops", "set", PACKAGE, "SYSTEM_ALERT_WINDOW", "allow" if args.scenario == "unlocked" else "deny")
        shell("run-as", PACKAGE, "mkdir", "-p", "files/recordings")
        # A real AAC recording avoids a dependency on the emulator's TTS engine or network.
        tone = report / "verification.m4a"
        subprocess.run(
            ["ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i", "sine=frequency=440:duration=2",
             "-c:a", "aac", str(tone)], check=True, timeout=20,
        )
        write_private("recordings/verification.m4a", tone.read_bytes())
        deadline = (int(shell("date", "+%s")) + 60) * 1000
        alarm = {
            "id": "verification", "hour": 7, "minute": 0, "label": "Alarm delivery verification",
            "enabled": args.kind == "Regular", "recording": "verification.m4a", "repeatDays": [],
            "loopMode": "continuous", "volume": 0.1, "fadeIn": False, "snoozeMinutes": [1, 5],
        }
        write_private("alarms.json", json.dumps({"schemaVersion": 3, "alarms": [alarm]}).encode())
        write_private("settings.json", json.dumps({"schemaVersion": 3, "guardianEnabled": False}).encode())
        write_private("alarm-schedules.json", json.dumps({"schemaVersion": 1, "occurrences": [{
            "alarmId": "verification", "kind": args.kind, "triggerAtMillis": deadline,
            "token": f"verification-{args.scenario}-{deadline}", "consumed": False,
        }]}).encode())
        write_private("trigger.log", b"")
        shell("logcat", "-c")
        launch = shell("am", "start", "-W", "-n", PACKAGE + "/com.vocalarm.app.ui.MainActivity")
        assert "Status: ok" in launch, launch
        old_pid = shell("pidof", PACKAGE)
        alarms = shell("dumpsys", "alarm")
        (report / "registered-alarms.txt").write_text(alarms)
        assert re.search(PACKAGE + r".*(?:startForegroundService|foregroundService)", alarms, re.I), \
            "AlarmManager must own a foreground-service PendingIntent"

        # Remove the recent task with a real swipe, then ensure even the cached process is gone.
        shell("input", "keyevent", "KEYCODE_APP_SWITCH")
        time.sleep(2)
        shell("input", "swipe", "540", "1200", "540", "150", "350")
        shell("input", "keyevent", "KEYCODE_HOME")
        shell("am", "kill", PACKAGE)
        pid = shell("pidof", PACKAGE, check=False)
        if pid:
            shell("run-as", PACKAGE, "kill", "-9", *pid.split())
        time.sleep(1)
        assert not shell("pidof", PACKAGE, check=False), "The app must be dead before delivery"
        if args.scenario == "locked":
            shell("input", "keyevent", "KEYCODE_SLEEP")
            shell("dumpsys", "deviceidle", "force-idle")
        else:
            shell("am", "start", "-W", "-a", "android.settings.SETTINGS")
        print(f"{args.scenario}/{args.kind}: task swiped, process {old_pid} gone, waiting for system deadline", flush=True)

        timeout = time.monotonic() + 75
        snapshot = {}
        while time.monotonic() < timeout:
            snapshot = read_log()
            if snapshot.get("screenShownAt", 0) >= deadline:
                break
            time.sleep(2)
        window = shell("dumpsys", "window", "windows")
        media = shell("dumpsys", "media.audio_flinger")
        notifications = shell("dumpsys", "notification", "--noredact")
        new_pid = shell("pidof", PACKAGE, check=False)
        (report / "window.txt").write_text(window)
        (report / "audio.txt").write_text(media)
        (report / "notifications.txt").write_text(notifications)
        (report / "screen.png").write_bytes(adb("exec-out", "screencap", "-p"))
        result = {
            "scenario": args.scenario, "kind": args.kind, "scheduledAt": deadline,
            "oldPid": old_pid, "newPid": new_pid, "triggerLog": snapshot,
            "deliveryDelayMs": snapshot.get("deliveredAt", 0) - deadline,
        }
        (report / "result.json").write_text(json.dumps(result, indent=2))
        assert new_pid and new_pid != old_pid, result
        assert snapshot.get("ringingAt", 0) >= deadline, result
        assert 0 <= result["deliveryDelayMs"] < 15_000, result
        assert snapshot.get("screenShownAt", 0) >= deadline, "No focused full-screen window: " + str(result)
        assert "AlarmRingActivity" in window, "Missing ringing window"
        print(json.dumps(result, indent=2), flush=True)
        print(f"PASS; evidence: {report}", flush=True)
    finally:
        (report / "logcat.txt").write_bytes(adb("logcat", "-d", check=False))
        shell("dumpsys", "deviceidle", "unforce", check=False)
        shell("am", "force-stop", PACKAGE, check=False)


if __name__ == "__main__":
    main()
