package employeehub.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import employeehub.exception.BusinessRuleException;
import employeehub.exception.GlobalExceptionHandler;
import employeehub.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins every status mapping in {@link GlobalExceptionHandler}. Uses a standalone MockMvc over a
 * throwaway controller that throws each mapped exception on demand — this keeps the matrix
 * independent of any real controller's behavior and free of a Spring context.
 */
class GlobalExceptionHandlerWebTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void resourceNotFound_mapsTo404() throws Exception {
        mockMvc.perform(get("/boom/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void illegalArgument_mapsTo400() throws Exception {
        mockMvc.perform(get("/boom/bad-request"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void businessRule_mapsTo409() throws Exception {
        mockMvc.perform(get("/boom/business-rule"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("conflict"));
    }

    @Test
    void illegalState_mapsTo409() throws Exception {
        mockMvc.perform(get("/boom/illegal-state"))
                .andExpect(status().isConflict());
    }

    @Test
    void optimisticLock_mapsTo409() throws Exception {
        mockMvc.perform(get("/boom/optimistic-lock"))
                .andExpect(status().isConflict());
    }

    @Test
    void accessDenied_mapsTo403() throws Exception {
        mockMvc.perform(get("/boom/access-denied"))
                .andExpect(status().isForbidden());
    }

    @Test
    void beanValidation_mapsTo400WithFieldError() throws Exception {
        mockMvc.perform(post("/boom/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("name")));
    }

    @Test
    void genericException_mapsTo500() throws Exception {
        mockMvc.perform(get("/boom/generic"))
                .andExpect(status().isInternalServerError());
    }

    @Data
    static class Payload {
        @NotBlank(message = "name is required")
        private String name;
    }

    @RestController
    @RequestMapping("/boom")
    static class ThrowingController {

        @GetMapping("/not-found")
        void notFound() {
            throw new ResourceNotFoundException("nope");
        }

        @GetMapping("/bad-request")
        void badRequest() {
            throw new IllegalArgumentException("bad");
        }

        @GetMapping("/business-rule")
        void businessRule() {
            throw new BusinessRuleException("conflict");
        }

        @GetMapping("/illegal-state")
        void illegalState() {
            throw new IllegalStateException("state");
        }

        @GetMapping("/optimistic-lock")
        void optimisticLock() {
            throw new OptimisticLockingFailureException("stale");
        }

        @GetMapping("/access-denied")
        void accessDenied() {
            throw new AccessDeniedException("denied");
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody Payload payload) {
            // reaching here means validation passed
        }

        @GetMapping("/generic")
        void generic() {
            throw new RuntimeException("kaboom");
        }
    }
}
