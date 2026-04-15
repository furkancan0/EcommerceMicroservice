package com.ecommerce.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoginRequest {
    @NotBlank @Email private String email;
    @NotBlank        private String password;
}
