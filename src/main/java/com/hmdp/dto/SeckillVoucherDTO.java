package com.hmdp.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 秒杀券 + 店铺基本信息，用于 tool 返回。
 */
@Data
public class SeckillVoucherDTO {
    private Long voucherId;
    private Long shopId;
    private String shopName;
    private String title;
    private Integer stock;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;
}
