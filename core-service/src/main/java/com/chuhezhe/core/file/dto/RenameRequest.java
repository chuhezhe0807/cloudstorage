package com.chuhezhe.core.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RenameRequest {

    @NotBlank
    @Size(max = 255)
    private String newName;
}
