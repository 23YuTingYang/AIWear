package org.example.aiwear.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.aiwear.entity.Record;

/**
 * 历史记录数据表对应的Mapper接口
 */

@Mapper
public interface RecordMapper extends BaseMapper<Record> {
}
