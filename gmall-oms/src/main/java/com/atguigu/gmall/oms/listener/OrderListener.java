package com.atguigu.gmall.oms.listener;

import com.atguigu.gmall.oms.mapper.OrderMapper;
import com.rabbitmq.client.Channel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;


@Component
public class OrderListener {

    @Autowired
    private OrderMapper orderMapper;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue("OMS_DISABLE_QUEUE"),
            exchange = @Exchange(value = "ORDER_EXCHANGE", ignoreDeclarationExceptions = "true", type = ExchangeTypes.TOPIC),
            key = {"order.disable"}
    ))
    public void listener(String orderToken, Channel channel, Message message) throws IOException {
        if (StringUtils.isBlank(orderToken)){
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            return;
        }

        // 标记订单为无效订单
        this.orderMapper.updateStatus(orderToken, 0, 5);

        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }
}
