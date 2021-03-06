package com.atguigu.gmall.pms.service.impl;

import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.feign.GmallSmsClient;
import com.atguigu.gmall.pms.mapper.SkuMapper;
import com.atguigu.gmall.pms.mapper.SpuDescMapper;
import com.atguigu.gmall.pms.service.*;
import com.atguigu.gmall.pms.vo.SkuVo;
import com.atguigu.gmall.pms.vo.SpuAttrValueVo;
import com.atguigu.gmall.pms.vo.SpuVo;
import com.atguigu.gmall.sms.vo.SkuSaleVo;
import io.seata.spring.annotation.GlobalTransactional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.mapper.SpuMapper;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;


@Service("spuService")
public class SpuServiceImpl extends ServiceImpl<SpuMapper, SpuEntity> implements SpuService {

    @Autowired
    private SpuDescService descService;

    @Autowired
    private SpuAttrValueService spuAttrValueService;

    @Autowired
    private SkuMapper skuMapper;
    
    @Autowired
    private SkuImagesService skuImagesService;

    @Autowired
    private SkuAttrValueService skuAttrValueService;

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<SpuEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<SpuEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public PageResultVo querySpuByCidAndPage(PageParamVo paramVo, Long categoryId) {

        QueryWrapper<SpuEntity> wrapper = new QueryWrapper<>();

        // ??????categoryId??????0??????????????????
        if (categoryId != 0) {
            wrapper.eq("category_id", categoryId);
        }

        // ?????????
        String key = paramVo.getKey();
        if (StringUtils.isNotBlank(key)){
            // wrapper?????????????????????????????????????????????and??????????????????????????????
            wrapper.and(t -> t.eq("id", key).or().like("name", key));
        }

        IPage<SpuEntity> page = this.page(
                paramVo.getPage(),
                wrapper
        );

        return new PageResultVo(page);
    }

    @GlobalTransactional
    @Override
    public void bigSave(SpuVo spu) throws FileNotFoundException {

        // 1.??????spu?????????3??????
        // 1.1. ??????pms_spu
        Long spuId = saveSpuInfo(spu); // ?????????????????????

        // 1.2. ??????pms_spu_desc
        //this.saveSpuDesc(spu, spuId);  // ???????????????????????????
        this.descService.saveSpuDesc(spu, spuId);

//        try {
//            //int i = 1/0; // ?????????????????????????????????
//            //new FileInputStream("xxx"); // ??????????????????????????????
//            TimeUnit.SECONDS.sleep(4);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }

        // 1.3. ??????pms_spu_attr_value
        saveBaseAttrs(spu, spuId);

        // 2.??????sku?????????3??????
        saveSkuInfo(spu, spuId);

        //int i = 1/0;
        this.rabbitTemplate.convertAndSend("PMS_ITEM_EXCHANGE", "item.insert", spuId);
    }

