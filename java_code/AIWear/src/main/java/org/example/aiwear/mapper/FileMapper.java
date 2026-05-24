package org.example.aiwear.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.aiwear.entity.ImageFile;

/**
 * 图片文件表数据访问层接口
 */
@Mapper
public interface FileMapper extends BaseMapper<ImageFile> {
}
