package dopaminelite.payment_portal.service;

import java.util.UUID;

/**
 * Service interface for enrolling students in classes.
 * This should be implemented to call the appropriate microservice (Content/Video service)
 * that manages class enrollments.
 */
public interface EnrollmentService {
    
    /**
     * Enrolls a student in a class (folder).
     * This method should call the Content Management Service or Video Service
     * to add the student's email/ID to the folder's allowedEmails list.
     *
     * @param studentId the student UUID
     * @param classId the class/folder ID
     * @throws RuntimeException if enrollment fails
     */
    void enrollStudent(UUID studentId, String classId);
    
}
