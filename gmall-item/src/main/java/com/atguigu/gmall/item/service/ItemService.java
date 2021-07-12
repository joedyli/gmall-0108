package com.atguigu.gmall.item.service;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.exception.ItemException;
import com.atguigu.gmall.item.feign.GmallPmsClient;
import com.atguigu.gmall.item.feign.GmallSmsClient;
import com.atguigu.gmall.item.feign.GmallWmsClient;
import com.atguigu.gmall.item.vo.ItemVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.vo.GroupVo;
import com.atguigu.gmall.pms.vo.SaleAttrValueVo;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class ItemService {

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private GmallSmsClient smsClient;

    public ItemVo loadData(Long skuId) {
        ItemVo itemVo = new ItemVo();

        //1.根据skuId查询sku V
        ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(skuId);
        SkuEntity skuEntity = skuEntityResponseVo.getData();
        if (skuEntity == null) {
            throw new ItemException("该skuId对应的商品不存在！");
        }
        itemVo.setSkuId(skuId);
        itemVo.setTitle(skuEntity.getTitle());
        itemVo.setSubTitle(skuEntity.getSubtitle());
        itemVo.setPrice(skuEntity.getPrice());
        itemVo.setDefaultImage(skuEntity.getDefaultImage());
        itemVo.setWeight(skuEntity.getWeight());

        //2.根据三级分类Id查询一二三级分类 V
        ResponseVo<List<CategoryEntity>> catesResponseVo = this.pmsClient.queryLvl123CategoriesByCid3(skuEntity.getCategoryId());
        List<CategoryEntity> categoryEntities = catesResponseVo.getData();
        itemVo.setCategories(categoryEntities);

        //3.根据品牌id查询品牌 V
        ResponseVo<BrandEntity> brandEntityResponseVo = this.pmsClient.queryBrandById(skuEntity.getBrandId());
        BrandEntity brandEntity = brandEntityResponseVo.getData();
        if (brandEntity != null) {
            itemVo.setBrandId(brandEntity.getId());
            itemVo.setBrandName(brandEntity.getName());
        }

        //4.根据spuId查询SPU V
        ResponseVo<SpuEntity> spuEntityResponseVo = this.pmsClient.querySpuById(skuEntity.getSpuId());
        SpuEntity spuEntity = spuEntityResponseVo.getData();
        if (spuEntity != null) {
            itemVo.setSpuId(spuEntity.getId());
            itemVo.setSpuName(spuEntity.getName());
        }

        //5.根据skuId查询营销信息 V
        ResponseVo<List<ItemSaleVo>> salesResponseVo = this.smsClient.queryItemSalesBySkuId(skuId);
        List<ItemSaleVo> itemSaleVos = salesResponseVo.getData();
        itemVo.setSales(itemSaleVos);

        //6.根据skuId查询库存列表 V
        ResponseVo<List<WareSkuEntity>> wareResponseVo = this.wmsClient.queryWareSkusBySkuId(skuId);
        List<WareSkuEntity> wareSkuEntities = wareResponseVo.getData();
        if (!CollectionUtils.isEmpty(wareSkuEntities)) {
            itemVo.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
        }

        // 7.根据skuId查询sku的图片列表  V
        ResponseVo<List<SkuImagesEntity>> imagesResponseVo = this.pmsClient.querySkuImagesBySkuId(skuId);
        List<SkuImagesEntity> skuImagesEntities = imagesResponseVo.getData();
        itemVo.setImages(skuImagesEntities);

        // 8.根据spuId查询spu下所有销售属性的可取值 V
        ResponseVo<List<SaleAttrValueVo>> saleAttrsResponseVo = this.pmsClient.querySaleAttrValuesBySpuId(skuEntity.getSpuId());
        List<SaleAttrValueVo> saleAttrValueVos = saleAttrsResponseVo.getData();
        itemVo.setSaleAttrs(saleAttrValueVos);

        // 9.根据skuId查询当前sku的销售属性 V  {3: '白天白', 4: '12G', 5: '256G'}
        ResponseVo<List<SkuAttrValueEntity>> saleAttrResponseVo = this.pmsClient.querySkuAttrValuesBYSkuId(skuId);
        List<SkuAttrValueEntity> skuAttrValueEntities = saleAttrResponseVo.getData();
        if (!CollectionUtils.isEmpty(skuAttrValueEntities)) {
            itemVo.setSaleAttr(skuAttrValueEntities.stream().collect(Collectors.toMap(SkuAttrValueEntity::getAttrId, SkuAttrValueEntity::getAttrValue)));
        }

        // 10.根据spuId所有销售属性组合和skuId的映射关系 V
        ResponseVo<String> stringResponseVo = this.pmsClient.queryMappingBySpuId(skuEntity.getSpuId());
        String json = stringResponseVo.getData();
        itemVo.setSkuJsons(json);

        // 11.根据spuId查询spu的描述信息 V
        ResponseVo<SpuDescEntity> spuDescEntityResponseVo = this.pmsClient.querySpuDescById(skuEntity.getSpuId());
        SpuDescEntity spuDescEntity = spuDescEntityResponseVo.getData();
        if (spuDescEntity != null) {
            itemVo.setSpuImages(Arrays.asList(StringUtils.split(spuDescEntity.getDecript(), ",")));
        }

        // 12.根据分类id、spuId、skuId查询出所有的规格参数组及组下的规格参数和值
        ResponseVo<List<GroupVo>> groupResponseVo = this.pmsClient.queryGroupsWithAttrValuesByCidAndSpuIdAndSkuId(skuEntity.getCategoryId(), skuEntity.getSpuId(), skuId);
        List<GroupVo> groupVos = groupResponseVo.getData();
        itemVo.setGroups(groupVos);

        return itemVo;
    }

    public static void main(String[] args) throws ExecutionException, InterruptedException, IOException {
        //new MyThread().start();
//        new Thread(() -> {
//            System.out.println("这是runnable接口的lambda表达式初始化多线程程序" + Thread.currentThread().getName());
//        }).start();

//        FutureTask<String> task = new FutureTask<>(() -> {
//            System.out.println("这是Callable接口实现多线程程序");
//            return "..dddfsfds..";
//        });
//        new Thread(task).start();
//        try {
//            System.out.println(task.get());
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        } catch (ExecutionException e) {
//            e.printStackTrace();
//        }
//        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(3, 5, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10));
//        threadPoolExecutor.execute(() -> {
//            System.out.println("通过线程池初始化多线程任务。。。。。" + Thread.currentThread().getName());
//        });
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            System.out.println("通过CompletableFuture的supplyAsync初始化了一个多线程程序");
            //int i = 1/0;
            return "hello CompletableFuture";
        }).whenCompleteAsync((t, u) -> {
            System.out.println("=================whenComplete====================");
            System.out.println("上一个任务的返回结果集t: " + t);
            System.out.println("上一个任务的异常信息u: " + u);
        }).exceptionally(t -> {
            System.out.println("======================exceptionally==========================");
            System.out.println("t: " + t);
            return null;
        });

        System.out.println("这是main线程" + Thread.currentThread().getName());
        System.in.read();
    }
}

class MyThread extends Thread {
    @Override
    public void run() {
        System.out.println("这是继承Thread类初始化多线程程序" + Thread.currentThread().getName());
    }
}

