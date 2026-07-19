package com.chuhezhe.core.share.service;

import com.chuhezhe.core.share.dto.*;

import java.util.List;

public interface ShareService {

    /** 创建分享链接 */
    ShareVO create(CreateShareRequest request);

    /** 查看分享基本信息（无需提取码） */
    ShareInfoResponse getShareInfo(String code);

    /** 访问分享（校验提取码、过期、次数），返回文件信息 + 下载 URL */
    ShareAccessResponse access(String code, ShareAccessRequest request);

    byte[] downloadFiles(String code, List<Long> fileIds);

    List<ShareVO> listMyShares();

    /** 取消分享 */
    void cancel(Long shareId);

    /** 更新分享设置 */
    ShareVO update(Long shareId, UpdateShareRequest request);
}
