package com.atguigu.gmall.item.vo;

import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.pms.entity.SkuImagesEntity;
import com.atguigu.gmall.pms.vo.GroupVo;
import com.atguigu.gmall.pms.vo.SaleAttrValueVo;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class ItemVo {

    // 面包屑所需要的参数
    // 一二三级分类 V O
    private List<CategoryEntity> categories;
    // 品牌信息 V   O
    private Long brandId;
    private String brandName;
    // spu相关信息 V    O
    private Long spuId;
    private String spuName;

    // 中间详细信息 V O
    private Long skuId;
    private String title;
    private String subTitle;
    private BigDecimal price;
    private Integer weight;
    private String defaultImage;

    // 营销信息 V O
    private List<ItemSaleVo> sales;

    // 是否有货 V O
    private Boolean store = false;

    // sku的图片列表V O
    private List<SkuImagesEntity> images;

    // 销售属性列表 V  O
    // [{attrId: 3, attrName: '颜色', attrValues: ['白天白', '黑夜黑']},
    // {attrId: 4, attrName: '内存', attrValues: ['8G', '12G']},
    // {attrId: 5, attrName: '存储', attrValues: ['128G', '256G']}]
    private List<SaleAttrValueVo> saleAttrs;

    // 当前sku的销售属性：{3: '白天白', 4: '12G', 5: '256G'} V O
    private Map<Long, String> saleAttr;

    // 为了页面跳转，需要销售属性组合与skuId的映射关系 V O
    // {'白天白, 8G, 128G': 100, '白天白, 12G, 256G': 101}
    private String skuJsons;

    // 商品描述 V O
    private List<String> spuImages;

    // 规格参数分组列表
    private List<GroupVo> groups;
}
