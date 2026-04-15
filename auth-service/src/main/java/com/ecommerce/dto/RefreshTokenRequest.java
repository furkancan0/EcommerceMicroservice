package com.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RefreshTokenRequest {
    @NotBlank
    private String refreshToken;
}
