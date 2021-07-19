package com.atguigu.gmall.oms.vo;

import com.atguigu.gmall.ums.entity.UserAddressEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderSubmitVo {

    // 收货地址
    private UserAddressEntity address;

    // 消费积分
    private Integer bounds;

    // 物流公司、配送方式
    private String deliveryCompany;

    // 送货清单
    private List<OrderItemVo> items;

    // 防重的唯一标识
    private String orderToken;

    // 支付方式
    private Integer payType;

    // 总价格  验总价
    private BigDecimal totalPrice;

    // TODO： 发票  买家留言
}
