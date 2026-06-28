package com.chuhezhe.core.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateDirRequest {

    private Long parentId;

    @NotBlank
    @Size(max = 255)
    private String name;
}
