package org.example.aiwear.service.impl;

import org.example.aiwear.dto.request.EditImageRequest;
import org.example.aiwear.dto.request.MergeImageRequest;
import org.example.aiwear.dto.response.EditImageResponse;
import org.example.aiwear.dto.response.MergeImageResponse;
import org.example.aiwear.entity.Record;
import org.example.aiwear.mapper.RecordMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = RecordServiceImpl.class)
class RecordServiceImplTest {

    @MockBean
    private RecordMapper recordMapper;

    @Autowired
    private RecordServiceImpl recordService;

// 测试说明：验证只按用户查询历史记录时，能返回结果列表。
    @Test
    void myRecords_shouldQueryByUserAndNoAction() {
        Record record = new Record();
        record.setId(1L);
        record.setUserId(3L);
        record.setAction("edit");
        when(recordMapper.selectList(any())).thenReturn(List.of(record));

        List<Record> data = recordService.myRecords(3L, null);

        assertThat(data).hasSize(1);
        assertThat(data.get(0).getUserId()).isEqualTo(3L);
        verify(recordMapper).selectList(any());
    }

// 测试说明：验证按用户和动作类型联合查询时，能返回结果列表。
    @Test
    void myRecords_shouldQueryByUserAndAction() {
        Record record = new Record();
        record.setId(2L);
        record.setUserId(3L);
        record.setAction("merge");
        when(recordMapper.selectList(any())).thenReturn(List.of(record));

        List<Record> data = recordService.myRecords(3L, "merge");

        assertThat(data).hasSize(1);
        assertThat(data.get(0).getAction()).isEqualTo("merge");
        verify(recordMapper).selectList(any());
    }

// 测试说明：验证保存编辑记录时，会向数据库插入一条记录。
    @Test
    void editSave_shouldInsertRecord() {
        EditImageRequest req = new EditImageRequest();
        req.setImage("http://oss/source.png");
        req.setInstruction("change color");

        EditImageResponse resp = new EditImageResponse();
        resp.setUrl("http://tmp/result.png");
        resp.setSaveUrl("http://oss/result.png");

        recordService.editSave(5L, req, resp);

        verify(recordMapper).insert(any(Record.class));
    }

// 测试说明：验证保存合并记录时，会向数据库插入一条记录。
    @Test
    void mergeSave_shouldInsertRecord() {
        MergeImageRequest req = new MergeImageRequest();
        req.setImage1("http://oss/1.png");
        req.setImage2("http://oss/2.png");
        req.setInstruction("merge");

        MergeImageResponse resp = new MergeImageResponse();
        resp.setUrl("http://tmp/merge.png");
        resp.setSaveUrl("http://oss/merge.png");

        recordService.mergeSave(6L, req, resp);

        verify(recordMapper).insert(any(Record.class));
    }
}
