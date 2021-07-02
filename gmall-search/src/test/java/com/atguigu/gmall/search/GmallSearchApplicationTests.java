package com.atguigu.gmall.search;

import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.search.feign.GmallPmsClient;
import com.atguigu.gmall.search.feign.GmallWmsClient;
import com.atguigu.gmall.search.pojo.Goods;
import com.atguigu.gmall.search.pojo.SearchAttrValueVo;
import com.atguigu.gmall.search.repository.GoodsRepository;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest
class GmallSearchApplicationTests {

    @Autowired
    private ElasticsearchRestTemplate restTemplate;

    @Autowired
    private GmallPmsClient pmsClient;
    
    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private GoodsRepository goodsRepository;

    @Test
    void contextLoads() {

        if (!this.restTemplate.indexExists(Goods.class)){
            this.restTemplate.createIndex(Goods.class);
            this.restTemplate.putMapping(Goods.class);
        }

        Integer pageNum = 1;
        Integer pageSize = 100;

        do {
            // 分批查询spu
            PageParamVo pageParamVo = new PageParamVo(pageNum, pageSize, null);
            ResponseVo<List<SpuEntity>> responseVo = this.pmsClient.querySpuByPageJson(pageParamVo);
            List<SpuEntity> spuEntities = responseVo.getData();
            if (CollectionUtils.isEmpty(spuEntities)){ // 如果spu的数量是100的整数倍，最后一次查询结果为空
                return;
            }

            // 遍历spu查询spu下所有的sku
            spuEntities.forEach(spuEntity -> {
                ResponseVo<List<SkuEntity>> skuResponseVo = this.pmsClient.querySkusBySpuId(spuEntity.getId());
                List<SkuEntity> skuEntities = skuResponseVo.getData();
                if (!CollectionUtils.isEmpty(skuEntities)){

                    // 查询品牌和分类，由于同一个spu品牌和分类是一样的，在spu的遍历中查询品牌和分类
                    ResponseVo<BrandEntity> brandEntityResponseVo = this.pmsClient.queryBrandById(spuEntity.getBrandId());
                    BrandEntity brandEntity = brandEntityResponseVo.getData();
                    ResponseVo<CategoryEntity> categoryEntityResponseVo = this.pmsClient.queryCategoryById(spuEntity.getCategoryId());
                    CategoryEntity categoryEntity = categoryEntityResponseVo.getData();

                    // 把spu下所有的sku转化成goods，导入到es
                    List<Goods> goodsList = skuEntities.stream().map(skuEntity -> {
                        Goods goods = new Goods();

                        // sku相关信息
                        goods.setSkuId(skuEntity.getId());
                        goods.setDefaultImage(skuEntity.getDefaultImage());
                        goods.setTitle(skuEntity.getTitle());
                        goods.setSubTitle(skuEntity.getSubtitle());
                        goods.setPrice(skuEntity.getPrice().doubleValue());

                        // 创建时间
                        goods.setCreateTime(spuEntity.getCreateTime());

                        // 销量和库存
                        ResponseVo<List<WareSkuEntity>> wareResponseVo = this.wmsClient.queryWareSkusBySkuId(skuEntity.getId());
                        List<WareSkuEntity> wareSkuEntities = wareResponseVo.getData();
                        if (!CollectionUtils.isEmpty(wareSkuEntities)){
                            goods.setSales(wareSkuEntities.stream().mapToLong(WareSkuEntity::getSales).reduce((a, b) -> a + b).getAsLong());
                            goods.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
                        }

                        // 品牌
                        if (brandEntity != null) {
                            goods.setBrandId(brandEntity.getId());
                            goods.setBrandName(brandEntity.getName());
                            goods.setLogo(brandEntity.getLogo());
                        }

                        // 分类
                        if (categoryEntity != null) {
                            goods.setCategoryId(categoryEntity.getId());
                            goods.setCategoryName(categoryEntity.getName());
                        }

                        // 检索类型的规格参数
                        List<SearchAttrValueVo> attrValueVos = new ArrayList<>();
                        // sku中的检索类型的规格参数
                        ResponseVo<List<SkuAttrValueEntity>> saleAttrResponseVo = this.pmsClient.querySearchAttrValuesBySkuId(skuEntity.getCategoryId(), skuEntity.getId());
                        List<SkuAttrValueEntity> skuAttrValueEntities = saleAttrResponseVo.getData();
                        if (!CollectionUtils.isEmpty(skuAttrValueEntities)){
                            attrValueVos.addAll(skuAttrValueEntities.stream().map(skuAttrValueEntity -> {
                                SearchAttrValueVo attrValueVo = new SearchAttrValueVo();
                                BeanUtils.copyProperties(skuAttrValueEntity, attrValueVo);
                                return attrValueVo;
                            }).collect(Collectors.toList()));
                        }

                        // spu中检索类型的规格参数
                        ResponseVo<List<SpuAttrValueEntity>> baseAttrResponseVo = this.pmsClient.querySearchAttrValuesBySpuId(skuEntity.getCategoryId(), spuEntity.getId());
                        List<SpuAttrValueEntity> spuAttrValueEntities = baseAttrResponseVo.getData();
                        if (!CollectionUtils.isEmpty(spuAttrValueEntities)){
                            attrValueVos.addAll(spuAttrValueEntities.stream().map(spuAttrValueEntity -> {
                                SearchAttrValueVo attrValueVo = new SearchAttrValueVo();
                                BeanUtils.copyProperties(spuAttrValueEntity, attrValueVo);
                                return attrValueVo;
                            }).collect(Collectors.toList()));
                        }
                        goods.setSearchAttrs(attrValueVos);

                        return goods;
                    }).collect(Collectors.toList());

                    this.goodsRepository.saveAll(goodsList);
                }
            });

            pageSize = spuEntities.size();
            pageNum++;
        } while (pageSize == 100);
    }

}