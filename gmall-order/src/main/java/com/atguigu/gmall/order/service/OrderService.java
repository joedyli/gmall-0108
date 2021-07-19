package com.atguigu.gmall.order.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.exception.OrderException;
import com.atguigu.gmall.oms.vo.OrderItemVo;
import com.atguigu.gmall.oms.vo.OrderSubmitVo;
import com.atguigu.gmall.order.feign.*;
import com.atguigu.gmall.order.interceptor.LoginInterceptor;
import com.atguigu.gmall.order.pojo.OrderConfirmVo;
import com.atguigu.gmall.order.pojo.UserInfo;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.ums.entity.UserAddressEntity;
import com.atguigu.gmall.ums.entity.UserEntity;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.atguigu.gmall.wms.vo.SkuLockVo;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class OrderService {

    @Autowired
    private GmallUmsClient umsClient;

    @Autowired
    private GmallCartClient cartClient;

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private GmallOmsClient omsClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final  String KEY_PREFIX = "order:token:";

    public OrderConfirmVo confirm() {

        OrderConfirmVo confirmVo = new OrderConfirmVo();
        // 获取登录用户信息
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();

        // 查询收获地址列表
        ResponseVo<List<UserAddressEntity>> addressResponseVo = this.umsClient.queryAddressesByUserId(userId);
        List<UserAddressEntity> userAddressEntities = addressResponseVo.getData();
        confirmVo.setAddresses(userAddressEntities);

        // 查询送货清单
        ResponseVo<List<Cart>> cartResponseVo = this.cartClient.queryCheckedCartsByUserId(userId);
        List<Cart> carts = cartResponseVo.getData();
        if (CollectionUtils.isEmpty(carts)){
            throw new OrderException("您没有要购买的商品");
        }
        // 把购物车集合转化成orderItemVo集合
        List<OrderItemVo> items = carts.stream().map(cart -> { // 只取购物车中skuId count
            OrderItemVo orderItemVo = new OrderItemVo();
            orderItemVo.setCount(cart.getCount());
            // 根据skuId查询sku
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(cart.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity != null){
                orderItemVo.setSkuId(skuEntity.getId());
                orderItemVo.setWeight(skuEntity.getWeight());
                orderItemVo.setPrice(skuEntity.getPrice());
                orderItemVo.setTitle(skuEntity.getTitle());
                orderItemVo.setDefaultImage(skuEntity.getDefaultImage());
            }

            // 查询营销信息
            ResponseVo<List<ItemSaleVo>> salesResponseVo = this.smsClient.queryItemSalesBySkuId(cart.getSkuId());
            List<ItemSaleVo> itemSaleVos = salesResponseVo.getData();
            orderItemVo.setSales(itemSaleVos);

            // 查询销售属性
            ResponseVo<List<SkuAttrValueEntity>> saleAttrResponseVo = this.pmsClient.querySkuAttrValuesBYSkuId(cart.getSkuId());
            List<SkuAttrValueEntity> skuAttrValueEntities = saleAttrResponseVo.getData();
            orderItemVo.setSaleAttrs(skuAttrValueEntities);

            // 查询库存信息
            ResponseVo<List<WareSkuEntity>> wareResponseVo = this.wmsClient.queryWareSkusBySkuId(cart.getSkuId());
            List<WareSkuEntity> wareSkuEntities = wareResponseVo.getData();
            if (!CollectionUtils.isEmpty(wareSkuEntities)){
                orderItemVo.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
            }
            return orderItemVo;
        }).collect(Collectors.toList());
        confirmVo.setItems(items);

        // 根据用户id查询用户信息
        ResponseVo<UserEntity> userEntityResponseVo = this.umsClient.queryUserById(userId);
        UserEntity userEntity = userEntityResponseVo.getData();
        if (userEntity != null) {
            confirmVo.setBounds(userEntity.getIntegration());
        }

        // 防重的唯一标识
        String orderToken = IdWorker.getIdStr();
        this.redisTemplate.opsForValue().set(KEY_PREFIX + orderToken, orderToken, 3, TimeUnit.HOURS);
        confirmVo.setOrderToken(orderToken);

        return confirmVo;
    }

    public void submit(OrderSubmitVo submitVo) {

        // 1.防重：页面中的orderToken 到redis中查询，查到了说明没有提交过，可以放行
        String orderToken = submitVo.getOrderToken(); // 页面的orderToken
        if (StringUtils.isBlank(orderToken)){
            throw new OrderException("非法提交。。。");
        }
        String script = "if(redis.call('get', KEYS[1]) == ARGV[1]) " +
                "then " +
                "   return redis.call('del', KEYS[1]) " +
                "else " +
                "   return 0 " +
                "end";
        Boolean flag = this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList(KEY_PREFIX + orderToken), orderToken);
        if (!flag){
            throw new OrderException("请不要重复提交。。。");
        }

        // 2.验总价：页面中的总价格  和 数据库中实时总价格
        List<OrderItemVo> items = submitVo.getItems();
        if (CollectionUtils.isEmpty(items)){
            throw new OrderException("您没有选中的商品!");
        }
        BigDecimal totalPrice = submitVo.getTotalPrice();// 页面总价格
        BigDecimal currentTotalPrice = items.stream().map(item -> {
            // 查询实时单价
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(item.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity == null) { // 如果skuEntity为空，返回小计0
                return new BigDecimal(0);
            }
            // 计算实时小计
            return skuEntity.getPrice().multiply(item.getCount());
        }).reduce((a, b) -> a.add(b)).get();
        if (totalPrice.compareTo(currentTotalPrice) != 0){
            throw new OrderException("页面已过期，请刷新后重试！");
        }

        // 3.验库存并锁定库存
        ResponseVo<List<SkuLockVo>> wareSkuResponseVo = this.wmsClient.checkLock(items.stream().map(item -> {
            SkuLockVo skuLockVo = new SkuLockVo();
            skuLockVo.setSkuId(item.getSkuId());
            skuLockVo.setCount(item.getCount().intValue());
            return skuLockVo;
        }).collect(Collectors.toList()), orderToken);
        List<SkuLockVo> skuLockVos = wareSkuResponseVo.getData();
        if (!CollectionUtils.isEmpty(skuLockVos)){
            throw new OrderException(JSON.toJSONString(skuLockVos));
        }

        // 4.创建订单
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();
        try {
            this.omsClient.saveOrder(submitVo, userId);
        } catch (Exception e) {
            this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "order.disable", orderToken);
            throw new OrderException("创建订单失败！");
        }

        // 5.删除购物车中对应商品的记录 使用mq异步删
        Map<String, Object> msg = new HashMap<>();
        msg.put("userId", userId);
        List<Long> skuIds = items.stream().map(OrderItemVo::getSkuId).collect(Collectors.toList());
        msg.put("skuIds", JSON.toJSONString(skuIds));
        this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "cart.delete", msg);
    }
}
