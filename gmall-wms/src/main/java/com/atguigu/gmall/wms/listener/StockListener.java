package com.atguigu.gmall.wms.listener;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.wms.mapper.WareSkuMapper;
import com.atguigu.gmall.wms.vo.SkuLockVo;
import com.rabbitmq.client.Channel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.List;

@Component
public class StockListener {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private WareSkuMapper wareSkuMapper;

    private static final String KEY_PREFIX = "stock:info:";

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue("STOCK_UNLOCK_QUEUE"),
            exchange = @Exchange(value = "ORDER_EXCHANGE", ignoreDeclarationExceptions = "true", type = ExchangeTypes.TOPIC),
            key = {"order.disable"}
    ))
    public void unlock(String orderToken, Channel channel, Message message) throws IOException {
        if (StringUtils.isBlank(orderToken)){
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            return;
        }

        // 查询库存锁定信息
        String json = this.redisTemplate.opsForValue().get(KEY_PREFIX + orderToken);
        // 如果库存锁定的缓存为空，直接确认消息
        if (StringUtils.isBlank(json)){
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            return ;
        }

        // 反序列化为库存锁定信息集合
        List<SkuLockVo> skuLockVos = JSON.parseArray(json, SkuLockVo.class);
        if (CollectionUtils.isEmpty(skuLockVos)){
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            return ;
        }

        // 遍历解锁库存
        skuLockVos.forEach(lockVo -> {
            this.wareSkuMapper.unlock(lockVo.getWareSkuId(), lockVo.getCount());
        });

        // 删除库存锁定的缓存信息
        this.redisTemplate.delete(KEY_PREFIX + orderToken);

        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }
}
