package com.chuhezhe.core.share.dto;

import lombok.Data;

import java.util.List;

@Data
public class ShareFileNode {

    private Long id;
    private String name;
    private boolean isDir;
    private long size;
    private List<ShareFileNode> children;
}
