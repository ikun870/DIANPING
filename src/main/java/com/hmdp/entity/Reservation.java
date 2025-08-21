package com.hmdp.entity;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/* 没用上
 * 为测试agents的预订功能而创建的Reservation类
 * 该类包含预订的基本信息，如姓名、性别、电话和预订时间
 * 完整的功能是：用户向agent查询当前时间段到未来1天时间内存在秒杀优惠卷活动的商店，agent返回商店信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Reservation {

    private Long id;
    private String name;
    private String gender;
    private String phone;
    private LocalDateTime reservationTime;
}
