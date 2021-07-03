package com.atguigu.gmall.search.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.pms.entity.BrandEntity;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.search.pojo.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.range.Range;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SearchService {

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    public SearchResponseVo search(SearchParamVo paramVo) {

        try {
            SearchResponse response = this.restHighLevelClient.search(new SearchRequest(new String[]{"goods"}, buildDsl(paramVo)), RequestOptions.DEFAULT);

            System.out.println(response);

            // 分页参数，在请求参数中
            SearchResponseVo responseVo = this.parseResult(response);
            responseVo.setPageNum(paramVo.getPageNum());
            responseVo.setPageSize(paramVo.getPageSize());

            return responseVo;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private SearchResponseVo parseResult(SearchResponse response){
        SearchResponseVo responseVo = new SearchResponseVo();

        // 获取hits结果集
        SearchHits hits = response.getHits();
        responseVo.setTotal(hits.getTotalHits());
        SearchHit[] hitsHits = hits.getHits();
        List<Goods> goodsList = Arrays.stream(hitsHits).map(hitsHit -> {
            String json = hitsHit.getSourceAsString(); // 获取_source
            Goods goods = JSON.parseObject(json, Goods.class); // 反序列化为goods对象
            // 获取高亮结果集，覆盖普通的标题
            Map<String, HighlightField> highlightFields = hitsHit.getHighlightFields();
            HighlightField highlightField = highlightFields.get("title");
            goods.setTitle(highlightField.fragments()[0].string());

            return goods;
        }).collect(Collectors.toList());
        responseVo.setGoodsList(goodsList);

        // 获取聚合结果集
        Aggregations aggregations = response.getAggregations();
        // 获取聚合结果集中的品牌id聚合
        ParsedLongTerms brandIdAgg = (ParsedLongTerms)aggregations.get("brandIdAgg");
        List<? extends Terms.Bucket> brandBuckets = brandIdAgg.getBuckets(); // 获取品牌id聚合结果集中的桶
        if (!CollectionUtils.isEmpty(brandBuckets)){
            List<BrandEntity> brandEntities = brandBuckets.stream().map(bucket -> { // 把每个桶转化成品牌
                BrandEntity brandEntity = new BrandEntity();
                brandEntity.setId(((Terms.Bucket) bucket).getKeyAsNumber().longValue()); // 桶中的key就是品牌id

                // 获取品牌id聚合中子聚合
                Aggregations subBrandAggs = ((Terms.Bucket) bucket).getAggregations();
                // 获取品牌名称的子聚合
                ParsedStringTerms brandNameAgg = (ParsedStringTerms)subBrandAggs.get("brandNameAgg");
                List<? extends Terms.Bucket> buckets = brandNameAgg.getBuckets();
                if (!CollectionUtils.isEmpty(buckets)){
                    brandEntity.setName(buckets.get(0).getKeyAsString());
                }

                // 获取品牌id聚合中的logo子聚合
                ParsedStringTerms logoAgg = (ParsedStringTerms)subBrandAggs.get("logoAgg");
                List<? extends Terms.Bucket> logoAggBuckets = logoAgg.getBuckets();
                if (!CollectionUtils.isEmpty(logoAggBuckets)){
                    brandEntity.setLogo(logoAggBuckets.get(0).getKeyAsString());
                }

                return brandEntity;
            }).collect(Collectors.toList());
            responseVo.setBrands(brandEntities);
        }

        // 获取分类的聚合，转化成分类列表
        ParsedLongTerms categoryIdAgg = (ParsedLongTerms)aggregations.get("categoryIdAgg");
        List<? extends Terms.Bucket> categoryBuckets = categoryIdAgg.getBuckets(); // 获取分类Id聚合中的桶
        if (!CollectionUtils.isEmpty(categoryBuckets)){
            // 分类id中的桶转化成分类集合
            List<CategoryEntity> categoryEntities = categoryBuckets.stream().map(bucket -> {
                CategoryEntity categoryEntity = new CategoryEntity();
                // 分类id聚合中桶的key就是分类id
                categoryEntity.setId(((Terms.Bucket) bucket).getKeyAsNumber().longValue());
                // 获取分类名称的子聚合
                ParsedStringTerms categoryNameAgg = (ParsedStringTerms)((Terms.Bucket) bucket).getAggregations().get("categoryNameAgg");
                List<? extends Terms.Bucket> buckets = categoryNameAgg.getBuckets();
                if (!CollectionUtils.isEmpty(buckets)){
                    categoryEntity.setName(buckets.get(0).getKeyAsString());
                }

                return categoryEntity;
            }).collect(Collectors.toList());
            responseVo.setCategories(categoryEntities);
        }

        // 获取规格参数的聚合，解析出规格参数列表
        ParsedNested attrAgg = (ParsedNested)aggregations.get("attrAgg"); // 获取规格参数的嵌套聚合
        // 获取嵌套聚合中的规格参数id的子聚合
        ParsedLongTerms attrIdAgg = (ParsedLongTerms)attrAgg.getAggregations().get("attrIdAgg");
        // 获取规格参数id聚合中的桶
        List<? extends Terms.Bucket> attrIdBuckets = attrIdAgg.getBuckets();
        // 把attrId的桶集合转化成SearchResponseAttrValueVo集合
        if (!CollectionUtils.isEmpty(attrIdBuckets)){
            List<SearchResponseAttrValueVo> searchResponseAttrValueVos = attrIdBuckets.stream().map(bucket -> {
                SearchResponseAttrValueVo attrValueVo = new SearchResponseAttrValueVo();
                // 桶中的key就是attrId
                attrValueVo.setAttrId(((Terms.Bucket) bucket).getKeyAsNumber().longValue());

                // 获取attrId聚合中的子聚合
                Aggregations aggs = ((Terms.Bucket) bucket).getAggregations();

                // 获取子聚合中的attrNameAgg
                ParsedStringTerms attrNameAgg = (ParsedStringTerms)aggs.get("attrNameAgg");
                // 获取attrNameAgg中的桶
                List<? extends Terms.Bucket> buckets = attrNameAgg.getBuckets();
                if (!CollectionUtils.isEmpty(buckets)){
                    // attrNameAgg中的桶，有且仅有一个元素
                    attrValueVo.setAttrName(buckets.get(0).getKeyAsString());
                }

                // 获取子聚合中的attrValueAgg
                ParsedStringTerms attrValueAgg = (ParsedStringTerms)aggs.get("attrValueAgg");
                List<? extends Terms.Bucket> valueBuckets = attrValueAgg.getBuckets();
                if (!CollectionUtils.isEmpty(valueBuckets)){
                    List<String> attrValues = valueBuckets.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList());
                    attrValueVo.setAttrValues(attrValues);
                }
                return attrValueVo;
            }).collect(Collectors.toList());
            responseVo.setFilters(searchResponseAttrValueVos);
        }

        return responseVo;
    }

    private SearchSourceBuilder buildDsl(SearchParamVo paramVo){
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        String keyword = paramVo.getKeyword();
        if (StringUtils.isBlank(keyword)){
            // TODO：打广告
            return sourceBuilder;
        }

        // 1.构建查询及过滤
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        sourceBuilder.query(boolQueryBuilder);
        // 1.1. 匹配查询
        boolQueryBuilder.must(QueryBuilders.matchQuery("title", keyword).operator(Operator.AND));

        // 1.2. 过滤
        // 1.2.1. 品牌过滤
        List<Long> brandId = paramVo.getBrandId(); // 品牌过滤条件
        if (!CollectionUtils.isEmpty(brandId)){
            boolQueryBuilder.filter(QueryBuilders.termsQuery("brandId", brandId));
        }

        // 1.2.2. 分类过滤
        List<Long> categoryId = paramVo.getCategoryId();//分类的过滤条件
        if (!CollectionUtils.isEmpty(categoryId)){
            boolQueryBuilder.filter(QueryBuilders.termsQuery("categoryId", categoryId));
        }

        // 1.2.3. 价格区间过滤
        Double priceFrom = paramVo.getPriceFrom();
        Double priceTo = paramVo.getPriceTo();
        if (priceFrom != null || priceTo != null){
            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("price"); // 构建范围查询
            boolQueryBuilder.filter(rangeQuery);

            if (priceFrom != null) {
                rangeQuery.gte(priceFrom);
            }
            if (priceTo != null) {
                rangeQuery.lte(priceTo);
            }
        }

        // 1.2.4. 仅显示有货过滤
        Boolean store = paramVo.getStore();
        if (store != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("store", store));
        }

        // 1.2.5. 规格参数过滤
        List<String> props = paramVo.getProps();
        if (!CollectionUtils.isEmpty(props)){
            props.forEach(prop -> {  // 4:8G-12G
                // 对规格参数过滤的条件字符串进行截取，获取attrId 和 attrValue
                String[] attr = StringUtils.split(prop, ":");
                if (attr != null && attr.length == 2){ // 分割后的数组长度必须是2时，才去处理
                    // 每一个规格参数过滤条件中的元素对应一个嵌套查询
                    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                    boolQueryBuilder.filter(QueryBuilders.nestedQuery("searchAttrs", boolQuery, ScoreMode.None));

                    // bool查询中有两个查询，他们之间是must关系
                    boolQuery.must(QueryBuilders.termQuery("searchAttrs.attrId", attr[0]));
                    boolQuery.must(QueryBuilders.termsQuery("searchAttrs.attrValue", StringUtils.split(attr[1], "-")));
                }
            });
        }

        // 2.构建排序
        Integer sort = paramVo.getSort();
        switch (sort){
            case 0: sourceBuilder.sort("_score", SortOrder.DESC); break;
            case 1: sourceBuilder.sort("price", SortOrder.DESC); break;
            case 2: sourceBuilder.sort("price", SortOrder.ASC); break;
            case 3: sourceBuilder.sort("sales", SortOrder.DESC); break;
            case 4: sourceBuilder.sort("createTime", SortOrder.DESC); break;
            default:
                throw new RuntimeException("您的搜索条件不合法！");
        }

        // 3.分页
        Integer pageNum = paramVo.getPageNum();
        Integer pageSize = paramVo.getPageSize();
        sourceBuilder.from((pageNum - 1) * pageSize);
        sourceBuilder.size(pageSize);

        // 4.高亮
        sourceBuilder.highlighter(
                new HighlightBuilder()
                        .field("title")
                        .preTags("<font style='color:red;'>")
                        .postTags("</font>")
        );

        // 5.聚合
        // 5.1. 品牌聚合
        sourceBuilder.aggregation(
                AggregationBuilders.terms("brandIdAgg").field("brandId")
                        .subAggregation(AggregationBuilders.terms("brandNameAgg").field("brandName"))
                        .subAggregation(AggregationBuilders.terms("logoAgg").field("logo"))
        );

        // 5.2. 分类聚合
        sourceBuilder.aggregation(
                AggregationBuilders.terms("categoryIdAgg").field("categoryId")
                        .subAggregation(AggregationBuilders.terms("categoryNameAgg").field("categoryName"))
        );

        // 5.3. 规格参数聚合
        sourceBuilder.aggregation(AggregationBuilders.nested("attrAgg", "searchAttrs")
                .subAggregation(AggregationBuilders.terms("attrIdAgg").field("searchAttrs.attrId")
                        .subAggregation(AggregationBuilders.terms("attrNameAgg").field("searchAttrs.attrName"))
                        .subAggregation(AggregationBuilders.terms("attrValueAgg").field("searchAttrs.attrValue"))
                )
        );

        // 6.结果集过滤
        sourceBuilder.fetchSource(new String[]{"skuId", "title", "subTitle", "price", "defaultImage"}, null);

        System.out.println(sourceBuilder);
        return sourceBuilder;
    }
}
