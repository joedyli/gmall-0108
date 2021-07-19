package com.atguigu.gmall.wms.vo;

import lombok.Data;

@Data
public class SkuLockVo {

    private Long skuId;
    private Integer count;
    private Boolean lock; // 锁定状态
    private Long wareSkuId; // 在锁定成功的情况下，记录锁定仓库的id，以方便将来减库存 或者 解锁库存
}
