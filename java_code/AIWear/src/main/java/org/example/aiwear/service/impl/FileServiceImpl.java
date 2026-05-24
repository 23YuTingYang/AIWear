package org.example.aiwear.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.aiwear.dto.request.EditImageRequest;
import org.example.aiwear.dto.request.MergeImageRequest;
import org.example.aiwear.dto.request.SearchImageRequest;
import org.example.aiwear.dto.response.EditImageResponse;
import org.example.aiwear.dto.response.MergeImageResponse;
import org.example.aiwear.dto.response.SearchImageResponse;
import org.example.aiwear.dto.response.UploadImageResponse;
import org.example.aiwear.entity.ImageFile;
import org.example.aiwear.mapper.FileMapper;
import org.example.aiwear.service.FileService;
import org.example.aiwear.service.PythonAiService;
import org.example.aiwear.service.RecordService;
import org.example.aiwear.util.AliyunOssService;
import org.example.aiwear.util.JWTUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 图片文件服务实现类
 */
@Slf4j
@Service
public class FileServiceImpl implements FileService {

    @Autowired
    private FileMapper fileMapper;

    @Autowired
    private AliyunOssService aliyunOssService;

    @Autowired
    private PythonAiService pythonAiService;

    @Autowired
    private JWTUtil jwtUtil;

    @Autowired
    private RecordService recordService;

    // 上传图片
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UploadImageResponse uploadImage(MultipartFile file, String authorization) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("图片文件不能为空");
        }

        // 审核必须放在 OSS 上传之前，避免不合规图片被写入 OSS、MySQL 和 Redis 向量库。
        if (!pythonAiService.validateImage(file)) {
            throw new IllegalArgumentException("图片审核不通过,图片不是人物或服装");
        }

        // 从 Authorization 请求头中解析当前用户，后续用于图片归属和 Redis 用户维度索引。
        String token = jwtUtil.parseToken(authorization);
        Long userId = jwtUtil.getUserId(token);

        // 审核通过后再上传 OSS，拿到公网 URL 后才能交给 Python 做向量入库。
        String ossUrl = aliyunOssService.uploadImage(file);

        // 保存 Java 业务侧图片记录，保持原有上传图片功能不变。
        ImageFile imageFile = new ImageFile();
        imageFile.setUserId(userId);
        imageFile.setFileName(file.getOriginalFilename());
        imageFile.setFileSize(file.getSize());
        imageFile.setOssUrl(ossUrl);
        fileMapper.insert(imageFile);

        // 通知 Python 根据同一个 ossUrl 生成图片描述和 CLIP 向量，供后续搜索使用。
        pythonAiService.uploadImageVector(userId, ossUrl);

        log.info("图片上传成功，用户ID: {}, 文件名: {}, OSS地址: {}", userId, file.getOriginalFilename(), ossUrl);

        return UploadImageResponse.builder()
                .url(ossUrl)
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .build();
    }

    // 我的图⽚列表（⽤来展⽰当前⽤⼾上传的图⽚）
    @Override
    public List<ImageFile> myImages(String authorization) {
        // 列表查询始终以 token 中的 userId 为准，避免前端传入 userId 查询别人的图片。
        String token = jwtUtil.parseToken(authorization);
        Long userId = jwtUtil.getUserId(token);

        LambdaQueryWrapper<ImageFile> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ImageFile::getUserId, userId)
                .orderByDesc(ImageFile::getId);
        return fileMapper.selectList(queryWrapper);
    }

    // 图⽚搜索（⽀持⽂搜图和图搜图）
    @Override
    public List<SearchImageResponse> search(String authorization, SearchImageRequest searchImageRequest) {
        // 1. 先去获取⽤⼾id
        String token = jwtUtil.parseToken(authorization);
        Long userId = jwtUtil.getUserId(token);
        //2.查询当前用户自己上传过的图片列表。
        List<ImageFile> imageFiles = myImages(authorization);
        //3.用于后面把搜索结果里的图片地址补上文件名。
        Map<String, String> map = new HashMap<>();
        for (ImageFile imageFile : imageFiles) {
            map.put(imageFile.getOssUrl(), imageFile.getFileName());
        }
        // 4. 调⽤python服务
        List<SearchImageResponse> searchImageResponseList = pythonAiService.search(userId, searchImageRequest);
        for (SearchImageResponse searchImageResponse : searchImageResponseList) {
            searchImageResponse.setFileName(map.get(searchImageResponse.getFilePath()));
        }
        // 5. 拿到数据，进⾏封装
        return searchImageResponseList;
    }


    // 图⽚编辑
    @Override
    public EditImageResponse edit(String authorization, EditImageRequest editImageRequest) {
        //1.鉴权 用户只能编辑自己上传的图片
        List<ImageFile> imageFiles = myImages(authorization);
        //2.判断当前 图片是不是在imageFiles 中的ossUrl集合中
        List<String> ossUrlList = imageFiles.stream().map(ImageFile::getOssUrl).toList();
        if (!ossUrlList.contains(editImageRequest.getImage())) {
            throw new IllegalArgumentException("无权限编辑此图片,只能编辑自己上传的图片");
        }
        //3.调用python服务
        EditImageResponse editImageResponse = pythonAiService.edit(editImageRequest);
        //4.新增调⽤记录
        Long userId = jwtUtil.getUserId(jwtUtil.parseToken(authorization));
        recordService.editSave(userId, editImageRequest, editImageResponse);

        return editImageResponse;
    }

    @Override
    public MergeImageResponse merge(String authorization, MergeImageRequest mergeImageRequest) {
        // 1. 鉴权 -> ⽤⼾只能合并⾃⼰上传的图⽚
        List<ImageFile> imageFiles = myImages(authorization);
        // 2. 判断上传的图⽚是否包含在imageFiles的ossUrl字段集合中
        List<String> urls = imageFiles.stream().map(ImageFile::getOssUrl).toList();
        if (!urls.contains(mergeImageRequest.getImage1()) ||
                !urls.contains(mergeImageRequest.getImage2())) {
            throw new IllegalArgumentException("只能合并自己上传的图片");
        }
        // 3. 调⽤python服务
        MergeImageResponse mergeImageResponse = pythonAiService.merge(mergeImageRequest);
        //4.    新增调⽤记录
        Long userId = jwtUtil.getUserId(jwtUtil.parseToken(authorization));
        recordService.mergeSave(userId, mergeImageRequest, mergeImageResponse);

        return mergeImageResponse;
    }


}
