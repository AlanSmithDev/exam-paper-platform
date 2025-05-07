package com.examforge.trade.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("coupon_template")
public class CouponTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String type;            // FULL_REDUCTION/DISCOUNT/POINTS
    private Integer discountCents;
    private Integer minSpendCents;
    private Double discountRate;
    private Integer rateCapCents;
    private Integer total;
    private Integer granted;
    private Integer perLimit;
    private Integer dailyPerLimit;  // 每人每日限领(0=不限，≥1 为天天领券类)
    private Integer validDays;
    private Integer status;
}
