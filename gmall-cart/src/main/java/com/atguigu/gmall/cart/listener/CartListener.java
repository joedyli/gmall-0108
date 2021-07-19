package com.atguigu.gmall.cart.listener;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.feign.GmallPmsClient;
import com.atguigu.gmall.cart.mapper.CartMapper;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.rabbitmq.client.Channel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class CartListener {

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CartMapper cartMapper;

    private static final String PRICE_PREFIX = "cart:price:";
    private static final String KEY_PREFIX = "cart:info:";

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue("CART_PRICE_QUEUE"),
            exchange = @Exchange(value = "PMS_ITEM_EXCHANGE", ignoreDeclarationExceptions = "true", type = ExchangeTypes.TOPIC),
            key = {"item.update"}
    ))
    public void listen(Long spuId, Channel channel, Message message) throws IOException {
        // 如果spuId为空，说明是垃圾消息
        if (spuId == null){
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            return ;
        }

        // 如果spuId下的sku为空，说明也是垃圾消息
        ResponseVo<List<SkuEntity>> spuResponseVo = this.pmsClient.querySkusBySpuId(spuId);
        List<SkuEntity> skuEntities = spuResponseVo.getData();
        if (CollectionUtils.isEmpty(skuEntities)){
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            return ;
        }

        // 如果spu下的sku不为空，遍历sku同步实时价格缓存
        skuEntities.forEach(skuEntity -> {
            this.redisTemplate.opsForValue().setIfPresent(PRICE_PREFIX + skuEntity.getId(), skuEntity.getPrice().toString());
        });

        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue("CART_DELETE_QUEUE"),
            exchange = @Exchange(value = "ORDER_EXCHANGE", ignoreDeclarationExceptions = "true", type = ExchangeTypes.TOPIC),
            key = {"cart.delete"}
    ))
    public void delete(Map<String, Object> msg, Channel channel, Message message) throws IOException {
        // 如果msg为空，说明是垃圾消息
        if (CollectionUtils.isEmpty(msg)){
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            return ;
        }

        // userId或者skuIds为空，说明垃圾消息
        String userId = msg.get("userId").toString();
        String skuIdsJson = msg.get("skuIds").toString();
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(skuIdsJson)){
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            return ;
        }

        // 删除redis中购物车
        List<String> skuIds = JSON.parseArray(skuIdsJson, String.class);
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        hashOps.delete(skuIds.toArray());
        // 删除mysql中的购物车
        this.cartMapper.delete(new UpdateWrapper<Cart>().eq("user_id", userId).in("sku_id", skuIds));

        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }
}
