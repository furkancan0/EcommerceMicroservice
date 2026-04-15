package com.ecommerce.dto;

import lombok.*;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private long   expiresIn;
    private UUID   userId;
    private String email;
    private String role;
    @Builder.Default private String tokenType = "Bearer";
}
