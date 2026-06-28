package com.chuhezhe.user.dto;

import lombok.Data;

@Data
public class UserPreferencesRequest {

    private String locale;
    private String theme;
}
