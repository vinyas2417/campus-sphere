"""Import the supplied synthetic Namma Sphere JSON into the existing SQLite app.

Use a copy of the database first. The import is transactional and idempotent by
source SHA-256. Existing records are never replaced; --link-existing explicitly
moves the student/teacher logins to the first demo profiles, retaining passwords.
"""
import argparse
import collections
import datetime as dt
import hashlib
import json
import re
import sqlite3
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TABLES = {'users','students','faculty','departments','courses','course_enrollments',
          'class_teacher_assignments','resources','classrooms','timetable','attendance',
          'assignments','study_materials','library_resources','library_borrowings',
          'library_reservations','exam_schedule','events','event_registrations','projects',
          'project_members','announcements','helpdesk_tickets','faculty_substitutions',
          'timetable_overrides','student_fees','faculty_salaries','hall_tickets','activity_logs'}

def connect(path):
    c = sqlite3.connect(path, timeout=30)
    c.row_factory = sqlite3.Row
    c.execute('PRAGMA foreign_keys=ON')
    c.execute('PRAGMA busy_timeout=30000')
    return c

def random_hashes(count):
    helper = ROOT / 'target' / 'import-tools' / 'ImportPasswordHashes.java'
    helper.parent.mkdir(parents=True, exist_ok=True)
    helper.write_text('import org.mindrot.jbcrypt.BCrypt; import java.util.UUID; '
        'class ImportPasswordHashes { public static void main(String[] a) {'
        'for(int i=0;i<Integer.parseInt(a[0]);i++) '
        'System.out.println(BCrypt.hashpw(UUID.randomUUID().toString()+UUID.randomUUID().toString(),BCrypt.gensalt(12)));}}', encoding='utf-8')
    result = subprocess.run(['java','--class-path',str(ROOT/'target'/'lib'/'*'),str(helper),str(count)],
                            capture_output=True,text=True,check=True,timeout=180)
    hashes = result.stdout.splitlines()
    if len(hashes) != count or any(not h.startswith('$2a$12$') for h in hashes):
        raise ValueError('Password hash generation failed')
    return iter(hashes)

def validate_source(data):
    if data.get('metadata', {}).get('synthetic') is not True:
        raise ValueError('This importer is scoped to the supplied fictional demo dataset.')
    tables = data['tables']
    indexes = {}
    for name, rows in tables.items():
        if not isinstance(rows,list) or not all(isinstance(r,dict) and isinstance(r.get('id'),int) for r in rows):
            raise ValueError('Invalid source table: '+name)
        indexes[name] = {r['id']:r for r in rows}
        if len(indexes[name]) != len(rows):
            raise ValueError('Duplicate source IDs in '+name)
    return tables, indexes

