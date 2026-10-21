package employeehub.dto;

import employeehub.domain.enums.DocumentAccessLevel;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DocumentAccessLevelRequest {

    @NotNull
    private DocumentAccessLevel accessLevel;
}
