package org.example.aiwear.service;


import lombok.extern.slf4j.Slf4j;
import org.example.aiwear.dto.request.EditImageRequest;
import org.example.aiwear.dto.request.MergeImageRequest;
import org.example.aiwear.dto.response.EditImageResponse;
import org.example.aiwear.dto.response.MergeImageResponse;
import org.example.aiwear.entity.Record;
import org.springframework.stereotype.Service;

import java.util.List;


public interface RecordService {

    // 查看调⽤记录
    List<Record> myRecords(Long userId, String action);

    //编辑生成的调用记录
    void editSave(Long userId , EditImageRequest editImageRequest , EditImageResponse editImageResponse );

    //合并生成的调用记录
    void mergeSave(Long userId , MergeImageRequest mergeImageRequest , MergeImageResponse mergeImageResponse);
}
