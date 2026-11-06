import sqlite3

def run():
    conn = sqlite3.connect('data/campussphere.db')
    cur = conn.cursor()

    courses = cur.execute('SELECT id, department_id, code, name, semester FROM courses').fetchall()

    # 1. Update/populate study_materials for missing courses (DSC, EA, MP)
    missing_materials = {
        'DSC': ('Data Structures and Complexity (DSC)', 'VIDEO', 'https://youtu.be/8hly31xKli0'),
        'EA': ('Environmental Awareness & Ecological Studies', 'VIDEO', 'https://youtu.be/7G3eXiU2HH4'),
        'MP': ('Major Project: System Architecture & Capstone Guide', 'VIDEO', 'https://youtu.be/8A4sT4B5bK8')
    }

    for c_id, dept_id, code, name, sem in courses:
        if code in missing_materials:
            title, m_type, url = missing_materials[code]
            existing = cur.execute('SELECT id FROM study_materials WHERE course_id=?', (c_id,)).fetchall()
            if not existing:
                cur.execute('INSERT INTO study_materials(course_id, title, type, url) VALUES(?, ?, ?, ?)', (c_id, title, m_type, url))

    # 2. Update/populate assignments for all 15 courses
    assignment_specs = {
        'PYTHON': ('Python Lab: Data Structures & OOP Problem Set', 'Solve Python algorithms, list comprehensions, and classes.', '2026-11-20', 'https://cs50.harvard.edu/python/psets/0/'),
        'WEBDEV': ('WebDev Project: Responsive Semantic Web Application', 'Develop a responsive multi-page web app with CSS Grid & DOM events.', '2026-11-22', 'https://developer.mozilla.org/en-US/docs/Learn_web_development/Extensions/Forms/Test_your_skills_HTML5_controls'),
        'MATH': ('Mathematics Problem Set: Linear Algebra & Matrices', 'Complete problem sets covering matrix factorization and vector spaces.', '2026-11-25', 'https://ocw.mit.edu/courses/18-06-linear-algebra-spring-2010/pages/assignments/'),
        'ADIG': ('Analog & Digital Lab: Combinational Logic Circuits', 'Design and simulate multiplexers and ALUs with timing analysis.', '2026-11-28', 'https://www.allaboutcircuits.com/worksheets/combinational-logic-functions/'),
        'OOPS': ('OOPS Project: Polymorphic Hierarchy & SOLID Principles', 'Construct an extensible Java class hierarchy applying design patterns.', '2026-12-05', 'https://cs61b.org/sp24/materials/proj/proj1a/'),
        'DSC': ('DSC Assignment: Balanced Trees & Shortest Path Graph Algorithms', 'Implement self-balancing trees and Dijkstra shortest paths.', '2026-12-08', 'https://ocw.mit.edu/courses/6-006-introduction-to-algorithms-spring-2020/pages/assignments/'),
        'OS': ('OS Lab: Concurrency, Thread Synchronization & Memory Paging', 'Implement POSIX thread synchronization and page replacement simulation.', '2026-12-10', 'https://pages.cs.wisc.edu/~remzi/OSTEP/threads-intro.pdf'),
        'COA': ('COA Assignment: CPU Architecture & Instruction Execution', 'Analyze pipelining, cache hit ratios, and write assembly instructions.', '2026-12-12', 'https://www.nand2tetris.org/project05'),
        'AML': ('AML Assignment: Deep Neural Networks & Backpropagation', 'Train a multi-class deep neural network using PyTorch on benchmark data.', '2026-12-15', 'https://cs231n.github.io/assignments2024/assignment1/'),
        'CLOUD': ('Cloud Computing Lab: Docker Containers & Cloud Deployment', 'Deploy microservices with containerization, load balancers, and auto-scaling.', '2026-12-18', 'https://kubernetes.io/docs/tutorials/kubernetes-basics/'),
        'MAD': ('MAD Project: Native Android App with Jetpack Compose & Room', 'Develop a mobile app with MVVM architecture, Room DB, and REST APIs.', '2026-12-20', 'https://developer.android.com/codelabs/basic-android-kotlin-compose-first-app'),
        'CGV': ('CGV Assignment: OpenGL 3D Transformations & Shader Pipeline', 'Render interactive 3D textured scenes with Phong lighting shaders.', '2026-12-22', 'https://learnopengl.com/Getting-started/Shaders'),
        'CNS': ('CNS Lab: Network Packet Sniffing & Cryptographic Protocols', 'Analyze Wireshark captures and implement AES/RSA cryptographic schemes.', '2026-12-24', 'https://seedsecuritylabs.org/labs.html'),
        'EA': ('EA Case Study: Campus Carbon Footprint & Sustainability Audit', 'Conduct an environmental impact audit and propose renewable solutions.', '2026-12-26', 'https://www.unep.org/resources/report/global-environment-outlook-6'),
        'MP': ('Major Project Milestone 1: SRS & Architectural Design Specification', 'Submit comprehensive software requirements, ER diagrams, and prototype plan.', '2026-12-28', 'https://ocw.mit.edu/courses/1-124j-foundations-of-software-engineering-fall-2000/pages/projects/')
    }

    # Clear old assignments
    cur.execute('DELETE FROM assignments')

    for c_id, dept_id, code, name, sem in courses:
        if code in assignment_specs:
            title, desc, due, url = assignment_specs[code]
            cur.execute('INSERT INTO assignments(course_id, title, description, due_date, attachment_url) VALUES(?, ?, ?, ?, ?)',
                        (c_id, title, desc, due, url))

    # 3. Update library_resources with clean, authoritative links
    book_specs = {
        'PYTHON': ('Python 3 Documentation & Tutorial', 'Python Software Foundation', 'https://docs.python.org/3/tutorial/'),
        'WEBDEV': ('MDN Web Development Guides', 'MDN Contributors', 'https://developer.mozilla.org/en-US/docs/Learn_web_development'),
        'MATH': ('College Algebra 2e', 'OpenStax', 'https://openstax.org/details/books/college-algebra-2e'),
        'ADIG': ('Lessons in Electric Circuits: Digital Logic', 'Tony R. Kuphaldt', 'https://www.allaboutcircuits.com/textbook/'),
        'OOPS': ('Think Java: How to Think Like a Computer Scientist (2e)', 'Allen B. Downey & Chris Mayfield', 'https://greenteapress.com/wp/think-java-2e/'),
        'DSC': ('OpenDSA: Interactive Data Structures & Algorithms', 'Virginia Tech et al.', 'https://opendsa.org/'),
        'OS': ('Operating Systems: Three Easy Pieces (OSTEP)', 'Remzi H. Arpaci-Dusseau & Andrea C. Arpaci-Dusseau', 'https://pages.cs.wisc.edu/~remzi/OSTEP/'),
        'COA': ('Dive into Systems: Computer Organization & Architecture', 'Suzanne J. Matthews, Tia Newhall & Kevin C. Webb', 'https://diveintosystems.org/book/'),
        'AML': ('Dive into Deep Learning (D2L)', 'Aston Zhang, Zack C. Lipton et al.', 'https://d2l.ai/d2l-en.pdf'),
        'CLOUD': ('Cloud Computing Architecture & OpenStack Guide', 'OpenInfra Foundation', 'https://docs.openstack.org/latest/'),
        'MAD': ('Android Developers Official Architecture Guide', 'Google Android Developers', 'https://developer.android.com/develop'),
        'CGV': ('LearnOpenGL: Graphics Programming Reference', 'Joey de Vries', 'https://learnopengl.com/book/learnopengl_book.pdf'),
        'CNS': ('Guide to Enterprise Network Security', 'NIST CSRC', 'https://csrc.nist.gov/pubs/sp/800/215/final'),
        'EA': ('UNEP Environmental Education Resources', 'UNEP Ozone Secretariat', 'https://ozone.unep.org/reset-earth-education-resources'),
        'MP': ('Software Engineering & Capstone Project Repository', 'MIT OpenCourseWare', 'https://ocw.mit.edu/courses/1-124j-foundations-of-software-engineering-fall-2000/pages/projects/')
    }

    for c_id, dept_id, code, name, sem in courses:
        if code in book_specs:
            btitle, author, url = book_specs[code]
            cur.execute('''UPDATE library_resources SET 
                           title=?, author=?, url=?, category=?
                           WHERE course_id=?''',
                        (btitle, author, url, f'{name} course resource', c_id))

    conn.commit()
    count_assignments = cur.execute('SELECT COUNT(1) FROM assignments').fetchone()[0]
    count_study_materials = cur.execute('SELECT COUNT(1) FROM study_materials').fetchone()[0]
    count_library_resources = cur.execute('SELECT COUNT(1) FROM library_resources').fetchone()[0]
    print(f'Total assignments: {count_assignments}')
    print(f'Total study materials: {count_study_materials}')
    print(f'Total library resources: {count_library_resources}')
    conn.close()

if __name__ == '__main__':
    run()
