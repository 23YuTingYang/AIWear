package org.example.aiwear.mapper;

/**
 * 用户表数据访问层接口
 * 用户数据访问接口
 */

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.aiwear.entity.User;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
