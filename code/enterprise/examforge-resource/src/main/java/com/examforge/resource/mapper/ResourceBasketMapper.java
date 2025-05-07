package com.examforge.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.examforge.resource.domain.ResourceBasket;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface ResourceBasketMapper extends BaseMapper<ResourceBasket> {

    /** 唯一索引(user_id, resource_id)幂等：重复加入返回 0 */
    @Insert("INSERT INTO resource_basket(user_id, resource_id) VALUES(#{userId}, #{resourceId}) " +
            "ON DUPLICATE KEY UPDATE id = id")
    int insertIgnore(@Param("userId") Long userId, @Param("resourceId") Long resourceId);
}
