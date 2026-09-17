package employeehub.controller;

import employeehub.domain.Employee;
import employeehub.dto.*;
import employeehub.security.JwtUtil;
import employeehub.service.EmployeeService;
import employeehub.service.LoginAttemptService;
import employeehub.service.PasswordResetService;
import employeehub.service.RefreshTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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
    private final PasswordResetService passwordResetService;
    private final RefreshTokenService refreshTokenService;

    // Dev/test convenience: when true, forgot-password echoes the token in the
    // response so the flow can be tested before the email channel exists.
    // MUST remain false in production — the email delivers the token.
    @Value("${app.security.password-reset.expose-token:false}")
    private boolean exposeResetToken;

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
        String accessToken = jwtUtil.generateToken(employee.getEmail(), employee.getRole().name());
        String refreshToken = refreshTokenService.issue(employee);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new access + refresh token pair")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refresh(
            @Valid @RequestBody RefreshRequest request) {
        // Rotation returns empty when the token is unknown, expired, or revoked
        // (reuse). Surface that as 401 so the client routes to login; a fresh
        // pair is returned on success. Mirrors the state-based return in login().
        return refreshTokenService.rotate(request.getRefreshToken())
                .<ResponseEntity<ApiResponse<Map<String, Object>>>>map(pair -> ResponseEntity.ok(ApiResponse.ok(Map.of(
                        "accessToken", pair.accessToken(),
                        "refreshToken", pair.refreshToken()))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Invalid or expired refresh token")));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke a refresh token (logout)")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshRequest request) {
        // Idempotent: revoking an unknown or already-revoked token still succeeds.
        refreshTokenService.revoke(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.ok("Logged out successfully", null));
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

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset token for an email")
    public ResponseEntity<ApiResponse<Map<String, String>>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        var token = passwordResetService.createResetToken(request.getEmail());

        // Always respond the same way regardless of whether the account exists,
        // to avoid revealing which emails are registered.
        Map<String, String> data = new HashMap<>();
        if (exposeResetToken) {
            token.ifPresent(t -> data.put("resetToken", t));
        }
        return ResponseEntity.ok(ApiResponse.ok(
                "If an account exists for that email, a password reset link has been sent.",
                data.isEmpty() ? null : data));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset a password using a valid reset token")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("Password has been reset successfully. You can now sign in.", null));
    }
}
