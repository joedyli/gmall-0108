package com.atguigu.gmall.oms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.oms.entity.OrderOperateHistoryEntity;

import java.util.Map;

/**
 * 订单操作历史记录
 *
 * @author fengge
 * @email fengge@atguigu.com
 * @date 2021-07-19 14:02:47
 */
public interface OrderOperateHistoryService extends IService<OrderOperateHistoryEntity> {

    PageResultVo queryPage(PageParamVo paramVo);
}