    private void saveSkuInfo(SpuVo spu, Long spuId) {
        List<SkuVo> skus = spu.getSkus();
        if (CollectionUtils.isEmpty(skus)){
            return ;
        }
        // ??????sku????????????pms_sku
        skus.forEach(skuVo -> {
            // 2.1. ??????pms_sku
            skuVo.setSpuId(spuId);
            skuVo.setCategoryId(spu.getCategoryId());
            skuVo.setBrandId(spu.getBrandId());
            // ???????????????????????????
            List<String> images = skuVo.getImages();
            if (!CollectionUtils.isEmpty(images)){
                // ????????????????????????????????????
                skuVo.setDefaultImage(StringUtils.isBlank(skuVo.getDefaultImage()) ? images.get(0) : skuVo.getDefaultImage());
            }
            this.skuMapper.insert(skuVo);
            Long skuId = skuVo.getId();

            // 2.2. ??????pms_sku_images
            if (!CollectionUtils.isEmpty(images)){
                this.skuImagesService.saveBatch(images.stream().map(image -> {
                    SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                    skuImagesEntity.setSkuId(skuId);
                    skuImagesEntity.setUrl(image);
                    // ??????????????????????????????sku??????????????????????????????????????????1??????????????????0
                    skuImagesEntity.setDefaultStatus(StringUtils.equals(skuVo.getDefaultImage(), image) ? 1 : 0);
                    return skuImagesEntity;
                }).collect(Collectors.toList()));
            }

            // 2.3. ??????pms_sku_attr_value
            List<SkuAttrValueEntity> saleAttrs = skuVo.getSaleAttrs();
            if (!CollectionUtils.isEmpty(saleAttrs)){
                saleAttrs.forEach(skuAttrValueEntity -> {
                    skuAttrValueEntity.setSkuId(skuId);
                });
                this.skuAttrValueService.saveBatch(saleAttrs);
            }

            // 3.???????????????????????????3??????
            SkuSaleVo skuSaleVo = new SkuSaleVo();
            BeanUtils.copyProperties(skuVo, skuSaleVo);
            skuSaleVo.setSkuId(skuId);
            this.smsClient.saleSales(skuSaleVo);
        });
    }

    private void saveBaseAttrs(SpuVo spu, Long spuId) {
        List<SpuAttrValueVo> baseAttrs = spu.getBaseAttrs();
        if (!CollectionUtils.isEmpty(baseAttrs)){
            // ???SpuAttrValueVo???????????????SpuAttrValueEntity??????
            List<SpuAttrValueEntity> spuAttrValueEntities = baseAttrs.stream()
                    .filter(spuAttrValueVo -> spuAttrValueVo.getAttrValue() != null)
                    .map(spuAttrValueVo -> {
                        SpuAttrValueEntity spuAttrValueEntity = new SpuAttrValueEntity();
                        BeanUtils.copyProperties(spuAttrValueVo, spuAttrValueEntity);
                        spuAttrValueEntity.setSpuId(spuId);
                        return spuAttrValueEntity;
                    }).collect(Collectors.toList());
            this.spuAttrValueService.saveBatch(spuAttrValueEntities);
        }
    }

    private Long saveSpuInfo(SpuVo spu) {
        spu.setCreateTime(new Date());
        spu.setUpdateTime(spu.getCreateTime());
        this.save(spu);
        return spu.getId();
    }

//    public static void main(String[] args) {
//
//        List<User> users = Arrays.asList(
//                new User("liuyan", 20, false),
//                new User("marong", 21, false),
//                new User("xiaolu", 22, false),
//                new User("laowang", 23, true),
//                new User("xiaoliang", 24, true),
//                new User("zhengshuang", 25, false),
//                new User("pig", 26, true)
//        );
        // map??????????????????????????????????????????
//        users.stream().map(user -> user.getUsername()).collect(Collectors.toList()).forEach(System.out::println);
//        users.stream().map(user -> {
//            Person person = new Person();
//            person.setName(user.getUsername());
//            person.setAge(user.getAge());
//            return person;
//        }).collect(Collectors.toList()).forEach(System.out::println);

        // filter????????????????????????????????????????????????
//        users.stream().filter(user -> user.getAge() > 22).collect(Collectors.toList()).forEach(System.out::println);
//        users.stream().filter(User::getSex).collect(Collectors.toList()).forEach(System.out::println);

        // reduce?????????
//        List<Integer> arrs = Arrays.asList(21, 22, 23, 24, 25);
//        System.out.println(arrs.stream().reduce((a, b) -> a + b).get());
//
//        System.out.println(users.stream().map(User::getAge).reduce((a, b) -> a + b).get());
//
//    }

}

//@Data
//@AllArgsConstructor
//@NoArgsConstructor
//class User{
//    private String username;
//    private Integer age;
//    private Boolean sex;
//}
//
//@Data
//@ToString
//class Person{
//    private String name;
//    private Integer age;
//}