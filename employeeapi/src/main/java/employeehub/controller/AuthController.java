package employeehub.controller;

import employeehub.domain.Employee;
import employeehub.dto.*;
import employeehub.security.JwtUtil;
import employeehub.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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

    @PostMapping("/login")
    @Operation(summary = "Login and receive a JWT token")
    public ResponseEntity<ApiResponse<Map<String, String>>> login(@RequestBody Map<String, String> body) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(body.get("email"), body.get("password"))
        );
        Employee employee = employeeService.getByEmail(body.get("email"));
        String token = jwtUtil.generateToken(employee.getEmail(), employee.getRole().name());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("token", token)));
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
