package com.atguigu.gmall.wms.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.wms.vo.SkuLockVo;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.wms.mapper.WareSkuMapper;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.atguigu.gmall.wms.service.WareSkuService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;


@Service("wareSkuService")
public class WareSkuServiceImpl extends ServiceImpl<WareSkuMapper, WareSkuEntity> implements WareSkuService {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private WareSkuMapper wareSkuMapper;

    private static final String LOCK_PREFIX = "stock:lock:";
    private static final String KEY_PREFIX = "stock:info:";

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<WareSkuEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<WareSkuEntity>()
        );

        return new PageResultVo(page);
    }

    @Transactional
    @Override
    public List<SkuLockVo> checkLock(List<SkuLockVo> lockVos, String orderToken) {

        // 遍历所有商品验库存并锁库存
        lockVos.forEach(lockVo -> {
            checkAndLock(lockVo);
        });

        // 判断是否存在锁定失败的记录，如果存在锁定失败的记录，则把所有锁定成功的库存释放掉
        if (lockVos.stream().anyMatch(lockVo -> !lockVo.getLock())){
            // 获取所有锁定成功的记录
            lockVos.stream().filter(SkuLockVo::getLock).collect(Collectors.toList()).forEach(lockVo -> {
                // 解锁对应的库存
                this.wareSkuMapper.unlock(lockVo.getWareSkuId(), lockVo.getCount());
            });
            // 并返回锁定信息
            return lockVos;
        }

        // 为了方便将来减库存 或者 解锁库存 ，需要把锁定信息缓存下来
        this.redisTemplate.opsForValue().set(KEY_PREFIX + orderToken, JSON.toJSONString(lockVos));

        // 如果都锁定成功的情况下，返回null
        return null;
    }

    private void checkAndLock(SkuLockVo lockVo){
        RLock lock = this.redissonClient.getLock(LOCK_PREFIX + lockVo.getSkuId());
        lock.lock();

        try {
            // 验库存：查询满足库存数的库存列表
            List<WareSkuEntity> wareSkuEntities = this.wareSkuMapper.check(lockVo.getSkuId(), lockVo.getCount());
            if (CollectionUtils.isEmpty(wareSkuEntities)){ // 如果返回值为空，说明验库存并锁库库存失败
                lockVo.setLock(false);
                return;
            }

            // 锁库存：更新库存锁定数，这里咱们取第一个仓库发货（大数据分析就近发货）
            WareSkuEntity wareSkuEntity = wareSkuEntities.get(0);
            if (this.wareSkuMapper.lock(wareSkuEntity.getId(), lockVo.getCount()) == 1) {
                lockVo.setLock(true);
                lockVo.setWareSkuId(wareSkuEntity.getId());
            }
        } finally {
            lock.unlock();
        }
    }

}