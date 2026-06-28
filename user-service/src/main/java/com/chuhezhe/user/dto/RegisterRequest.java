package com.chuhezhe.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank
    @Size(min = 3, max = 64)
    private String account;

    @NotBlank
    @Size(min = 6, max = 128)
    private String password;

    private String locale;
    private String theme;
}
