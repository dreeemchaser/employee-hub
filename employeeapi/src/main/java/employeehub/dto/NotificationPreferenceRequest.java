package employeehub.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NotificationPreferenceRequest {

    @NotNull
    private Boolean emailEnabled;
}
