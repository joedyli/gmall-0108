package com.atguigu.gmall.pms.service.impl;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.mapper.AttrMapper;
import com.atguigu.gmall.pms.entity.AttrEntity;
import com.atguigu.gmall.pms.service.AttrService;


@Service("attrService")
public class AttrServiceImpl extends ServiceImpl<AttrMapper, AttrEntity> implements AttrService {

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<AttrEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<AttrEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public List<AttrEntity> queryAttrsByCidOrTypeOrSearchType(Long cid, Integer type, Integer searchType) {

        QueryWrapper<AttrEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("category_id", cid);

        // 判断type是否为空，不为空才需要添加该条件
        if (type != null) {
            wrapper.eq("type", type);
        }

        // 判断searchType是否为空，不为空
        if (searchType != null) {
            wrapper.eq("search_type", searchType);
        }

        return this.list(wrapper);
    }

}