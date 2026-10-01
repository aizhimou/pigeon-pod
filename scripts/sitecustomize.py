import sys
import os
import json
import re

# 1. Fallback for silent videos without audio stream (e.g., SpaceX clips)
for i in range(len(sys.argv) - 1):
    if sys.argv[i] in ('-f', '--format') and '+bestaudio' in sys.argv[i + 1] and not sys.argv[i + 1].endswith('/best'):
        sys.argv[i + 1] = sys.argv[i + 1] + '/bestvideo[height<=1080]/best'

orig_args = getattr(sys, 'orig_argv', sys.argv)
is_ytdlp = any('yt_dlp' in arg or 'yt-dlp' in arg for arg in orig_args) or os.environ.get('PIGEON_CDN_FORCE_HOOK') == '1'

if '-c' not in orig_args and is_ytdlp:
    try:
        def load_cdn_rules():
            rules_paths = [
                os.environ.get('PIGEON_CDN_RULES_PATH', ''),
                '/data/cdn-rules.json',
                '/app/scripts/cdn-rules.json',
                os.path.join(os.path.dirname(__file__), 'cdn-rules.json')
            ]
            config = None
            for p in rules_paths:
                if p and os.path.isfile(p):
                    try:
                        with open(p, 'r', encoding='utf-8-sig') as f:
                            config = json.load(f)
                            break
                    except Exception:
                        pass

            region = os.environ.get('PIGEON_CDN_REGION')
            if not config:
                # Built-in fallback if no file is present
                return [
                    {
                        "action": "replace_host",
                        "match_hosts": ["upos-sz-mirrorcosov.bilivideo.com"],
                        "target_host": "upos-sz-mirrorali.bilivideo.com"
                    }
                ]

            if not region:
                region = config.get('default_region', 'overseas-apac')

            if region == 'disabled':
                return []

            region_data = config.get('regions', {}).get(region, {})
            return region_data.get('rules', [])

        active_rules = load_cdn_rules()

        def rewrite_url(url, rules):
            if not isinstance(url, str) or not rules:
                return url
            res = url
            for rule in rules:
                action = rule.get('action')
                if action == 'replace_host':
                    match_hosts = rule.get('match_hosts', [])
                    target_host = rule.get('target_host')
                    if target_host:
                        for h in match_hosts:
                            if h in res:
                                res = res.replace(h, target_host)
                elif action == 'replace_regex':
                    pattern = rule.get('pattern')
                    replacement = rule.get('replacement')
                    if pattern and replacement:
                        # Auto-convert $1, $2 to Python \g<1>, \g<2> syntax for community flexibility
                        safe_repl = re.sub(r'\$(\d+)', r'\\g<\1>', replacement)
                        res = re.sub(pattern, safe_repl, res)
            return res

        def process_urls_recursively(node, rules):
            if isinstance(node, dict):
                for k, v in list(node.items()):
                    if k == 'url' and isinstance(v, str):
                        node[k] = rewrite_url(v, rules)
                    elif isinstance(v, (dict, list)):
                        process_urls_recursively(v, rules)
            elif isinstance(node, list):
                for item in node:
                    process_urls_recursively(item, rules)

        if active_rules:
            import yt_dlp
            from yt_dlp import YoutubeDL

            orig_process_info = YoutubeDL.process_info

            def patched_process_info(self, info_dict):
                try:
                    process_urls_recursively(info_dict, active_rules)
                except Exception:
                    pass
                return orig_process_info(self, info_dict)

            YoutubeDL.process_info = patched_process_info

    except Exception:
        pass
