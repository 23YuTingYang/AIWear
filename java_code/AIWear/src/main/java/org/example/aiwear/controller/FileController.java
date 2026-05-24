package org.example.aiwear.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.aiwear.common.Result;
import org.example.aiwear.dto.request.EditImageRequest;
import org.example.aiwear.dto.request.MergeImageRequest;
import org.example.aiwear.dto.request.SearchImageRequest;
import org.example.aiwear.dto.response.EditImageResponse;
import org.example.aiwear.dto.response.MergeImageResponse;
import org.example.aiwear.dto.response.SearchImageResponse;
import org.example.aiwear.dto.response.UploadImageResponse;
import org.example.aiwear.entity.ImageFile;
import org.example.aiwear.log.ApiLog;
import org.example.aiwear.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 图片文件模块的控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/file")
public class FileController {

    @Autowired
    private FileService fileService;

    //上传图片到系统和 OSS
    @ApiLog
    @PostMapping("/upload/image")
    public Result<UploadImageResponse> uploadImage(@RequestParam(value = "file", required = false) MultipartFile file,
                                                   @RequestHeader(value = "Authorization") String authorization) {
        if (file == null || file.isEmpty()) {
            return Result.clientError("图片文件不能为空");
        }
        return Result.success("图片上传成功", fileService.uploadImage(file, authorization));
    }

    // 我的图⽚列表（⽤来展⽰当前⽤⼾上传的图⽚）
    @ApiLog
    @GetMapping("/my-images")
    public Result<List<ImageFile>> myImages(
            @RequestHeader(value = "Authorization") String authorization
    ) {
        List<ImageFile> imageFiles = fileService.myImages(authorization);
        return Result.success("查询成功", imageFiles);
    }


    // 图⽚搜索（⽀持⽂搜图和图搜图）
    @ApiLog
    @PostMapping("/search")
    public Result<List<SearchImageResponse>> search(
            @RequestHeader(value = "Authorization") String authorization,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "file", required = false) MultipartFile file
    ) {
        SearchImageRequest searchImageRequest = new SearchImageRequest();
        searchImageRequest.setQuery(query);
        searchImageRequest.setFile(file);
        return Result.success("查询成功", fileService.search(authorization, searchImageRequest));
    }

    // 图⽚编辑
    @ApiLog
    @PostMapping("/edit")
    public Result<EditImageResponse> edit(
            @RequestBody @Validated EditImageRequest editImageRequest,
            @RequestHeader(value = "Authorization") String authorization
    ) {
        return Result.success("编辑成功", fileService.edit(authorization,
                editImageRequest));
    }

    // 图⽚合并
    @ApiLog
    @PostMapping("/merge")
    public Result<MergeImageResponse> merge(
            @RequestBody @Validated MergeImageRequest mergeImageRequest,
            @RequestHeader(value = "Authorization") String authorization
    ) {
        return Result.success("合并成功", fileService.merge(authorization,
                mergeImageRequest));
    }
}
















