#!/usr/bin/env python3
"""
Unit tests for community CDN resolver rules and URL rewrites.
Can be executed directly via `python3 scripts/test_cdn_resolver.py`.
"""
import os
import sys
import json
import re

def create_resolver(config_path=None, region=None):
    config = None
    if config_path and os.path.isfile(config_path):
        with open(config_path, "r", encoding="utf-8-sig") as f:
            config = json.load(f)

    if not config:
        return [
            {
                "action": "replace_host",
                "match_hosts": ["upos-sz-mirrorcosov.bilivideo.com"],
                "target_host": "upos-sz-mirrorali.bilivideo.com",
            }
        ]

    if not region:
        region = config.get("default_region", "overseas-apac")

    if region == "disabled":
        return []

    return config.get("regions", {}).get(region, {}).get("rules", [])

def rewrite_url(url, rules):
    if not isinstance(url, str) or not rules:
        return url
    res = url
    for rule in rules:
        action = rule.get("action")
        if action == "replace_host":
            match_hosts = rule.get("match_hosts", [])
            target_host = rule.get("target_host")
            if target_host:
                for h in match_hosts:
                    if h in res:
                        res = res.replace(h, target_host)
        elif action == "replace_regex":
            pattern = rule.get("pattern")
            replacement = rule.get("replacement")
            if pattern and replacement:
                safe_repl = re.sub(r"\$(\d+)", r"\\g<\1>", replacement)
                res = re.sub(pattern, safe_repl, res)
    return res

def run_tests():
    base_dir = os.path.dirname(__file__)
    rules_path = os.path.join(base_dir, "cdn-rules.json")
    if not os.path.exists(rules_path):
        rules_path = "/data/cdn-rules.json"

    print(f"Testing CDN Rules from: {rules_path}")

    # Test 1: overseas-apac (default)
    rules_apac = create_resolver(rules_path, "overseas-apac")
    print(f"[1] overseas-apac rules count: {len(rules_apac)}")
    assert len(rules_apac) >= 2, "Expected at least 2 rules for overseas-apac"

    u1 = "https://upos-sz-mirrorcosov.bilivideo.com/upgcxcode/11/22/33.m4a"
    r1 = rewrite_url(u1, rules_apac)
    print(f"  cosov -> ali: {r1}")
    assert "upos-sz-mirrorali.bilivideo.com" in r1, "Failed to rewrite cosov to ali"

    u2 = "http://xy123x.mcdn.bilivideo.cn:8082/upgcxcode/44/55/66.m4a"
    r2 = rewrite_url(u2, rules_apac)
    print(f"  mcdn -> ali: {r2}")
    assert r2 == "https://upos-sz-mirrorali.bilivideo.com/upgcxcode/44/55/66.m4a", f"Failed mcdn rewrite: {r2}"

    # Test 2: YouTube URL must remain untouched
    yt_url = "https://rr3---sn-4g5ednle.googlevideo.com/videoplayback?expire=123"
    r_yt = rewrite_url(yt_url, rules_apac)
    assert r_yt == yt_url, "YouTube URL must never be touched!"
    print("  YouTube URL untouched: OK")

    # Test 3: cn-mainland
    rules_cn = create_resolver(rules_path, "cn-mainland")
    r3 = rewrite_url(u2, rules_cn)
    print(f"[2] cn-mainland mcdn -> cos: {r3}")
    assert r3 == "https://upos-sz-mirrorcos.bilivideo.com/upgcxcode/44/55/66.m4a", f"Failed cn-mainland rewrite: {r3}"

    r_cosov_cn = rewrite_url(u1, rules_cn)
    assert "upos-sz-mirrorcosov.bilivideo.com" in r_cosov_cn, "cosov should not be modified in cn-mainland"
    print("  cosov in cn-mainland untouched: OK")

    # Test 4: disabled
    rules_dis = create_resolver(rules_path, "disabled")
    print(f"[3] disabled mode rules count: {len(rules_dis)}")
    assert len(rules_dis) == 0
    assert rewrite_url(u1, rules_dis) == u1
    assert rewrite_url(u2, rules_dis) == u2
    print("  disabled mode: all pass-through OK")

    # Test 5: Fallback on missing file
    rules_fallback = create_resolver("/nonexistent/file.json")
    r_fb = rewrite_url(u1, rules_fallback)
    assert "upos-sz-mirrorali.bilivideo.com" in r_fb
    print("[4] Fallback on missing file: OK")

    print("\n>>> ALL 5 TEST SUITES PASSED CLEANLY! <<<")

if __name__ == "__main__":
    run_tests()
