#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""仅在本机独立空库验证办赛全流程；测试数据明确标记，私有快照不提交。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import argparse
import concurrent.futures
from datetime import datetime, timedelta, timezone
import json
import os
from pathlib import Path
from urllib.parse import urlparse
import uuid
from http_client import Client, ROOT, env


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--base', default='http://127.0.0.1:8144')
    p.add_argument('--env-file', default=str(ROOT / '.env'))
    p.add_argument('--output', default=str(ROOT / 'output/mysql-quality.json'))
    a = p.parse_args()
    if urlparse(a.base).hostname not in ['127.0.0.1', 'localhost', '::1']:
        p.error('Quality fixtures are only allowed on a dedicated localhost instance')
    values = env(a.env_file)
    admin, referee, participant, public = [Client(a.base) for _ in range(4)]
    admin.login(values.get('ADMIN_USERNAME', 'admin'), values['ADMIN_PASSWORD'])
    assert admin.request('/events')['total'] == 0, 'Refuse to modify non-empty instance'
    now = datetime.now(timezone.utc)
    iso = lambda v: v.isoformat().replace('+00:00', 'Z')
    accounts = []
    for i in range(6):
        accounts.append(admin.request('/admin/users', {'username': 'league-referee' if i == 0 else f'league-p{i}', 'displayName': 'TEST 裁判' if i == 0 else f'TEST 参赛负责人{i}', 'roleId': 3 if i == 0 else 4, 'departmentId': 1, 'enabled': True, 'password': values['ADMIN_PASSWORD']}))
    official = accounts[0]['id']
    referee.login('league-referee', values['ADMIN_PASSWORD'])
    participant.login('league-p1', values['ADMIN_PASSWORD'])
    assert not participant.request('/options')['accounts']
    assert not referee.request('/options')['accounts']
    participant.request('/admin/users', status=403)
    public.request('/events', status=401)
    admin.request('/events', {}, status=403, csrf=False)
    season = admin.request('/masters/seasons', {'name': 'TEST 秋季俱乐部联赛', 'departmentId': 1, 'enabled': True})
    court = admin.request('/masters/courts', {'name': 'TEST 1号球场', 'departmentId': 1, 'enabled': True})
    secondCourt = admin.request('/masters/courts', {'name': 'TEST 2号球场', 'departmentId': 1, 'enabled': True})

    class Event:
        """测试事件使用真实 API 和最新版本，绝不直接写业务数据库。知华科技 https://www.zhuatech.cn/。"""
        def __init__(self, name, format='GROUP_KO', groups=1, advance=2):
            b = {'seasonId': season['id'], 'name': name, 'category': 'RACKET', 'format': format, 'groups': groups, 'advance': advance, 'bestOf': 3, 'capacity': 8, 'startsAt': iso(now-timedelta(days=2)), 'endsAt': iso(now+timedelta(days=2)), 'description': 'TEST 测试赛事，非真实客户或运营案例。'}
            self.id = admin.request('/events', b)['id']
            self.path = f'/events/{self.id}'
        def detail(self): return admin.request(self.path)
        def cmd(self, action, extra=None, actor=None, status=200):
            b = dict(extra or {}, version=self.detail()['tournament']['version'], requestId=str(uuid.uuid4()))
            return (actor or admin).request(self.path+'/commands/'+action, b, status=status)
        def game(self, id): return next(g for g in self.detail()['games'] if g['id']==id)
        def ready(self, n):
            self.cmd('OPEN')
            for i, acct in enumerate(accounts[1:n+1]):
                actor = participant if i == 0 else admin
                d = actor.request(self.path+'/entries', {'accountId': acct['id'], 'name': f'TEST {chr(65+i)}队', 'note': 'TEST 私有报名备注', 'publicConsent': True})
                e = next(e for e in d['entries'] if e.get('accountId')==acct['id'])
                self.cmd('REVIEW', {'entryId':e['id'], 'entryVersion':e['version'], 'approved':True, 'seed':i+1})
                e = next(x for x in self.detail()['entries'] if x['id']==e['id'])
                self.cmd('CHECKIN', {'entryId':e['id'], 'entryVersion':e['version'], 'checkedIn':True})
            self.cmd('DRAW')
            self.cmd('PUBLISH', {'published':True})
        def schedule(self, g, slot, courtId=None, status=200):
            time = now-timedelta(hours=36)+timedelta(minutes=10*slot)
            return self.cmd('SCHEDULE', {'gameId':g['id'], 'gameVersion':g['version'], 'courtId':courtId or court['id'], 'refereeId':official, 'startsAt':iso(time), 'endsAt':iso(time+timedelta(minutes=5))}, status=status)
        def play(self, g, slot, walkover=False):
            self.schedule(g, slot)
            g=self.game(g['id']);self.cmd('START', {'gameId':g['id'], 'gameVersion':g['version']}, referee)
            g=self.game(g['id'])
            b={'gameId':g['id'],'gameVersion':g['version'],'resultType':'WALKOVER' if walkover else 'PLAYED','homeScore':0 if walkover else 2,'awayScore':0}
            if walkover: b['winnerId']=g['homeId']
            self.cmd('RESULT',b,referee)

    completed = Event('TEST 秋季单打积分赛')
    completed.ready(5)
    assert len(completed.detail()['games'])==10
    participant.request(completed.path+'/entries', {'name':'TEST duplicate','publicConsent':True},status=409)
    contestantView=participant.request(completed.path)
    assert sum('note' in e for e in contestantView['entries'])==1
    gd=completed.detail()['games']
    completed.schedule(gd[0],0)
    assert completed.schedule(gd[1],0,status=409)['code']=='SCHEDULE_CONFLICT'
    assert completed.schedule(gd[1],0,secondCourt['id'],409)['code']=='SCHEDULE_CONFLICT'
    completed.cmd('ADVANCE',status=409)
    g=completed.game(gd[0]['id']);completed.cmd('START',{'gameId':g['id'],'gameVersion':g['version']},referee)
    g=completed.game(g['id']);completed.cmd('RESULT',{'gameId':g['id'],'gameVersion':g['version'],'resultType':'PLAYED','homeScore':2,'awayScore':2},referee,409)
    completed.cmd('CANCEL',{'reason':'TEST cannot cancel live'},status=409)
    completed.cmd('RESULT',{'gameId':g['id'],'gameVersion':g['version'],'resultType':'PLAYED','homeScore':2,'awayScore':0},referee)
    for slot,g in enumerate(gd[1:],1): completed.play(g,slot)
    completed.cmd('ADVANCE')
    final=next(g for g in completed.detail()['games'] if g['stage']=='KNOCKOUT')
    completed.play(final,10)
    completed.cmd('FINISH')
    report=admin.request('/reports');assert sorted(x['points'] for x in report['standings'])==[1,1,1,3,5]
    exported=admin.request(completed.path+'/report.csv',binary=True);assert b'GROUP' in exported and b'TEST' in exported
    participant.request(completed.path+'/report.csv',status=403)
    pub=public.request('/public'+completed.path);text=json.dumps(pub)
    for key in ['accountId','passwordHash','ownerId','refereeId','TEST 私有报名备注']: assert key not in text
    assert len(pub['entries'])==5

    knockout=Event('TEST 俱乐部淘汰赛','KNOCKOUT');knockout.ready(5)
    assert len(knockout.detail()['games'])==7
    slot=15
    for round in [1,2,3]:
        for g in knockout.detail()['games']:
            if g['roundNo']==round and g['status']!='BYE':knockout.play(g,slot,walkover=round==1);slot+=1
    knockout.cmd('FINISH')

    group=Event('TEST 双组公开赛','GROUP_KO',groups=2,advance=1);group.ready(4)
    assert len(group.detail()['games'])==2
    for slot,g in enumerate(group.detail()['games'],25):group.play(g,slot)
    group.cmd('ADVANCE')
    final=next(g for g in group.detail()['games'] if g['stage']=='KNOCKOUT');group.play(final,27)
    group.cmd('FINISH')

    live=Event('TEST 周末球馆循环赛');live.ready(4)
    first=live.detail()['games'][0];live.play(first,30)
    second=live.detail()['games'][1];live.schedule(second,31)
    second=live.game(second['id']);live.cmd('START',{'gameId':second['id'],'gameVersion':second['version']},referee)
    third=live.detail()['games'][2];live.schedule(third,32)
    view=public.request('/public'+live.path);assert len(view['games'])==6
    live.cmd('PUBLISH',{'published':False});public.request('/public'+live.path,status=404)
    live.cmd('PUBLISH',{'published':True})
    b={'version':live.detail()['tournament']['version'],'published':True,'requestId':str(uuid.uuid4())}
    results=[]
    def replay(_):
        c=Client(a.base);c.login(values.get('ADMIN_USERNAME','admin'),values['ADMIN_PASSWORD']);return c.request(live.path+'/commands/PUBLISH',b)
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:results=list(pool.map(replay,range(2)))
    assert results[0]['tournament']['version']==results[1]['tournament']['version']
    openEvent=Event('TEST 下月公开报名赛');openEvent.cmd('OPEN');openEvent.cmd('PUBLISH',{'published':True})
    participant.request(openEvent.path+'/entries',{'name':'TEST A队','publicConsent':True,'note':'TEST only'})
    withdraw=next(e for e in openEvent.detail()['entries'] if e['accountId']==accounts[1]['id'])
    participant.request(openEvent.path+f'/entries/{withdraw["id"]}/withdraw',{'version':withdraw['version']})
    participant.request(openEvent.path+'/entries',{'name':'TEST A队','publicConsent':True})
    account=accounts[-1];account.update(enabled=False,password='');admin.request('/admin/users/'+str(account['id']),account,method='PUT')
    snapshots={str(e.id):e.detail() for e in [completed,knockout,group,live,openEvent]}
    out=Path(a.output);out.parent.mkdir(parents=True,exist_ok=True)
    private=ROOT/'output/mysql-quality-state.json';fd=os.open(private,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
    with os.fdopen(fd,'w') as stream:json.dump({'events':snapshots,'reports':admin.request('/reports'),'liveId':live.id,'openId':openEvent.id},stream,ensure_ascii=False,indent=2)
    checks=sum((c.checks for c in [admin,referee,participant,public]),[])
    out.write_text(json.dumps({'checks':checks,'requests':len(checks),'result':'passed','coverage':['group5','group2x2','knockout5byes','walkover','public-privacy','unpublish','referee-session','participant-permissions','csrf','schedule-conflict','concurrent-replay','withdraw-reenter','season-points']},indent=2))
    print(f'Passed {len(checks)} HTTP assertions against real database; private snapshot saved for restart/restore validation.')

if __name__=='__main__':main()
