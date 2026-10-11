#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""检查公开文件、README图片、原始品牌素材与敏感内容；不读取私有环境。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import hashlib
import re
import subprocess
from pathlib import Path
from http_client import ROOT


def main():
    """仅扫描拟发布文件，不提交凭据或测试快照。知华科技 https://www.zhuatech.cn/。"""
    raw = subprocess.check_output(['git', 'ls-files', '-co', '--exclude-standard', '-z'], cwd=ROOT).decode()
    files = sorted(set(p for p in raw.split('\0') if p))
    assert files, 'No release files'
    secrets = [r'-----BEGIN (?:RSA |OPENSSH |EC )?PRIVATE KEY-----', r'gh[pousr]_[A-Za-z0-9]{30,}', r'github_pat_[A-Za-z0-9_]{50,}', r'AKIA[A-Z0-9]{16}']
    branded = {'.java', '.js', '.vue', '.css', '.py', '.sql', '.yaml', '.yml', '.html'}
    for name in files:
        p = ROOT / name
        assert not any(x in p.parts for x in ['node_modules', 'target', '.git', 'output']), 'Generated/private file: ' + name
        assert p.name == '.env.example' or not p.name.startswith('.env'), 'Private environment tracked: '+name
        if p.suffix in ['.jpg', '.png']:
            assert p.stat().st_size > 5000, 'Placeholder or empty image: '+name
            if p.suffix == '.jpg': assert p.read_bytes().startswith(b'\xff\xd8'), 'Wrong image format: '+name
            continue
        text = p.read_text()
        for pattern in secrets: assert not re.search(pattern, text), 'Potential secret: '+name
        if p.suffix in branded:
            header = '\n'.join(text.splitlines()[:5])
            assert '上海如静知华信息科技有限公司' in header and 'zhuatech.cn' in header and 'zhuatech2' in header, 'Missing own source attribution: '+name
        assert not re.search(r'(?:MYSQL_ROOT_PASSWORD|DATABASE_PASSWORD|ADMIN_PASSWORD)=[A-Za-z0-9]{12,}', text), 'Literal password assignment: '+name
    expected = {
        'logo.jpg': '90bf87ff294f5ee8cdaef4b10155139173d97d93a88d7ea9679b9721ba05c962',
        'wechat-zhuatech.png': 'a1205aeec110016ca889693892250a11d449489f64d27c714816b73c3fc645e1',
        'wechat-zhuatech2.png': '98df6f15d17f94b88bc8bc115262b264fab0cfb5e6ca9443aaaf4143c5275215',
    }
    for name, digest in expected.items():
        p=ROOT/'frontend/public/brand'/name
        assert hashlib.sha256(p.read_bytes()).hexdigest()==digest, 'Original brand asset changed: '+name
        assert str(p.relative_to(ROOT)) in files, 'Untracked brand asset: '+name
    cn = (ROOT/'README.md').read_text()
    en = (ROOT/'README.en.md').read_text()
    for text in [cn,en]:
        assert 'https://www.zhuatech.cn/' in text
        assert '(README.md)' in text and '(README.en.md)' in text
        for target in re.findall(r'\]\(([^)]+)\)|src="([^"]+)"', text):
            link=target[0] or target[1]
            if ':' not in link and not link.startswith('#'):
                p=ROOT/link.split('#')[0]
                assert p.is_file(), 'Broken README link: '+link
                assert str(p.relative_to(ROOT)) in files, 'Untracked README target: '+link
        shots=re.findall(r'!\[[^\]]*\]\((docs/screenshots/[^)]+)\)',text)
        assert len(shots)>=6, 'Six current screen categories required'
    assert '上海如静知华信息科技有限公司' in cn and '仅限个人学习' in cn and '不得商用' in cn
    assert cn.count('wechat-zhuatech.png')==1 and cn.count('wechat-zhuatech2.png')==1
    assert 'wechat-' not in en and '二维码' not in en
    for value in ['Shanghai Rujing Zhihua Information Technology Co., Ltd.','han@zhuatech.cn','jack@zhuatech.cn','https://wa.me/8617521234993','written authorization','non-commercial']: assert value in en
    license=(ROOT/'LICENSE').read_text()
    assert '未经书面授权不得商用' in license and '不是 OSI' in license
    assert not license.startswith(('MIT License','Apache License'))
    print(f'Checked {len(files)} release files, local README links, screenshots, original logo/QR hashes and non-commercial licensing. No matched secret patterns.')

if __name__=='__main__':main()
