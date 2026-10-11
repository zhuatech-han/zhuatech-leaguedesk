#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""核对重启或隔离恢复后的业务快照，差异即失败。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import argparse
import json
from pathlib import Path
from urllib.parse import urlparse
from http_client import Client, ROOT, env

p = argparse.ArgumentParser()
p.add_argument('--base', default='http://127.0.0.1:8144')
p.add_argument('--env-file', default=str(ROOT / '.env'))
p.add_argument('--state', default=str(ROOT / 'output/mysql-quality-state.json'))
a = p.parse_args()
if urlparse(a.base).hostname not in ['127.0.0.1', 'localhost', '::1']:
    p.error('Persistence checks are limited to isolated localhost instances')
values = env(a.env_file)
c = Client(a.base)
c.login(values.get('ADMIN_USERNAME', 'admin'), values['ADMIN_PASSWORD'])
state = json.loads(Path(a.state).read_text())
for key, expected in state['events'].items():
    assert c.request('/events/' + key) == expected, 'Event snapshot mismatch: ' + key
assert c.request('/reports') == state['reports'], 'Season standings mismatch'
print(f'Exact match for {len(state["events"])} event snapshots and season standings; no data loss.')
