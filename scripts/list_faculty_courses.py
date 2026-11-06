import sqlite3
import csv

def main():
    conn = sqlite3.connect('data/campussphere.db')
    cur = conn.cursor()

    # Load credentials
    creds = {}
    for filename in ['data/import-reports/gmu_portal_credentials.csv', 'data/semester_login_credentials.csv']:
        try:
            with open(filename, 'r', encoding='utf-8') as f:
                reader = csv.DictReader(f)
                for r in reader:
                    uname = r.get('username')
                    pw = r.get('temporary_password') or r.get('password')
                    if uname and pw:
                        creds[uname] = pw
        except Exception as e:
            pass

    query = '''
    SELECT f.id, f.name, f.designation, d.code as dept_code, d.name as dept_name,
           u.id as user_id, u.username, u.email,
           c.id as course_id, c.code as course_code, c.name as course_name, c.semester
    FROM courses c
    JOIN faculty f ON c.faculty_id = f.id
    JOIN departments d ON f.department_id = d.id
    LEFT JOIN users u ON f.user_id = u.id
    ORDER BY c.semester, c.department_id, c.code
    '''

    rows = cur.execute(query).fetchall()

    print(f"Total Course Assignments: {len(rows)}")
    for r in rows:
        fid, fname, desig, dcode, dname, uid, uname, email, cid, ccode, cname, sem = r
        pwd = creds.get(uname, 'Default / Check CSV')
        print(f"Sem {sem} | {dcode} | {ccode} - {cname} | Faculty: {fname} ({desig}) | Username: {uname} | Password: {pwd}")

    # Also list all faculty even if unassigned
    all_fac_query = '''
    SELECT f.id, f.name, f.designation, d.code as dept_code, u.username, u.email
    FROM faculty f
    JOIN departments d ON f.department_id = d.id
    LEFT JOIN users u ON f.user_id = u.id
    ORDER BY f.id
    '''
    print("\nALL FACULTY:")
    for r in cur.execute(all_fac_query).fetchall():
        fid, fname, desig, dcode, uname, email = r
        pwd = creds.get(uname, 'N/A')
        print(f"ID: {fid} | {fname} ({desig}, {dcode}) | Username: {uname} | Password: {pwd}")

    conn.close()

if __name__ == '__main__':
    main()
