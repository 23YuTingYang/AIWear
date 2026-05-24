package org.example.aiwear.service;

import org.example.aiwear.dto.request.EditImageRequest;
import org.example.aiwear.dto.request.MergeImageRequest;
import org.example.aiwear.dto.request.SearchImageRequest;
import org.example.aiwear.dto.response.EditImageResponse;
import org.example.aiwear.dto.response.MergeImageResponse;
import org.example.aiwear.dto.response.SearchImageResponse;
import org.example.aiwear.dto.response.UploadImageResponse;
import org.example.aiwear.entity.ImageFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 图片文件模块服务接口
 */
public interface FileService {

    //上传图片到 OSS，并写入 files 表
    UploadImageResponse uploadImage(MultipartFile file, String authorization);


    // 查询当前⽤⼾上传的图⽚列表
    List<ImageFile> myImages(String authorization);

    //搜索出来当前用户上传的图片
    List<SearchImageResponse> search(String authorization, SearchImageRequest searchImageRequest);

    // 编辑⽤⼾上传的图⽚
    EditImageResponse edit(String authorization, EditImageRequest editImageRequest);

    // 合并图片
    MergeImageResponse merge(String authorization, MergeImageRequest mergeImageRequest);
}
