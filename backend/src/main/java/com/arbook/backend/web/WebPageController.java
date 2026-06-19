package com.arbook.backend.web;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.arbook.backend.common.service.AdminService;
import com.arbook.backend.common.service.CmsService;
import com.arbook.backend.common.service.StudentService;
import com.arbook.backend.common.service.TeacherService;
import com.arbook.backend.security.AccessGuard;
import com.arbook.backend.security.SecurityFacade;

@Controller
public class WebPageController {
    private final JdbcTemplate jdbc;
    private final AccessGuard accessGuard;
    private final StudentService studentService;
    private final TeacherService teacherService;
    private final CmsService cmsService;
    private final AdminService adminService;
    private final PasswordEncoder passwordEncoder;
    private final SecurityFacade security;

    public WebPageController(JdbcTemplate jdbc, AccessGuard accessGuard,
                             StudentService studentService, TeacherService teacherService,
                             CmsService cmsService, AdminService adminService,
                             PasswordEncoder passwordEncoder, SecurityFacade security) {
        this.jdbc = jdbc;
        this.accessGuard = accessGuard;
        this.studentService = studentService;
        this.teacherService = teacherService;
        this.cmsService = cmsService;
        this.adminService = adminService;
        this.passwordEncoder = passwordEncoder;
        this.security = security;
    }

    @GetMapping("/")
    public String index() {
        return "redirect:/home";
    }

