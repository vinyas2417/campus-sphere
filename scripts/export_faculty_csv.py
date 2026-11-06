import sqlite3
import csv

def generate_csv():
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
        except Exception:
            pass

    query = '''
    SELECT c.semester, d.name as department, c.code as course_code, c.name as course_name,
           f.name as faculty_name, f.designation, u.username, u.email
    FROM courses c
    JOIN faculty f ON c.faculty_id = f.id
    JOIN departments d ON f.department_id = d.id
    LEFT JOIN users u ON f.user_id = u.id
    ORDER BY c.semester, d.name, c.code
    '''

    rows = cur.execute(query).fetchall()

    csv_rows = []
    for r in rows:
        sem, dept, code, cname, fname, desig, uname, email = r
        pwd = creds.get(uname, 'N/A')
        csv_rows.append({
            'semester': sem,
            'department': dept,
            'course_code': code,
            'course_name': cname,
            'faculty_name': fname,
            'designation': desig,
            'username': uname,
            'password': pwd,
            'email': email
        })

    output_path = 'faculty_assigned_courses_credentials.csv'
    fieldnames = ['semester', 'department', 'course_code', 'course_name', 'faculty_name', 'designation', 'username', 'password', 'email']
    
    with open(output_path, 'w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(csv_rows)

    print(f"Generated {output_path} with {len(csv_rows)} rows.")
    conn.close()

if __name__ == '__main__':
    generate_csv()
