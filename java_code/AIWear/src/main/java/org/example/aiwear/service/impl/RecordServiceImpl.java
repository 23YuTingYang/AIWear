package org.example.aiwear.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.aiwear.dto.request.EditImageRequest;
import org.example.aiwear.dto.request.MergeImageRequest;
import org.example.aiwear.dto.response.EditImageResponse;
import org.example.aiwear.dto.response.MergeImageResponse;
import org.example.aiwear.entity.Record;
import org.example.aiwear.mapper.RecordMapper;
import org.example.aiwear.service.RecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 历史调用记录服务实现类
 */

@Slf4j
@Service
public class RecordServiceImpl implements RecordService {

    @Autowired
    private RecordMapper recordMapper;

    // 查看调⽤记录
    @Override
    public List<Record> myRecords(Long userId, String action) {
        // 创建查询条件
        LambdaQueryWrapper<Record> queryWrapper = new LambdaQueryWrapper<>();
        // 添加查询条件：用户ID
        queryWrapper.eq(Record::getUserId, userId);
        // 添加查询条件：动作
        if (action != null && !action.isBlank()) {
            queryWrapper.eq(Record::getAction, action);
        }
        // 添加排序条件：按ID降序
        queryWrapper.orderByDesc(Record::getId);
        return recordMapper.selectList(queryWrapper);
    }

    // 保存编辑调⽤记录
    @Override
    public void editSave(Long userId, EditImageRequest editImageRequest, EditImageResponse editImageResponse) {
        Record record = new Record();
        record.setUserId(userId);
        record.setAction("edit");
        record.setInputOssUrl1(editImageRequest.getImage());
        record.setInstruction(editImageRequest.getInstruction());
        record.setResultUrl(editImageResponse.getUrl());
        record.setOutputOssUrl(editImageResponse.getUrl());
        recordMapper.insert(record);
    }

    // 保存合并调⽤记录
    @Override
    public void mergeSave(Long userId, MergeImageRequest mergeImageRequest, MergeImageResponse mergeImageResponse) {
        Record record = new Record();
        record.setUserId(userId);
        record.setAction("merge");
        record.setInputOssUrl1(mergeImageRequest.getImage1());
        record.setInputOssUrl2(mergeImageRequest.getImage2());
        record.setInstruction(mergeImageRequest.getInstruction());
        record.setResultUrl(mergeImageResponse.getUrl());
        record.setOutputOssUrl(mergeImageResponse.getUrl());
        recordMapper.insert(record);
    }
}