    @GetMapping("/home")
    public String home(Model model) {
        model.addAttribute("counts", Map.of(
                "subjects", count("subjects"),
                "textbooks", count("textbooks"),
                "lessons", count("lessons"),
                "arContents", count("ar_contents")
        ));
        return "landing";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/guide")
    public String guide(Model model) {
        addPageChrome(model, "/guide");
        model.addAttribute("title", "Hướng Dẫn Demo");
        model.addAttribute("subtitle", "Chạy backend local, mở Unity Editor hoặc cài APK Android, sau đó quét marker bằng camera.");
        model.addAttribute("rows", List.of(
                Map.of("label", "Backend", "value", "http://localhost:8080"),
                Map.of("label", "Unity API", "value", "GET /api/ar-markers/resolve?code=BIO_CELL_001"),
                Map.of("label", "APK Android", "value", "D:\\ARBOOK\\dist\\ARTextbookDemo-Android.apk"),
                Map.of("label", "Marker", "value", "BIO_CELL_001, SOLAR_SYSTEM_001, HEART_001")
        ));
        return "page";
    }

    @GetMapping("/register")
    public String showRegister() {
        return "register";
    }

    @PostMapping("/register")
    @Transactional
    public String registerUser(@RequestParam String fullName,
                               @RequestParam String email,
                               @RequestParam String password,
                               @RequestParam(required = false) String phone,
                               @RequestParam String role,
                               Model model) {
        if (!"STUDENT".equals(role) && !"TEACHER".equals(role)) {
            model.addAttribute("error", "Vai trò đăng ký không hợp lệ. Chỉ cho phép đăng ký Học sinh hoặc Giáo viên.");
            return "register";
        }
        try {
            Integer count = jdbc.queryForObject("select count(*) from users where email = ?", Integer.class, email);
            if (count != null && count > 0) {
                model.addAttribute("error", "Email đã tồn tại trong hệ thống.");
                return "register";
            }

            Long id = jdbc.queryForObject("""
                    insert into users(full_name, email, password_hash, phone, status, created_at, updated_at)
                    values (?, ?, ?, ?, 'ACTIVE', now(), now())
                    returning id
                    """, Long.class, fullName, email, passwordEncoder.encode(password), phone);

            jdbc.update("""
                    insert into user_roles(user_id, role_id, assigned_at)
                    values (?, (select id from roles where code = ?::role_code), now())
                    """, id, role);

            List<Long> classIds = jdbc.query("select id from classes where name = '10A1' limit 1", (rs, rowNum) -> rs.getLong("id"));
            if (!classIds.isEmpty()) {
                Long classId = classIds.get(0);
                if ("STUDENT".equals(role)) {
                    jdbc.update("""
                            insert into class_students(class_id, student_id, joined_at, is_active)
                            values (?, ?, now(), true)
                            on conflict do nothing
                            """, classId, id);
                } else if ("TEACHER".equals(role)) {
                    jdbc.update("""
                            insert into teacher_classes(teacher_id, class_id, assigned_at, is_main_teacher)
                            values (?, ?, now(), true)
                            on conflict do nothing
                            """, id, classId);
                }
            }

            return "redirect:/login?registered";
        } catch (Exception e) {
            model.addAttribute("error", "Lỗi đăng ký tài khoản: " + e.getMessage());
            return "register";
        }
    }

    @GetMapping({
            "/student/dashboard",
            "/student/textbooks",
            "/student/progress",
            "/student/feedbacks",
            "/teacher/dashboard",
            "/teacher/classes",
            "/teacher/assignments",
            "/teacher/analytics",
            "/cms/dashboard",
            "/cms/subjects",
            "/cms/textbooks",
            "/cms/chapters",
            "/cms/lessons",
            "/cms/pages",
            "/cms/markers",
            "/cms/models",
            "/cms/ar-contents",
            "/admin/dashboard",
            "/admin/users",
            "/admin/roles",
            "/admin/schools",
            "/admin/classes",
            "/admin/review-queue",
            "/admin/feedbacks",
            "/admin/settings",
            "/admin/analytics"
    })
    public String simplePage(Model model, jakarta.servlet.http.HttpServletRequest request) {
        String path = request.getRequestURI();
        addPageChrome(model, path);
        
        String tab = "dashboard";
        if (path.contains("/textbooks")) tab = "textbooks";
        else if (path.contains("/progress")) tab = "progress";
        else if (path.contains("/feedbacks")) tab = "feedbacks";
        else if (path.contains("/classes")) tab = "classes";
        else if (path.contains("/assignments")) tab = "assignments";
        else if (path.contains("/analytics")) tab = "analytics";
        else if (path.contains("/subjects")) tab = "subjects";
        else if (path.contains("/chapters")) tab = "chapters";
        else if (path.contains("/lessons")) tab = "lessons";
        else if (path.contains("/pages")) tab = "pages";
        else if (path.contains("/markers")) tab = "markers";
        else if (path.contains("/models")) tab = "models";
        else if (path.contains("/ar-contents")) tab = "ar-contents";
        else if (path.contains("/users")) tab = "users";
        else if (path.contains("/roles")) tab = "roles";
        else if (path.contains("/schools")) tab = "schools";
        else if (path.contains("/review-queue")) tab = "review-queue";
        else if (path.contains("/settings")) tab = "settings";

        model.addAttribute("activeTab", tab);
        model.addAttribute("title", titleFromPath(path));
        model.addAttribute("subtitle", "Màn hình quản lý và hiển thị thông tin thực tế.");
        model.addAttribute("rows", dashboardRows(path));

        Long currentUserId = security.currentUserIdOrNull();

        // 1. STUDENT WORKSPACE
        if (path.startsWith("/student")) {
            Long studentId = currentUserId;
            if (studentId == null) {
                return "redirect:/login";
            }
            if (studentId != null) {
                model.addAttribute("textbooks", jdbc.queryForList("""
                    select t.id, t.title, t.cover_image_url, s.name as subject_name, g.name as grade_name,
                           (select count(*) from lessons l join chapters c on c.id = l.chapter_id where c.textbook_id = t.id) as lesson_count
                    from textbooks t
                    join class_textbooks ct on ct.textbook_id = t.id
                    join class_students cs on cs.class_id = ct.class_id
                    join subjects s on s.id = t.subject_id
                    join grades g on g.id = t.grade_id
                    where cs.student_id = ? and cs.is_active = true
                    """, studentId));
                
                model.addAttribute("assignments", jdbc.queryForList("""
                    select ta.id, ta.title, ta.due_date, ta.note, l.title as lesson_title, l.id as lesson_id, ast.status::text as assignment_status
                    from teacher_assignments ta
                    join assignment_students ast on ast.assignment_id = ta.id
                    join lessons l on l.id = ta.lesson_id
                    where ast.student_id = ?
                    order by ta.due_date asc
                    """, studentId));
                
                model.addAttribute("recentScans", jdbc.queryForList("""
                    select sh.scanned_at, sh.result::text as result, m.marker_code, m.marker_name
                    from scan_history sh
                    join ar_markers m on m.id = sh.marker_id
                    where sh.student_id = ?
                    order by sh.scanned_at desc
                    limit 5
                    """, studentId));
                
                Integer scanCount = jdbc.queryForObject("select count(*) from scan_history where student_id = ?", Integer.class, studentId);
                model.addAttribute("scanCount", scanCount != null ? scanCount : 0);
                
                Integer completedCount = jdbc.queryForObject("select count(*) from learning_progress where student_id = ? and status = 'COMPLETED'", Integer.class, studentId);
                model.addAttribute("completedCount", completedCount != null ? completedCount : 0);

                List<Map<String, Object>> classInfo = jdbc.queryForList("""
                    select c.name as class_name, s.name as school_name
                    from class_students cs
                    join classes c on c.id = cs.class_id
                    join schools s on s.id = c.school_id
                    where cs.student_id = ? and cs.is_active = true
                    limit 1
                    """, studentId);
                if (!classInfo.isEmpty()) {
                    model.addAttribute("classInfo", classInfo.get(0));
                }
            }
            return "student/dashboard";
        }

        // 2. TEACHER WORKSPACE
        if (path.startsWith("/teacher")) {
            Long teacherId = currentUserId;
            if (teacherId == null) {
                return "redirect:/login";
            }
            if (teacherId != null) {
                List<Map<String, Object>> classes = jdbc.queryForList("""
                    select c.id, c.name, c.school_year, g.name as grade_name,
                           (select count(*) from class_students cs where cs.class_id = c.id and cs.is_active = true) as student_count
                    from classes c
                    join teacher_classes tc on tc.class_id = c.id
                    join grades g on g.id = c.grade_id
                    where tc.teacher_id = ?
                    """, teacherId);
                model.addAttribute("classes", classes);
                
                model.addAttribute("assignments", jdbc.queryForList("""
                    select ta.id, ta.title, ta.due_date, c.name as class_name, l.title as lesson_title,
                           (select count(*) from assignment_students ast where ast.assignment_id = ta.id and ast.status = 'COMPLETED') as completed_count,
                           (select count(*) from assignment_students ast where ast.assignment_id = ta.id) as total_count
                    from teacher_assignments ta
                    join classes c on c.id = ta.class_id
                    join lessons l on l.id = ta.lesson_id
                    where ta.teacher_id = ?
                    order by ta.created_at desc
                    """, teacherId));

                model.addAttribute("lessonsList", jdbc.queryForList("select id, title from lessons where status = 'PUBLISHED' order by title"));
                model.addAttribute("classId", 1L); // default for charts
                model.addAttribute("analytics", teacherService.getClassAnalytics(1L));

                Boolean isPremium = jdbc.queryForObject("select is_premium from users where id = ?", Boolean.class, teacherId);
                model.addAttribute("isPremium", isPremium != null && isPremium);
            }
            return "teacher/dashboard";
        }

        // 3. CMS WORKSPACE
        if (path.startsWith("/cms")) {
            model.addAttribute("textbooks", jdbc.queryForList("""
                select t.id, t.title, s.name as subject_name, g.name as grade_name, t.status::text as status
                from textbooks t
                join subjects s on s.id = t.subject_id
                join grades g on g.id = t.grade_id
                order by t.created_at desc
                """));
            
            model.addAttribute("models", jdbc.queryForList("""
                select id, name, description, file_url, format::text as format, size_mb, status::text as status
                from three_d_models
                order by created_at desc
                """));
            
            model.addAttribute("markers", jdbc.queryForList("""
                select m.id, m.marker_code, m.marker_name, m.image_url, m.status::text as status, p.page_number
                from ar_markers m
                join textbook_pages p on p.id = m.textbook_page_id
                order by m.created_at desc
                """));
            
            model.addAttribute("arContents", jdbc.queryForList("""
                select c.id, c.title, l.title as lesson_title, m.marker_code, model.name as model_name, c.status::text as status
                from ar_contents c
                join lessons l on l.id = c.lesson_id
                join ar_markers m on m.id = c.marker_id
                left join three_d_models model on model.id = c.default_model_id
                order by c.created_at desc
                """));

            model.addAttribute("lessonsList", jdbc.queryForList("select id, title from lessons order by title"));
            model.addAttribute("markersList", jdbc.queryForList("select id, marker_code from ar_markers order by marker_code"));
            model.addAttribute("modelsList", jdbc.queryForList("select id, name from three_d_models order by name"));
            model.addAttribute("pagesList", jdbc.queryForList("select id, concat(page_title, ' (Trang ', page_number, ')') as title from textbook_pages"));
            
            return "cms/dashboard";
        }

        // 4. ADMIN WORKSPACE
        if (path.startsWith("/admin")) {
            model.addAttribute("users", jdbc.queryForList("""
                select u.id, u.full_name, u.email, u.phone, u.status::text status, u.last_login_at, u.created_at,
                       coalesce(string_agg(r.code::text, ',' order by r.code::text), '') roles
                from users u
                left join user_roles ur on ur.user_id = u.id
                left join roles r on r.id = ur.role_id
                group by u.id
                order by u.created_at desc
                """));
            
            model.addAttribute("pendingReviews", adminService.getPendingReviews());
            
            model.addAttribute("feedbacks", jdbc.queryForList("""
                select f.*, f.type::text type, f.status::text status, u.full_name user_name, l.title lesson_title
                from feedbacks f
                join users u on u.id = f.user_id
                left join lessons l on l.id = f.lesson_id
                order by f.created_at desc
                """));

            model.addAttribute("totalUsers", jdbc.queryForObject("select count(*) from users where status <> 'DELETED'", Long.class));
            model.addAttribute("totalScans", jdbc.queryForObject("select count(*) from scan_history", Long.class));
            model.addAttribute("successfulScans", jdbc.queryForObject("select count(*) from scan_history where result = 'SUCCESS'", Long.class));
            model.addAttribute("pendingReviewCount", jdbc.queryForObject("select count(*) from content_review_requests where status = 'PENDING'", Long.class));

            return "admin/dashboard";
        }

        return "page";
    }

    @GetMapping("/student/lessons/{id}")
    public String lesson(@PathVariable Long id, Model model) {
        accessGuard.requireStudentCanAccessLesson(id);
        addPageChrome(model, "/student/lessons/" + id);
        model.addAttribute("title", "Chi Tiết Bài Học");
        model.addAttribute("subtitle", "Thông tin bài học, 3D interactive viewer và bài kiểm tra ngắn.");
        
        model.addAttribute("rows", studentService.getLessonDetails(id));
        List<String> modelUrls = studentService.getModelUrls(id);
        if (!modelUrls.isEmpty()) {
            model.addAttribute("modelUrl", modelUrls.get(0));
            model.addAttribute("labels", jdbc.queryForList("""
                    select label_name as name, description
                    from model_labels
                    where model_id = (
                        select default_model_id from ar_contents where lesson_id = ? and status = 'PUBLISHED'
                        limit 1
                    )
                    order by display_order
                    """, id));
            model.addAttribute("animations", jdbc.queryForList("""
                    select name, description
                    from animations
                    where ar_content_id = (
                        select id from ar_contents where lesson_id = ? and status = 'PUBLISHED'
                        limit 1
                    )
                    order by order_no
                    """, id));
        }
        
        List<Map<String, Object>> quizzes = jdbc.queryForList("select id, title from quizzes where lesson_id = ? and status = 'PUBLISHED'", id);
        if (!quizzes.isEmpty()) {
            model.addAttribute("quiz", quizzes.get(0));
        }

        return "student/lesson_detail";
    }

    @GetMapping("/student/quizzes/{quizId}")
    public String quiz(@PathVariable Long quizId, Model model) {
        Long lessonId = jdbc.queryForObject("select lesson_id from quizzes where id = ?", Long.class, quizId);
        accessGuard.requireStudentCanAccessLesson(lessonId);
        addPageChrome(model, "/student/quizzes/" + quizId);
        model.addAttribute("title", "Làm Bài Kiểm Tra");
        model.addAttribute("subtitle", "Trả lời câu hỏi trắc nghiệm bên dưới và nộp bài.");
        
        model.addAttribute("quizId", quizId);
        model.addAttribute("questions", jdbc.queryForList("""
                select q.id, q.question_text, q.question_type::text as question_type, q.score, q.order_no
                from quiz_questions q
                where q.quiz_id = ?
                order by q.order_no
                """, quizId));
        
        model.addAttribute("answers", jdbc.queryForList("""
                select a.id, a.question_id, a.answer_text, a.order_no
                from quiz_answers a
                join quiz_questions q on q.id = a.question_id
                where q.quiz_id = ?
                order by q.id, a.order_no
                """, quizId));

        return "student/quiz";
    }

    @GetMapping("/student/scan")
    public String scan(Model model) {
        addPageChrome(model, "/student/scan");
        model.addAttribute("title", "Quét Marker AR Trực Tiếp");
        model.addAttribute("subtitle", "Sử dụng camera của thiết bị để quét các marker trong sách giáo khoa.");
        model.addAttribute("markers", jdbc.queryForList("""
                select marker_code, marker_name, image_url
                from ar_markers
                where status = 'ACTIVE'
                order by marker_code
                """));
        return "scan";
    }

    @GetMapping("/teacher/classes/{id}")
    public String teacherClass(@PathVariable Long id, Model model) {
        accessGuard.requireTeacherCanAccessClass(id);
        addPageChrome(model, "/teacher/classes/" + id);
        model.addAttribute("title", "Chi Tiết Lớp Học");
        model.addAttribute("subtitle", "Danh sách học sinh và phân tích kết quả học tập.");
        model.addAttribute("rows", teacherService.getClassStudents(id));
        model.addAttribute("classId", id);
        model.addAttribute("analytics", teacherService.getClassAnalytics(id));
        return "teacher/class_detail";
    }

    @GetMapping("/cms/ar-contents/{id}/preview")
    public String arPreview(@PathVariable Long id, Model model) {
        addPageChrome(model, "/cms/ar-contents/" + id + "/preview");
        model.addAttribute("title", "Xem Trước Nội Dung AR");
        model.addAttribute("subtitle", "Xem metadata và mô hình 3D trước khi gửi duyệt.");
        model.addAttribute("rows", cmsService.getArContentPreview(id));
        List<String> modelUrls = cmsService.getArContentModelUrl(id);
        if (!modelUrls.isEmpty()) {
            model.addAttribute("modelUrl", modelUrls.get(0));
        }
        return "cms/preview";
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    private void addPageChrome(Model model, String path) {
        model.addAttribute("currentPath", path);
        model.addAttribute("sectionLabel", sectionLabel(path));
    }

    private String sectionLabel(String path) {
        if (path.startsWith("/student")) {
            return "Student workspace";
        }
        if (path.startsWith("/teacher")) {
            return "Teacher workspace";
        }
        if (path.startsWith("/cms")) {
            return "Content workspace";
        }
        if (path.startsWith("/admin")) {
            return "Admin workspace";
        }
        if (path.startsWith("/guide")) {
            return "Demo guide";
        }
        return "Workspace";
    }

    private String titleFromPath(String path) {
        String last = path.substring(path.lastIndexOf('/') + 1).replace("-", " ");
        if ("dashboard".equals(last)) {
            String[] parts = path.split("/");
            return parts.length > 1 ? parts[1].toUpperCase() + " Dashboard" : "Dashboard";
        }
        return Character.toUpperCase(last.charAt(0)) + last.substring(1);
    }

    private List<Map<String, Object>> dashboardRows(String path) {
        if (path.startsWith("/cms")) {
            return cmsService.getDashboardRows();
        }
        if (path.startsWith("/admin")) {
            return adminService.getDashboardRows();
        }
        if (path.startsWith("/teacher")) {
            return teacherService.getDashboardRows();
        }
        return studentService.getDashboardRows();
    }
}
