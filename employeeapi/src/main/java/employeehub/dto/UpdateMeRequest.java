package employeehub.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMeRequest {
    private String firstName;
    private String lastName;
    private String phone;
    private String address;
    private String nationality;
    private String gender;
}
