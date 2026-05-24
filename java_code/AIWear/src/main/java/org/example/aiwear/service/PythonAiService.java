package org.example.aiwear.service;

import org.example.aiwear.dto.request.EditImageRequest;
import org.example.aiwear.dto.request.MergeImageRequest;
import org.example.aiwear.dto.request.SearchImageRequest;
import org.example.aiwear.dto.response.EditImageResponse;
import org.example.aiwear.dto.response.MergeImageResponse;
import org.example.aiwear.dto.response.SearchImageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Python AI 服务调用门面
 */
public interface PythonAiService {

    /**
     * 图片上传到 OSS 前先进行内容审核。
     */
    boolean validateImage(MultipartFile file);

    /**
     * 调用 Python 服务保存图片描述和 CLIP 向量。
     */
    void uploadImageVector(Long userId, String ossUrl);

    // 图⽚搜索（⽀持⽂搜图和图搜图）
    List<SearchImageResponse> search(Long userId, SearchImageRequest searchImageRequest);

    // 图⽚编辑
    EditImageResponse edit(EditImageRequest editImageRequest);

    // 图⽚合并
    MergeImageResponse merge(MergeImageRequest mergeImageRequest);
}
