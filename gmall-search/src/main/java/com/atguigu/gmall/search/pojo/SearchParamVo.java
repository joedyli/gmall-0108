package com.atguigu.gmall.search.pojo;

import lombok.Data;

import java.util.List;

/**
 * search.gmall.com/search?keyword=手机&brandId=1,2,3&categoryId=225,250&props=4:8G-12G&props=5:128G-256G
 *      &priceFrom=1000&priceTo=5000&store=true&sort=1&pageNum=2
 */
@Data
public class SearchParamVo {

    // 查询关键字
    private String keyword;

    // 品牌的过滤条件
    private List<Long> brandId;

    // 分类过滤条件
    private List<Long> categoryId;

    // 规格参数的过滤条件：["4:8G-12G", "5:128G-256G"]
    private List<String> props;

    // 价格区间过滤
    private Double priceFrom;
    private Double priceTo;

    // 仅显示有货
    private Boolean store;

    // 排序： 0-默认排序 1-价格降序 2-价格升序 3-销量的降序 4-新品降序
    private Integer sort = 0;

    private Integer pageNum = 1; // 页码
    private final Integer pageSize = 20;

}
