#!/usr/bin/env python3
"""Verify hour/minute independence by swiping the real editor on a disposable emulator.

Install app-debug.apk first. Replaces only the emulator debug app's alarm/settings fixtures.
Checks both directions, quick switches between wheels, and the saved time after reopening.
"""

import argparse
import json
from pathlib import Path
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = "com.vocalarm.app.debug"
LABEL = "Time wheel regression"
ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--report", default="fixed")
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+", args.serial):
        parser.error("Use a disposable emulator, not a physical device with personal alarms.")
    report = ROOT / "app/build/time-wheel-verification" / args.report
    report.mkdir(parents=True, exist_ok=True)
    observations = []

    def adb(*command, data=None, check=True):
        result = subprocess.run(
            [args.adb, "-s", args.serial, *command], input=data, capture_output=True, timeout=45,
        )
        if check and result.returncode:
            raise RuntimeError(result.stderr.decode(errors="replace") + result.stdout.decode(errors="replace"))
        return result.stdout

    def shell(*command, **kwargs):
        return adb("shell", *command, **kwargs).decode(errors="replace").strip()

    def write_private(name, value):
        shell("run-as", PACKAGE, "sh", "-c", shlex.quote("cat > " + shlex.quote("files/" + name)),
              data=json.dumps(value).encode())

    def dump(name):
        shell("uiautomator", "dump", "/sdcard/time-wheel.xml")
        data = adb("exec-out", "cat", "/sdcard/time-wheel.xml")
        (report / f"{name}.xml").write_bytes(data)
        return ET.fromstring(data)

    def bounds(node):
        return tuple(map(int, re.findall(r"\d+", node.attrib["bounds"])))

    def tap_text(tree, text):
        node = next(n for n in tree.iter("node") if n.get("text") == text)
        left, top, right, bottom = bounds(node)
        shell("input", "tap", str((left + right) // 2), str((top + bottom) // 2))

    def selected_time(tree):
        text = next(n.get("text") for n in tree.iter("node")
                    if re.fullmatch(r"\d{2}:\d{2}", n.get("text", "")))
        return tuple(map(int, text.split(":")))

    def wheels(tree):
        width = max(bounds(n)[2] for n in tree.iter("node"))
        candidates = [n for n in tree.iter("node") if n.get("scrollable") == "true"
                      and bounds(n)[2] - bounds(n)[0] < width * 0.55
                      and sum(bool(re.fullmatch(r"\d{2}", x.get("text", ""))) for x in n.iter("node")) >= 3]
        assert len(candidates) == 2, "Expected separate hour and minute wheels"
        return sorted(candidates, key=lambda n: bounds(n)[0])

    def verify_center(tree, expected):
        for wheel, value in zip(wheels(tree), expected):
            _, top, _, bottom = bounds(wheel)
            center = (top + bottom) / 2
            rows = [n for n in wheel.iter("node") if re.fullmatch(r"\d{2}", n.get("text", ""))]
            centered = min(rows, key=lambda n: abs((bounds(n)[1] + bounds(n)[3]) / 2 - center))
            assert int(centered.get("text")) == value, f"Wheel center differs from selected time {expected}"

    def swipe(wheel, direction):
        left, top, right, bottom = bounds(wheel)
        x, center = (left + right) // 2, (top + bottom) // 2
        row = (bottom - top) / 5
        shell("input", "swipe", str(x), str(round(center + direction * row)),
              str(x), str(round(center - direction * row)), "600")

    try:
        shell("am", "force-stop", PACKAGE)
        shell("input", "keyevent", "KEYCODE_WAKEUP")
        shell("wm", "dismiss-keyguard")
        shell("pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS")
        shell("appops", "set", PACKAGE, "USE_FULL_SCREEN_INTENT", "allow")
        shell("run-as", PACKAGE, "mkdir", "-p", "files")
        write_private("alarms.json", {"schemaVersion": 3, "alarms": [{
            "id": "wheel-verification", "hour": 7, "minute": 15, "label": LABEL,
            "enabled": False, "text": "Verification", "repeatDays": [],
        }]})
        write_private("settings.json", {"schemaVersion": 3, "guardianEnabled": False})
        write_private("alarm-schedules.json", {"schemaVersion": 1, "occurrences": []})
        shell("am", "start", "-W", "-n", PACKAGE + "/com.vocalarm.app.ui.MainActivity")
        tap_text(dump("list"), LABEL)
        tree = dump("initial")
        assert selected_time(tree) == (7, 15)
        verify_center(tree, (7, 15))

        for index, (column, direction) in enumerate([(0, 1), (1, 1), (0, -1), (1, -1), (0, 1), (1, 1)]):
            before = selected_time(tree)
            swipe(wheels(tree)[column], direction)
            tree = dump(f"swipe-{index}")
            after = selected_time(tree)
            observations.append({"column": column, "before": before, "after": after})
            print(f"{'hour' if column == 0 else 'minute'}: {before} -> {after}", flush=True)
            assert after[column] != before[column], "The swiped wheel did not change"
            assert after[1 - column] == before[1 - column], "Swiping one wheel reverted the other"
            verify_center(tree, after)

        before = selected_time(tree)
        hour, minute = wheels(tree)
        swipe(hour, 1)
        swipe(minute, 1)
        tree = dump("quick-switch")
        expected = selected_time(tree)
        assert expected[0] > before[0] and expected[1] > before[1], "Quick switches lost a time update"
        verify_center(tree, expected)
        tap_text(tree, "保存")
        listing = dump("saved")
        tap_text(listing, LABEL)
        reopened = dump("reopened")
        assert selected_time(reopened) == expected, "Reopening lost the selected time"
        verify_center(reopened, expected)
        saved = json.loads(shell("run-as", PACKAGE, "cat", "files/alarms.json"))["alarms"][0]
        assert (saved["hour"], saved["minute"]) == expected, "Persisted time differs from the editor"
        print(f"PASS; saved and reopened {expected}; evidence: {report}", flush=True)
    finally:
        (report / "observations.json").write_text(json.dumps(observations, indent=2))
        (report / "screen.png").write_bytes(adb("exec-out", "screencap", "-p", check=False))
        shell("am", "force-stop", PACKAGE, check=False)


if __name__ == "__main__":
    main()
