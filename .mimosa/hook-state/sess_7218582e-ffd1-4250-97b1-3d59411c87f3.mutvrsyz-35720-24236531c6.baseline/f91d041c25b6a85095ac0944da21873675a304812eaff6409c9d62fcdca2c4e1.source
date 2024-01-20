package com.examforge.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.examforge.resource.domain.ResourceItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ResourceItemMapper extends BaseMapper<ResourceItem> {

    /** 浏览计数（前台展示口径，异步回写由统计任务接管前先用原子自增） */
    @Update("UPDATE resource_item SET browse_count = browse_count + 1 WHERE id = #{id}")
    int incrBrowse(@Param("id") Long id);

    @Update("UPDATE resource_item SET download_count = download_count + 1 WHERE id = #{id}")
    int incrDownload(@Param("id") Long id);
}
