package com.atguigu.gmall.payment.controller;

import com.alipay.api.AlipayApiException;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.exception.OrderException;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.payment.config.AlipayTemplate;
import com.atguigu.gmall.payment.interceptor.LoginInterceptor;
import com.atguigu.gmall.payment.pojo.PaymentInfoEntity;
import com.atguigu.gmall.payment.pojo.UserInfo;
import com.atguigu.gmall.payment.service.PaymentService;
import com.atguigu.gmall.payment.vo.PayAsyncVo;
import com.atguigu.gmall.payment.vo.PayVo;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RCountDownLatch;
import org.redisson.api.RLock;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Controller
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private AlipayTemplate alipayTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @GetMapping("pay.html")
    public String paySelect(@RequestParam("orderToken")String orderToken, Model model){

        // 如果支付订单不存在
        OrderEntity orderEntity = this.paymentService.queryOrderByToken(orderToken);
        if (orderEntity == null) {
            throw new OrderException("非法参数");
        }

        // 获取当前用户信息
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();

        // 订单状态如果不是待支付状态 或者 这个订单不属于当前用户
        if (orderEntity.getStatus() != 0 || orderEntity.getUserId() != userId) {
            throw new OrderException("非法参数");
        }

        model.addAttribute("orderEntity", orderEntity);

        return "pay";
    }

    /**
     * 接受用户的支付请求，跳转到支付页
     */
    @GetMapping("alipay.html")
    @ResponseBody // 代表以其他视图的形式展示方法的返回结果集
    public String alipay(@RequestParam("orderToken")String orderToken){
        // 如果支付订单不存在
        OrderEntity orderEntity = this.paymentService.queryOrderByToken(orderToken);
        if (orderEntity == null) {
            throw new OrderException("非法参数");
        }

        // 获取当前用户信息
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();

        // 订单状态如果不是待支付状态 或者 这个订单不属于当前用户
        if (orderEntity.getStatus() != 0 || orderEntity.getUserId() != userId) {
            throw new OrderException("非法参数");
        }

        try {
            // 调用支付宝支付接口，打开支付页面
            PayVo payVo = new PayVo();
            payVo.setOut_trade_no(orderToken);
            // 这里建议就写0.01
            payVo.setTotal_amount("0.01");
            payVo.setSubject("谷粒商城支付平台");
            payVo.getPassback_params();


            // 记录对账表
            Long payId = this.paymentService.savePaymentInfo(payVo);
            payVo.setPassback_params(payId.toString());

            String form = alipayTemplate.pay(payVo);

            return form;
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 支付宝同步回调接口：显示支付成功页面
     */
    @GetMapping("pay/success")
    public String paySuccess() {
        // TODO: 根据订单编号查询订单
        return "paysuccess";
    }

    /**
     * 支付宝异步回调接口：修改订单状态
     */
    @PostMapping("pay/ok")
    public Object payOk(PayAsyncVo asyncVo){
        // 1.验签 ：确保是支付宝发送的
        Boolean flag = this.alipayTemplate.checkSignature(asyncVo);
        if (!flag) {
            return "failure";
        }

        // 2.校验业务参数：确保是我们平台的订单
        // 异步通知中的参数
        String appId = asyncVo.getApp_id();
        String out_trade_no = asyncVo.getOut_trade_no();
        String total_amount = asyncVo.getTotal_amount();
        String payId = asyncVo.getPassback_params();
        // 根据对账记录表的id查询对账记录
        PaymentInfoEntity paymentInfoEntity = this.paymentService.queryPaymentInfoById(payId);
        if (paymentInfoEntity == null) {
            return "failure";
        }
        if (!StringUtils.equals(appId, alipayTemplate.getApp_id())
                || !StringUtils.equals(out_trade_no, paymentInfoEntity.getOutTradeNo())
                || new BigDecimal(total_amount).compareTo(paymentInfoEntity.getTotalAmount()) != 0
        ){
            return "failure";
        }

        // 3.校验支付状态：TRADE_SUCCESS
        if (!StringUtils.equals("TRADE_SUCCESS", asyncVo.getTrade_status())){
            return "failure";
        }

        // 4.更新对账表的数据：已支付 回到时间和内容
        if (this.paymentService.updatePaymentInfo(asyncVo) == 1) {
            // 5.发送消息给mq，更新订单状态为待发货，并减库存
            this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "order.pay", out_trade_no);
        }

        return "success";
    }

    @GetMapping("seckill/{skuId}")
    public ResponseVo<Object> seckill(@PathVariable("skuId")Long skuId) throws InterruptedException {
        RSemaphore semaphore = this.redissonClient.getSemaphore("seckill:semaphore:" + skuId);
        semaphore.trySetPermits(10);
        semaphore.acquire();
        // 分布式锁
        RLock lock = this.redissonClient.getLock("sec:kill:" + skuId);
        lock.lock();

        UserInfo userInfo = LoginInterceptor.getUserInfo();

        String stock = this.redisTemplate.opsForValue().get("seckill:stock:" + skuId);
        if (StringUtils.isBlank(stock)){
            throw new OrderException("秒杀不存在！");
        }
        int st = Integer.parseInt(stock);
        if (st <= 0) {
            return ResponseVo.fail("手慢了，下次快点！");
        }
        // 减库存
        this.redisTemplate.opsForValue().decrement("seckill:stock:" + skuId);

        String orderToken = IdWorker.getIdStr();
        Map<String, Object> msg = new HashMap<>();
        msg.put("orderToken", orderToken);
        msg.put("userId", userInfo.getUserId());
        msg.put("skuId", skuId);
        this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "seckill.success", msg);

        RCountDownLatch countDownLatch = this.redissonClient.getCountDownLatch("seckill:order:" + orderToken);
        countDownLatch.trySetCount(1);

        lock.unlock();

        semaphore.release();
        return ResponseVo.ok(orderToken);
    }

    @GetMapping("seckill/{orderToken}")
    public String queryOrder(@PathVariable("orderToken")String orderToken, Model model) throws InterruptedException {
        RCountDownLatch countDownLatch = this.redissonClient.getCountDownLatch("seckill:order:" + orderToken);
        countDownLatch.await();
        // 查询订单
        OrderEntity orderEntity = this.paymentService.queryOrderByToken(orderToken);
        model.addAttribute("orderToken", orderEntity);
        return "xx";
    }
}
