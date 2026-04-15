package com.ecommerce.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RegisterRequest {
    @NotBlank @Email
    private String email;
    @NotBlank @Size(min = 8, max = 100)
    private String password;
    @Size(max = 100) private String firstName;
    @Size(max = 100) private String lastName;
}
