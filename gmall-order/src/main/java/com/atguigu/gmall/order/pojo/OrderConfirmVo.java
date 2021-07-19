package com.atguigu.gmall.order.pojo;

import com.atguigu.gmall.oms.vo.OrderItemVo;
import com.atguigu.gmall.ums.entity.UserAddressEntity;
import lombok.Data;

import java.util.List;

@Data
public class OrderConfirmVo {

    // 收获地址列表
    private List<UserAddressEntity> addresses;

    // 商品列表
    private List<OrderItemVo> items;

    // 购物积分
    private Integer bounds;

    // 防重的唯一标识（幂等），页面有一份  redis有一份
    private String orderToken;

}
