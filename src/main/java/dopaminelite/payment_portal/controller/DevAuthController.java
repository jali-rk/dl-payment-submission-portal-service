package dopaminelite.payment_portal.controller;

import dopaminelite.payment_portal.dto.auth.AuthResponse;
import dopaminelite.payment_portal.dto.auth.DevLoginRequest;
import dopaminelite.payment_portal.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Development-only authentication controller.
 * Provides JWT tokens for testing without connecting to the User Service.
 * 
 * This controller is only active when spring.profiles.active includes 'dev' or 'test'.
 * It will NOT be available in production.
 * 
 * Available test users:
 * - admin@test.com / admin123 (ADMIN role)
 * - staff@test.com / staff123 (STAFF role)
 * - student@test.com / student123 (STUDENT role)
 */
@RestController
@RequestMapping("/api/v1/dev/auth")
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "spring.profiles.active",
    havingValue = "dev",
    matchIfMissing = true
)
public class DevAuthController {

    private final JwtUtil jwtUtil;
    
    // In-memory test users (development only)
    private static final Map<String, TestUser> TEST_USERS = new HashMap<>();
    
    static {
        TEST_USERS.put("admin@test.com", new TestUser(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "admin@test.com",
            "admin123",
            List.of("ADMIN")
        ));
        TEST_USERS.put("staff@test.com", new TestUser(
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            "staff@test.com",
            "staff123",
            List.of("STAFF")
        ));
        TEST_USERS.put("student@test.com", new TestUser(
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            "student@test.com",
            "student123",
            List.of("STUDENT")
        ));
        TEST_USERS.put("student2@test.com", new TestUser(
            UUID.fromString("44444444-4444-4444-4444-444444444444"),
            "student2@test.com",
            "student123",
            List.of("STUDENT")
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> devLogin(@Valid @RequestBody DevLoginRequest request) {
        TestUser user = TEST_USERS.get(request.getEmail());
        
        if (user == null || !user.password.equals(request.getPassword())) {
            return ResponseEntity.status(401).body(Map.of(
                "error", "Invalid credentials",
                "message", "Email or password is incorrect"
            ));
        }
        
        String token = jwtUtil.generateToken(user.userId, user.email, user.roles);
        
        return ResponseEntity.ok(new AuthResponse(token, user.userId, user.email, user.roles));
    }

    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> listTestUsers() {
        List<Map<String, Object>> users = TEST_USERS.values().stream()
            .map(user -> Map.of(
                "email", (Object) user.email,
                "password", user.password,
                "roles", user.roles
            ))
            .toList();
        
        return ResponseEntity.ok(Map.of(
            "message", "Development test users (do not use in production)",
            "users", users
        ));
    }

    private record TestUser(UUID userId, String email, String password, List<String> roles) {}
}
