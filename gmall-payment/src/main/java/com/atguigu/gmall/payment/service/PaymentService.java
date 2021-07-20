package com.atguigu.gmall.payment.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.payment.feign.GmallOmsClient;
import com.atguigu.gmall.payment.mapper.PaymentInfoMapper;
import com.atguigu.gmall.payment.pojo.PaymentInfoEntity;
import com.atguigu.gmall.payment.vo.PayAsyncVo;
import com.atguigu.gmall.payment.vo.PayVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;

@Service
public class PaymentService {

    @Autowired
    private GmallOmsClient omsClient;

    @Autowired
    private PaymentInfoMapper paymentInfoMapper;

    public OrderEntity queryOrderByToken(String orderToken){
        ResponseVo<OrderEntity> orderEntityResponseVo = this.omsClient.queryOrderByToken(orderToken);
        return orderEntityResponseVo.getData();
    }

    public Long savePaymentInfo(PayVo payVo) {
        PaymentInfoEntity paymentInfoEntity = new PaymentInfoEntity();
        paymentInfoEntity.setPaymentStatus(0);
        paymentInfoEntity.setTotalAmount(new BigDecimal(payVo.getTotal_amount()));
        paymentInfoEntity.setSubject(payVo.getSubject());
        paymentInfoEntity.setPaymentType(1);
        paymentInfoEntity.setOutTradeNo(payVo.getOut_trade_no());
        paymentInfoEntity.setCreateTime(new Date());
        this.paymentInfoMapper.insert(paymentInfoEntity);
        return paymentInfoEntity.getId();
    }

    public PaymentInfoEntity queryPaymentInfoById(String payId){
        return this.paymentInfoMapper.selectById(payId);
    }

    public int updatePaymentInfo(PayAsyncVo asyncVo) {
        PaymentInfoEntity paymentInfoEntity = this.queryPaymentInfoById(asyncVo.getPassback_params());
        paymentInfoEntity.setTradeNo(asyncVo.getTrade_no());
        paymentInfoEntity.setPaymentStatus(1);
        paymentInfoEntity.setCallbackTime(new Date());
        paymentInfoEntity.setCallbackContent(JSON.toJSONString(asyncVo));
        return this.paymentInfoMapper.updateById(paymentInfoEntity);
    }
}
