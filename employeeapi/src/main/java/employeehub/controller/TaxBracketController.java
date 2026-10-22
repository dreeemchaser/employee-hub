package employeehub.controller;

import employeehub.dto.ApiResponse;
import employeehub.dto.TaxBracketRequest;
import employeehub.dto.TaxBracketResponse;
import employeehub.service.EmployeeService;
import employeehub.service.TaxBracketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tax-brackets")
@RequiredArgsConstructor
@Tag(name = "Tax Brackets")
public class TaxBracketController {

    private final TaxBracketService taxBracketService;
    private final EmployeeService employeeService;

    @GetMapping
    @Operation(summary = "List tax brackets, optionally filtered by tax year")
    public ResponseEntity<ApiResponse<Page<TaxBracketResponse>>> getAll(
            @RequestParam(required = false) Integer taxYear,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(taxBracketService.getAll(taxYear, pageable)));
    }

    @PostMapping
    @Operation(summary = "Create a tax bracket")
    public ResponseEntity<ApiResponse<TaxBracketResponse>> create(
            @Valid @RequestBody TaxBracketRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        var actor = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(new TaxBracketResponse(
                        taxBracketService.create(request, actor.getId()))));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a tax bracket")
    public ResponseEntity<ApiResponse<TaxBracketResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody TaxBracketRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        var actor = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(new TaxBracketResponse(
                taxBracketService.update(id, request, actor.getId()))));
    }
}
