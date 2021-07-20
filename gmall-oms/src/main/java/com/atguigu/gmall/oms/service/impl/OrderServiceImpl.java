package com.atguigu.gmall.oms.service.impl;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.oms.entity.OrderItemEntity;
import com.atguigu.gmall.oms.feign.GmallPmsClient;
import com.atguigu.gmall.oms.mapper.OrderItemMapper;
import com.atguigu.gmall.oms.vo.OrderItemVo;
import com.atguigu.gmall.oms.vo.OrderSubmitVo;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.pms.entity.SpuEntity;
import com.atguigu.gmall.ums.entity.UserAddressEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.oms.mapper.OrderMapper;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.oms.service.OrderService;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;


@Service("orderService")
public class OrderServiceImpl extends ServiceImpl<OrderMapper, OrderEntity> implements OrderService {

    @Autowired
    private OrderItemMapper itemMapper;

    @Autowired
    private GmallPmsClient pmsClient;

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<OrderEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<OrderEntity>()
        );

        return new PageResultVo(page);
    }

    @Transactional
    @Override
    public void saveOrder(OrderSubmitVo submitVo, Long userId) {
        // 保存订单表
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setUserId(userId);
        //orderEntity.setUsername();
        orderEntity.setOrderSn(submitVo.getOrderToken());
        orderEntity.setCreateTime(new Date());
        orderEntity.setTotalAmount(submitVo.getTotalPrice());
        orderEntity.setPayAmount(submitVo.getTotalPrice());
        orderEntity.setPayType(1);
        orderEntity.setSourceType(0);
        orderEntity.setStatus(0);
        orderEntity.setDeliveryCompany(submitVo.getDeliveryCompany());
        orderEntity.setAutoConfirmDay(15);
        // 物流信息
        UserAddressEntity address = submitVo.getAddress();
        orderEntity.setReceiverRegion(address.getRegion());
        orderEntity.setReceiverProvince(address.getProvince());
        orderEntity.setReceiverPostCode(address.getPostCode());
        orderEntity.setReceiverPhone(address.getPhone());
        orderEntity.setReceiverName(address.getName());
        orderEntity.setReceiverCity(address.getCity());
        orderEntity.setReceiverAddress(address.getAddress());
        orderEntity.setConfirmStatus(0);
        orderEntity.setDeleteStatus(0);
        orderEntity.setUseIntegration(submitVo.getBounds());
        this.save(orderEntity);
        // 获取orderId
        Long orderId = orderEntity.getId();

//        try {
//            TimeUnit.SECONDS.sleep(3);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }

        // 保存订单详情表
        List<OrderItemVo> items = submitVo.getItems();
        items.forEach(item -> {
            OrderItemEntity orderItemEntity = new OrderItemEntity();
            orderItemEntity.setOrderId(orderId);
            orderItemEntity.setOrderSn(submitVo.getOrderToken());
            // 根据skuId 查询sku的信息
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(item.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity != null) {
                orderItemEntity.setSkuId(skuEntity.getId());
                orderItemEntity.setSkuName(skuEntity.getName());
                orderItemEntity.setSkuPrice(skuEntity.getPrice());
                orderItemEntity.setSkuPic(skuEntity.getDefaultImage());
                orderItemEntity.setCategoryId(skuEntity.getCategoryId());
            }
            // TODO：查询销售属性
            orderItemEntity.setSkuAttrsVals(null);
            // TODO: 查询品牌
            orderItemEntity.setSpuBrand(null);

            // 查询spu
            ResponseVo<SpuEntity> spuEntityResponseVo = this.pmsClient.querySpuById(skuEntity.getSpuId());
            SpuEntity spuEntity = spuEntityResponseVo.getData();
            if (spuEntity != null) {
                orderItemEntity.setSpuId(spuEntity.getId());
                orderItemEntity.setSpuName(spuEntity.getName());
            }

            // TODO：查询描述信息
            orderItemEntity.setSpuPic(null);

            // TODO: 查询积分优惠
            orderItemEntity.setGiftIntegration(null);
            orderItemEntity.setGiftGrowth(null);

            this.itemMapper.insert(orderItemEntity);
        });

    }

}