def run(args):
    source = args.source.resolve()
    raw = source.read_bytes()
    digest = hashlib.sha256(raw).hexdigest()
    data = json.loads(raw.decode('utf-8-sig'))
    tables, idx = validate_source(data)
    db = args.database.resolve()
    if not db.is_file():
        raise ValueError('The destination database must already exist.')
    c = connect(db)
    marker_exists = c.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name='dataset_imports'").fetchone()
    if marker_exists and c.execute('SELECT 1 FROM dataset_imports WHERE source_sha256=?',(digest,)).fetchone():
        print(json.dumps({'status':'already_imported','source_sha256':digest,'database':str(db)}))
        return
    if not args.link_existing:
        raise ValueError('Specify --link-existing only after choosing to connect the existing demo logins.')
    existing_accounts = {r['username']:dict(r) for r in c.execute('SELECT * FROM users')}
    for username,role in [('student','STUDENT'),('teacher','FACULTY')]:
        if username not in existing_accounts or existing_accounts[username]['role'] != role:
            raise ValueError('Expected existing '+username+' login')
    admin = c.execute("SELECT id FROM users WHERE role='ADMIN' ORDER BY id LIMIT 1").fetchone()
    if admin is None: raise ValueError('Administrator account required')
    first_student = min(tables['students'],key=lambda r:r['id'])
    first_teacher = min(tables['teachers'],key=lambda r:r['id'])
    reuse = {first_student['user_id']:existing_accounts['student']['id'],
             first_teacher['user_id']:existing_accounts['teacher']['id']}
    print('Preparing secure identities for dataset accounts...',flush=True)
    hashes = random_hashes(len(tables['users'])-len(reuse))
    timestamp = dt.datetime.now().strftime('%Y%m%d-%H%M%S-%f')
    args.backup_dir.mkdir(parents=True,exist_ok=True)
    backup = args.backup_dir.resolve()/('campussphere-before-dataset-'+timestamp+'.db')
    with sqlite3.connect(backup) as b: c.backup(b)
    asset_dir = ROOT/'data'/'materials'/('dataset-'+digest[:12])
    archive_dir = ROOT/'data'/'imports'
    report_dir = args.report_dir.resolve()
    for p in (asset_dir,archive_dir,report_dir):p.mkdir(parents=True,exist_ok=True)
    archive = archive_dir/('campussphere-'+digest[:12]+'.json')
    if archive.exists() and archive.read_bytes()!=raw: raise ValueError('Archive collision')
    archive.write_bytes(raw)
    maps = collections.defaultdict(dict)
    inserted = collections.Counter()
    now = dt.datetime.now().isoformat(timespec='seconds')
    report = {'source':str(source),'source_sha256':digest,'synthetic':True,'as_of':data['metadata']['as_of'],
              'database':str(db),'backup':str(backup),'source_archive':str(archive),'imported_at':now,
              'source_counts':{k:len(v) for k,v in tables.items()},'notes':[], 'skipped':{}}
    def put(table, **values):
        if table not in TABLES or any(not re.fullmatch('[a-z_]+',k) for k in values):raise ValueError('Invalid target table/column')
        keys=list(values)
        result=c.execute('INSERT INTO '+table+' ('+','.join(keys)+') VALUES ('+','.join('?' for _ in keys)+')',list(values.values()))
        inserted[table]+=1
        return result.lastrowid
    def record(source_table, source_id, table, **values):
        dest=put(table,**values);maps[source_table][source_id]=dest;return dest
    def asset(kind, row, text):
        path=asset_dir/(kind+'-'+str(row['id'])+'.txt')
        content='Namma Sphere fictional demo dataset\n\n'+row['title']+'\n\n'+text+'\n'
        if path.exists() and path.read_text(encoding='utf-8')!=content:raise ValueError('Existing material differs: '+str(path))
        path.write_text(content,encoding='utf-8')
        return path.as_uri()
    try:
        c.execute('BEGIN IMMEDIATE')
        baseline={t:[dict(r) for r in c.execute('SELECT * FROM '+t)] for t in TABLES}
        c.execute('CREATE TABLE IF NOT EXISTS dataset_imports (source_sha256 TEXT PRIMARY KEY, imported_at TEXT NOT NULL, report_json TEXT NOT NULL)')
        for r in tables['departments']:
            # The app uses the named school catalog, rather than department abbreviations.
            dest=c.execute("SELECT id FROM departments WHERE code='SCST'").fetchone()
            if r['code']!='CSE' or dest is None:raise ValueError('Unmapped department')
            maps['departments'][r['id']]=dest['id']
        report['notes'].append('CSE maps to the existing School of Computer Science and Technology (SCST).')
        for r in tables['users']:
            role={'TEACHER':'FACULTY','STUDENT':'STUDENT'}[r['role']]
            if r['id'] in reuse:
                user_id=reuse[r['id']]
                c.execute('UPDATE users SET name=? WHERE id=?',(r['name'],user_id))
                maps['users'][r['id']]=user_id
            else:
                username='demo.'+('teacher' if role=='FACULTY' else 'student')+'.'+str(r['id'])
                record('users',r['id'],'users',username=username,email=r['email'],password_hash=next(hashes),name=r['name'],role=role,created_at=now)
        previous_links={}
        for table,username in [('students','student'),('faculty','teacher')]:
            uid=existing_accounts[username]['id']
            previous_links[table]=[r['id'] for r in c.execute('SELECT id FROM '+table+' WHERE user_id=?',(uid,))]
            c.execute('UPDATE '+table+' SET user_id=NULL WHERE user_id=?',(uid,))
        for r in tables['teachers']:
            user=idx['users'][r['user_id']]
            record('teachers',r['id'],'faculty',user_id=maps['users'][r['user_id']],name=user['name'],department_id=maps['departments'][r['department_id']],designation=r['designation'],email=user['email'])
        for r in tables['students']:
            user=idx['users'][r['user_id']];cls=idx['classes'][r['class_id']]
            record('students',r['id'],'students',user_id=maps['users'][r['user_id']],usn=r['roll_number'],name=user['name'],department_id=maps['departments'][cls['department_id']],semester=cls['semester'],section=cls['section'],email=user['email'],admission_year=r['admission_year'],status='ACTIVE' if user['active'] else 'INACTIVE')
        report['linked_accounts']={'student':{'profile_id':maps['students'][first_student['id']],'name':idx['users'][first_student['user_id']]['name']},'teacher':{'profile_id':maps['teachers'][first_teacher['id']],'name':idx['users'][first_teacher['user_id']]['name']}}
        report['previous_profile_links']=previous_links
        report['notes'].append('Existing student/teacher usernames, passwords and login email addresses are retained. Their former profiles are kept without a login link. Other imported identities need an administrator password reset before use.')
        for r in tables['classes']:
            record('classes',r['id'],'class_teacher_assignments',faculty_id=maps['teachers'][r['class_teacher_id']],department_id=maps['departments'][r['department_id']],semester=r['semester'],section=r['section'])
        for r in tables['rooms']:
            rid=record('rooms',r['id'],'resources',name=r['name'],type=r['room_type'],building='Not specified in dataset',floor=0,capacity=r['capacity'],status='AVAILABLE')
            put('classrooms',resource_id=rid)
        report['notes'].append('Room buildings are unspecified; floor 0 is the storage default because the target schema requires a numeric floor. No extra campus resources with unknown capacities were invented.')
        for r in tables['course_offerings']:
            base=idx['courses'][r['course_id']];cls=idx['classes'][r['class_id']]
            room_caps=[idx['rooms'][slot['room_id']]['capacity'] for slot in tables['timetable'] if slot['offering_id']==r['id']]
            record('course_offerings',r['id'],'courses',code=base['code']+'-'+cls['section']+'-'+r['term'],name=base['name']+' (Section '+cls['section']+')',credits=base['credits'],department_id=maps['departments'][base['department_id']],semester=base['semester'],faculty_id=maps['teachers'][r['teacher_id']],capacity=min(room_caps))
        report['notes'].append('Six subjects become 12 section-specific course records. Codes include section and term; course capacity is the smallest assigned classroom capacity.')
        for r in tables['enrollments']:
            if r['status']!='ACTIVE':raise ValueError('Unexpected enrollment state')
            record('enrollments',r['id'],'course_enrollments',course_id=maps['course_offerings'][r['offering_id']],student_id=maps['students'][r['student_id']])
        for r in tables['timetable']:
            offering=idx['course_offerings'][r['offering_id']]
            record('timetable',r['id'],'timetable',course_id=maps['course_offerings'][r['offering_id']],faculty_id=maps['teachers'][offering['teacher_id']],resource_id=maps['rooms'][r['room_id']],day_of_week=r['weekday'],start_time=r['start_time'],end_time=r['end_time'])
        for r in tables['attendance']:
            session=idx['class_sessions'][r['session_id']];slot=idx['timetable'][session['timetable_id']]
            if session['status']!='COMPLETED':raise ValueError('Attendance for incomplete session')
            record('attendance',r['id'],'attendance',course_id=maps['course_offerings'][slot['offering_id']],student_id=maps['students'][r['student_id']],date=session['session_date'],status=r['status'])
        for r in tables['assignments']:
            record('assignments',r['id'],'assignments',course_id=maps['course_offerings'][r['offering_id']],title=r['title'],description=r['description'],due_date=r['due_on'])
        for r in tables['study_materials']:
            record('study_materials',r['id'],'study_materials',course_id=maps['course_offerings'][r['offering_id']],title=r['title'],type='NOTES',url=asset('notes',r,r['content']))
        for r in tables['exams']:
            record('exams',r['id'],'exam_schedule',course_id=maps['course_offerings'][r['offering_id']],title=r['title'],date=r['exam_date'],start_time=r['start_time'],end_time=r['end_time'],resource_id=maps['rooms'][r['room_id']])
        for r in tables['books']:
            record('books',r['id'],'library_resources',title=r['title'],author=r['author'],category='Demo study guide',url=asset('book-preview',r,r['digital_content']),total_copies=r['total_copies'])
        student_by_user={r['user_id']:r['id'] for r in tables['students']}
        for r in tables['library_loans']:
            record('library_loans',r['id'],'library_borrowings',library_resource_id=maps['books'][r['book_id']],student_id=maps['students'][student_by_user[r['borrower_user_id']]],borrowed_date=r['issued_on'],due_date=r['due_on'],returned_date=r['returned_on'] or None)
        for r in tables['book_reservations']:
            record('book_reservations',r['id'],'library_reservations',library_resource_id=maps['books'][r['book_id']],student_id=maps['students'][student_by_user[r['user_id']]],reserved_date=r['reserved_on'],status=r['status'])
        for r in tables['events']:
            day=dt.date.fromisoformat(r['event_date']);organizer=idx['users'][idx['teachers'][r['organizer_teacher_id']]['user_id']]['name']
            record('events',r['id'],'events',title=r['title'],description='Demo event. Source supplies a date only; displayed as an all-day event. Organizer: '+organizer,location=r['venue'],start_time=day.isoformat()+'T00:00',end_time=(day+dt.timedelta(days=1)).isoformat()+'T00:00',capacity=r['capacity'])
        for r in tables['event_registrations']:
            if r['status']!='CONFIRMED':raise ValueError('Unexpected event registration status')
            record('event_registrations',r['id'],'event_registrations',event_id=maps['events'][r['event_id']],student_id=maps['students'][student_by_user[r['user_id']]])
        for r in tables['projects']:
            record('projects',r['id'],'projects',title=r['title'],description='Fictional demo project imported from the supplied dataset.',mentor_id=maps['teachers'][r['mentor_teacher_id']],status='PLANNED')
        for r in tables['project_members']:
            record('project_members',r['id'],'project_members',project_id=maps['projects'][r['project_id']],student_id=maps['students'][r['student_id']])
        for r in tables['announcements']:
            record('announcements',r['id'],'announcements',title=r['title'],body=r['body'],audience=r['audience'],created_at=r['published_on']+'T00:00:00')
        for r in tables['help_tickets']:
            record('help_tickets',r['id'],'helpdesk_tickets',user_id=maps['users'][r['user_id']],subject=r['subject'],description=r['message'],status=r['status'])
        for r in tables['faculty_reallocations']:
            slot=idx['timetable'][r['timetable_id']]
            record('faculty_reallocations',r['id'],'faculty_substitutions',timetable_id=maps['timetable'][r['timetable_id']],faculty_id=maps['teachers'][r['substitute_teacher_id']],date=r['session_date'],start_time=slot['start_time'],end_time=slot['end_time'])
        for r in tables['schedule_changes']:
            slot=idx['timetable'][r['timetable_id']]
            if r['change_type']!='SUBSTITUTE_TEACHER':raise ValueError('Unsupported schedule change')
            replacement=idx['users'][idx['teachers'][r['substitute_teacher_id']]['user_id']]['name']
            record('schedule_changes',r['id'],'timetable_overrides',timetable_id=maps['timetable'][r['timetable_id']],date=r['change_date'],resource_id=maps['rooms'][slot['room_id']],start_time=slot['start_time'],end_time=slot['end_time'],reason='Substitute teacher: '+replacement+' (demo dataset).')
        payments=collections.Counter()
        for r in tables['fee_payments']:
            if r['amount_rupees']<0:raise ValueError('Negative payment')
            if r['invoice_id'] not in idx['fee_invoices']:raise ValueError('Unlinked fee payment')
            payments[r['invoice_id']]+=r['amount_rupees']
        for r in tables['fee_invoices']:
            cls=idx['classes'][idx['students'][r['student_id']]['class_id']]
            record('fee_invoices',r['id'],'student_fees',student_id=maps['students'][r['student_id']],title=r['description']+' - '+r['term'],academic_year=cls['academic_year'],amount=r['amount_rupees'],paid_amount=payments[r['id']],due_date=r['due_on'])
        for r in tables['faculty_salary']:
            net=r['basic_rupees']+r['allowances_rupees']-r['deductions_rupees']
            record('faculty_salary',r['id'],'faculty_salaries',faculty_id=maps['teachers'][r['teacher_id']],salary_month=r['month'],amount=net,status=r['status'],paid_date=r['paid_on'] or None)
        for r in tables['hall_tickets']:
            for registration in tables['exam_registrations']:
                exam=idx['exams'][registration['exam_id']];offering=idx['course_offerings'][exam['offering_id']]
                if registration['student_id']!=r['student_id'] or offering['term']!=r['term'] or registration['status']!='REGISTERED':continue
                key=str(r['id'])+':'+str(exam['id'])
                record('hall_tickets',key,'hall_tickets',exam_id=maps['exams'][exam['id']],student_id=maps['students'][r['student_id']],ticket_number=r['ticket_number']+'-E'+str(exam['id']),issued_date=r['issued_on'],status=r['status'])
        report['notes'] += [
            '60 term-wide hall tickets expand into 360 exam-specific hall tickets; -E<source exam ID> keeps ticket numbers unique. Seat numbers were not provided, so no seating allotments were created.',
            'Fees use exact invoice amounts minus summed recorded payments; salary stores basic + allowances - deductions. Original payment references and salary breakdowns remain in the archived JSON.',
            'Study material and book-preview text is stored as local .txt files. No external book/PDF links were invented.',
            'Date-only events are represented as all-day events. Project status defaults to PLANNED because the source has no project status.',
            'Fields without an equivalent in the app, including marks/submissions, employee codes, project-member roles and source approval metadata, are retained in the source archive.'
        ]
        report['skipped']={
            'submissions':{'count':len(tables['submissions']),'reason':'No submission/grading module in the app.'},
            'campus_resources':{'count':len(tables['campus_resources']),'reason':'No capacities or complete resource types; two fully specified classrooms are imported separately.'},
            'permissions':{'count':len(tables['permissions']),'reason':'Source contains leave requests, a feature the user removed; these are not timed permission requests.'},
            'teacher_meetings':{'count':len(tables['teacher_meetings']),'reason':'Meeting end times are absent; the app requires valid start and end times.'}
        }
        # Prove that all pre-existing records survive, allowing only the requested account relinking.
        for table, rows in baseline.items():
            for old in rows:
                new=dict(c.execute('SELECT * FROM '+table+' WHERE id=?',(old['id'],)).fetchone())
                expected=dict(old)
                if table=='users' and old['id'] in reuse.values():
                    sid=next(k for k,v in reuse.items() if v==old['id']);expected['name']=idx['users'][sid]['name']
                if table in previous_links and old['id'] in previous_links[table]:expected['user_id']=None
                if new!=expected:raise ValueError('Unexpected change to existing '+table+' #'+str(old['id']))
        problems=list(c.execute('PRAGMA foreign_key_check'))
        if problems:raise ValueError('Foreign key check failed: '+str(problems))
        if c.execute('PRAGMA integrity_check').fetchone()[0]!='ok':raise ValueError('Database integrity check failed')
        # Check room/teacher conflicts independently of the source claims.
        slots=list(c.execute('SELECT * FROM timetable'))
        for i,a in enumerate(slots):
            for b in slots[i+1:]:
                if a['day_of_week']==b['day_of_week'] and a['start_time']<b['end_time'] and b['start_time']<a['end_time'] and (a['faculty_id']==b['faculty_id'] or a['resource_id']==b['resource_id']):raise ValueError('Timetable conflict')
        for row in c.execute('SELECT c.id,c.capacity,COUNT(e.id) AS enrolled FROM courses c LEFT JOIN course_enrollments e ON e.course_id=c.id GROUP BY c.id'):
            if row['enrolled']>row['capacity']:raise ValueError('Course over capacity')
        put('activity_logs',user_id=admin['id'],action='IMPORT_DEMO_DATASET',entity='dataset',entity_id=None,created_at=now)
        report['inserted']=dict(inserted);report['id_mapping']={k:dict(v) for k,v in maps.items()}
        report['validation']={'foreign_keys':'passed','integrity':'passed','existing_records_preserved':'passed','existing_login_credentials_unchanged':'passed','timetable_conflicts':'none','course_capacities':'passed'}
        c.execute('INSERT INTO dataset_imports VALUES(?,?,?)',(digest,now,json.dumps(report,ensure_ascii=False)))
        c.commit()
    except Exception:
        c.rollback();raise
    finally:
        c.close()
    report_path=report_dir/('dataset-import-'+timestamp+'.json')
    report_path.write_text(json.dumps(report,indent=2,ensure_ascii=False),encoding='utf-8')
    print(json.dumps({'status':'imported','inserted':dict(inserted),'report':str(report_path),'backup':str(backup),'linked_accounts':report['linked_accounts']},indent=2))

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source',type=Path,required=True)
    parser.add_argument('--database',type=Path,required=True)
    parser.add_argument('--backup-dir',type=Path,default=ROOT/'data'/'backups')
    parser.add_argument('--report-dir',type=Path,default=ROOT/'data'/'import-reports')
    parser.add_argument('--link-existing',action='store_true')
    run(parser.parse_args())
