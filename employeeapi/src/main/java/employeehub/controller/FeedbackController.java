package employeehub.controller;

import employeehub.domain.enums.FeedbackVisibility;
import employeehub.dto.ApiResponse;
import employeehub.dto.FeedbackRequest;
import employeehub.dto.FeedbackResponse;
import employeehub.service.EmployeeService;
import employeehub.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/feedback")
@RequiredArgsConstructor
@Tag(name = "Feedback")
public class FeedbackController {
    private final FeedbackService feedbackService;
    private final EmployeeService employeeService;

    @GetMapping("/received")
    @Operation(summary = "Get feedback received by the current employee")
    public ResponseEntity<ApiResponse<List<FeedbackResponse>>> received(
            @AuthenticationPrincipal UserDetails userDetails) {
        var employee = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(feedbackService.received(employee).stream()
                .map(feedback -> new FeedbackResponse(feedback,
                        feedback.getVisibility() == FeedbackVisibility.ANONYMOUS)).toList()));
    }

    @GetMapping("/authored")
    @Operation(summary = "Get feedback authored by the current employee")
    public ResponseEntity<ApiResponse<List<FeedbackResponse>>> authored(
            @AuthenticationPrincipal UserDetails userDetails) {
        var employee = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(feedbackService.authored(employee).stream()
                .map(feedback -> new FeedbackResponse(feedback,
                        feedback.getVisibility() == FeedbackVisibility.PRIVATE)).toList()));
    }

    @PostMapping
    @Operation(summary = "Submit feedback for an employee")
    public ResponseEntity<ApiResponse<FeedbackResponse>> create(
            @Valid @RequestBody FeedbackRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        var author = employeeService.getByEmail(userDetails.getUsername());
        var saved = feedbackService.create(author, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(new FeedbackResponse(saved, false)));
    }
}
