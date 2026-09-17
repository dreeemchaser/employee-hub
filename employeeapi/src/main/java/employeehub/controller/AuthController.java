package employeehub.controller;

import employeehub.domain.Employee;
import employeehub.dto.*;
import employeehub.security.JwtUtil;
import employeehub.service.EmployeeService;
import employeehub.service.LoginAttemptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final EmployeeService employeeService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final LoginAttemptService loginAttemptService;

    @PostMapping("/login")
    @Operation(summary = "Login and receive a JWT token")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody Map<String, String> body) {
        String email = body.get("email");

        // Refuse early if the account is currently locked, before spending a
        // password check. Return 423 with a Retry-After header and the seconds
        // remaining so the client can show an accurate countdown.
        if (loginAttemptService.isLocked(email)) {
            return lockedResponse(email);
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, body.get("password"))
            );
        } catch (AuthenticationException ex) {
            // Track the failure for known accounts. If that failure just tripped
            // the lock, respond 423 with countdown; otherwise rethrow for a 401.
            loginAttemptService.recordFailure(email);
            if (loginAttemptService.isLocked(email)) {
                return lockedResponse(email);
            }
            throw ex;
        }

        loginAttemptService.recordSuccess(email);
        Employee employee = employeeService.getByEmail(email);
        String token = jwtUtil.generateToken(employee.getEmail(), employee.getRole().name());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("token", token)));
    }

    // Build the 423 Locked response with a Retry-After header (seconds) and a
    // retryAfterSeconds field in the body for the client countdown.
    private ResponseEntity<ApiResponse<Map<String, Object>>> lockedResponse(String email) {
        long retryAfter = loginAttemptService.secondsUntilUnlock(email);
        return ResponseEntity.status(HttpStatus.LOCKED)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter))
                .body(ApiResponse.error(
                        "Account is temporarily locked due to repeated failed login attempts. Try again later.",
                        Map.of("retryAfterSeconds", retryAfter)));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated employee profile")
    public ResponseEntity<ApiResponse<MeResponse>> me(@AuthenticationPrincipal UserDetails userDetails) {
        Employee employee = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(new MeResponse(employee)));
    }

    @PatchMapping("/me")
    @Operation(summary = "Update own profile (name, phone, address, nationality, gender)")
    public ResponseEntity<ApiResponse<MeResponse>> updateMe(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody UpdateMeRequest request) {
        Employee employee = employeeService.getByEmail(userDetails.getUsername());
        Employee updated = employeeService.updateMe(employee.getId(), request);
        return ResponseEntity.ok(ApiResponse.ok(new MeResponse(updated)));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change own password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody ChangePasswordRequest request) {
        Employee employee = employeeService.getByEmail(userDetails.getUsername());
        employeeService.changePassword(employee.getId(), request);
        return ResponseEntity.ok(ApiResponse.ok("Password changed successfully", null));
    }
}
