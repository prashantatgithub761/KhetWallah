#!/usr/bin/env python3
"""Back up local KhetWallah MySQL and photo files to a private folder outside Git.
Run python3 docs/backup-local.py --output /absolute/path/outside/project.
Uses the existing private IntelliJ/local environment settings. Never connects to a remote DB.
"""
from pathlib import Path
import argparse, datetime, os, re, runpy, subprocess, tarfile, tempfile, urllib.parse

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',required=True);args=parser.parse_args()
 project=Path(__file__).resolve().parent.parent
 dest=Path(args.output).expanduser().resolve()
 if dest==project or dest.is_relative_to(project):raise SystemExit('Choose a backup folder outside the project/Git repository.')
 env=runpy.run_path(str(project/'docs/run-email-test.py'))['settings']()
 url=urllib.parse.urlsplit(env.get('DATABASE_URL','jdbc:mysql://localhost:3306/KhetWallah').removeprefix('jdbc:'))
 if url.hostname not in {'localhost','127.0.0.1','::1'}:raise SystemExit('This helper only backs up a local database. Use the hosting backup/runbook for hosted data.')
 database=urllib.parse.unquote(url.path.lstrip('/'))
 if not re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]*',database):raise SystemExit('Unexpected database name.')
 dest.mkdir(mode=0o700,parents=True,exist_ok=True);dest.chmod(0o700)
 out=dest/datetime.datetime.now().strftime('khetwallah-%Y%m%d-%H%M%S');out.mkdir(mode=0o700)
 def escaped(value):return '"'+str(value).replace('\\','\\\\').replace('"','\\"').replace('\n','\\n').replace('\r','\\r')+'"'
 with tempfile.NamedTemporaryFile('w',prefix='khet-backup-',suffix='.cnf',delete=False) as config:
  os.chmod(config.name,0o600);config.write('[client]\nprotocol=TCP\nhost='+escaped(url.hostname)+'\nport='+str(url.port or 3306)+'\nuser='+escaped(env['DB_USERNAME'])+'\npassword='+escaped(env['DB_PASSWORD'])+'\n')
 try:
  dump=out/'database.sql'
  with dump.open('xb') as stream:
   dump.chmod(0o600);result=subprocess.run(['mysqldump','--defaults-extra-file='+config.name,'--single-transaction','--no-tablespaces','--set-gtid-purged=OFF',database],stdout=stream,stderr=subprocess.PIPE)
  if result.returncode:dump.unlink(missing_ok=True);raise SystemExit('Database backup failed; no schema change was attempted.')
 finally:Path(config.name).unlink(missing_ok=True)
 uploads=Path(env.get('UPLOAD_DIR',str(Path.home()/'KhetWallahUploads'))).expanduser().resolve()
 if uploads.is_dir():
  archive=out/'uploads.tar.gz'
  with archive.open('xb') as raw:
   archive.chmod(0o600)
   with tarfile.open(fileobj=raw,mode='w:gz',dereference=False) as tar:tar.add(uploads,arcname='uploads')
 (out/'README.txt').write_text('Private local KhetWallah backup. Contains account records and photo data. Keep outside Git.\nRestore database.sql into a separate database first, extract uploads into a separate folder, and run the application against those copies to check the restore.\nStop application writes during backup when you need an exact database/photo pair.\n')
 print('Local database and available photo files backed up privately to: '+str(out))
if __name__=='__main__':main()
