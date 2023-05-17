package com.takeshi.mybatisplus;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.takeshi.pojo.basic.*;
import com.takeshi.pojo.bo.RetBO;
import com.takeshi.util.TakeshiUtil;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 扩展的mybatis-plus service 层接口<br/>
 * <pre>{@code
 * 自定义多表关联分页查询，在mapper层新建一个方法
 * //示例：
 * //@Select("select ${ew.sqlSelect} from tableName t1 left join tableName t2 on t1.t1_id = t2.t1_id ${ew.customSqlSegment}")
 * Page<T> pageList(Page<T> page, @Param(Constants.WRAPPER) Wrapper<T> queryWrapper);
 * }
 * </pre>
 *
 * @author 七濑武【Nanase Takeshi】
 */
public interface ITakeshiService<T> extends IService<T> {

    /**
     * 构建一个有排序的分页对象
     *
     * @param basicPage basicPage
     * @param <E>       e
     * @return Page
     */
    default <E extends BasicPage> Page<T> buildPage(E basicPage) {
        Page<T> page = Page.of(basicPage.getPageNum(), basicPage.getPageSize());
        if (basicPage instanceof BasicSortPage basicSortPage) {
            if (StrUtil.isNotBlank(basicSortPage.getSortColumn())) {
                page.addOrder(new OrderItem(basicSortPage.getSortColumn(), BooleanUtil.isTrue(basicSortPage.getSortAsc())));
            }
        }
        return page;
    }

    /**
     * 构建一个有排序的分页对象
     *
     * @param basicPage basicPage
     * @param <E>       p
     * @return Page
     */
    default <E extends BasicPage> TakeshiPage<T> buildTakeshiPage(E basicPage) {
        TakeshiPage<T> page = TakeshiPage.of(basicPage.getPageNum(), basicPage.getPageSize());
        if (basicPage instanceof BasicSortPage basicSortPage) {
            if (StrUtil.isNotBlank(basicSortPage.getSortColumn())) {
                page.addOrder(new OrderItem(basicSortPage.getSortColumn(), BooleanUtil.isTrue(basicSortPage.getSortAsc())));
            }
        }
        return page;
    }

    /**
     * 扩展的mybatis-plus分页接口
     * <p>通用的列表分页查询接口</p>
     * <p>columns 示例："user_name"</p>
     *
     * @param basicPage 列表查询参数
     * @param <E>       e
     * @return Page
     */
    default <E extends BasicPage> Page<T> listPage(E basicPage) {
        return this.listPage(basicPage, Collections.emptyList());
    }

    /**
     * 扩展的mybatis-plus分页接口
     * <p>通用的列表分页查询接口</p>
     * <p>columns 示例："user_name"</p>
     *
     * @param basicPage 列表查询参数
     * @param <E>       e
     * @return Page
     */
    default <E extends BasicPage> TakeshiPage<T> listTakeshiPage(E basicPage) {
        return this.listTakeshiPage(basicPage, Collections.emptyList());
    }

    /**
     * 扩展的mybatis-plus分页接口
     * <p>通用的列表分页查询接口</p>
     * <p>columns 示例："user_name"</p>
     *
     * @param basicPage 列表查询参数
     * @param columns   需要进行模糊搜索的数据库字段名
     * @param <E>       e
     * @return Page
     */
    default <E extends BasicPage> Page<T> listPage(E basicPage, List<SFunction<T, ?>> columns) {
        return this.getBaseMapper().selectPage(this.buildPage(basicPage), this.queryWrapper(basicPage, columns));
    }

    /**
     * 扩展的mybatis-plus分页接口
     * <p>通用的列表分页查询接口</p>
     * <p>columns 示例："user_name"</p>
     *
     * @param basicPage 列表查询参数
     * @param columns   需要进行模糊搜索的数据库字段名
     * @param <E>       e
     * @return Page
     */
    default <E extends BasicPage> TakeshiPage<T> listTakeshiPage(E basicPage, List<SFunction<T, ?>> columns) {
        return this.getBaseMapper().selectPage(this.buildTakeshiPage(basicPage), this.queryWrapper(basicPage, columns));
    }

    /**
     * 构建一个QueryWrapper
     *
     * @param basicPage 列表查询参数
     * @param columns   需要进行模糊搜索的数据库字段名
     * @param <E>       e
     * @return QueryWrapper
     */
    private <E extends BasicPage> QueryWrapper<T> queryWrapper(E basicPage, List<SFunction<T, ?>> columns) {
        QueryWrapper<T> queryWrapper = new QueryWrapper<>();
        String keyword = null;
        Long startTime = null, endTime = null;
        if (basicPage instanceof BasicSortQuery basicSortQuery) {
            keyword = basicSortQuery.getKeyword();
            startTime = basicSortQuery.getStartTime();
            endTime = basicSortQuery.getEndTime();
        } else if (basicPage instanceof BasicQuery basicQuery) {
            keyword = basicQuery.getKeyword();
            startTime = basicQuery.getStartTime();
            endTime = basicQuery.getEndTime();
        }
        String createTime = TakeshiUtil.getColumnName(AbstractBasicEntity::getCreateTime);
        queryWrapper.ge(ObjUtil.isNotNull(startTime), createTime, startTime)
                .le(ObjUtil.isNotNull(endTime), createTime, endTime);
        String sql = "CONCAT_WS(' '," + columns.stream().map(TakeshiUtil::getColumnName).collect(Collectors.joining(StrUtil.COMMA)) + ") like '%" + keyword + "%'";
        queryWrapper.apply(StrUtil.isNotBlank(keyword) && CollUtil.isNotEmpty(columns), sql);
        return queryWrapper;
    }

    /**
     * 扩展的mybatis-plus分页接口
     * 示例：xxxService.queryWrapperPage([basePage类或集成了BasePage的类], item -> item.eq(User::getUserId,1));
     *
     * @param basicPage 列表分页查询参数
     * @param consumer  item -> item.eq("user_id",1)
     * @param <E>       e
     * @return Page
     */
    default <E extends BasicPage> Page<T> queryWrapperPage(E basicPage, Consumer<QueryWrapper<T>> consumer) {
        return this.getBaseMapper().selectPage(this.buildPage(basicPage), new QueryWrapper<T>().func(Objects.nonNull(consumer), consumer));
    }

    /**
     * 扩展的mybatis-plus分页接口
     * 示例：xxxService.queryWrapperPage([basePage类或继承了BasePage的类], item -> item.eq(User::getUserId,1));
     *
     * @param basicPage 列表分页查询参数
     * @param consumer  item -> item.eq(User::getUserId,1)
     * @param <E>       p
     * @return Page
     */
    default <E extends BasicPage> Page<T> lambdaQueryWrapperPage(E basicPage, Consumer<LambdaQueryWrapper<T>> consumer) {
        return this.getBaseMapper().selectPage(this.buildPage(basicPage), new QueryWrapper<T>().lambda().func(Objects.nonNull(consumer), consumer));
    }

    /**
     * 判断当前实体对象中某个字段值是否已存在
     *
     * @param column 查询的字段
     * @param val    查询的值
     * @return boolean
     */
    default boolean columnExists(SFunction<T, ?> column, Object val) {
        return this.getBaseMapper().columnExists(column, val);
    }

    /**
     * 判断当前实体对象中某个字段值是否已存在，已存在时抛出异常
     *
     * @param column 查询的字段
     * @param val    查询的值
     * @param retBO  异常信息对象
     * @param args   将为消息中的参数填充的参数数组（参数在消息中类似于“{0}”、“{1,date}”、“{2,time}”），如果没有则为null
     */
    default void columnExists(SFunction<T, ?> column, Object val, RetBO retBO, Object... args) {
        this.getBaseMapper().columnExists(column, val, retBO, args);
    }

    /**
     * 判断当前实体对象中某个字段值是否已存在，不包括本身
     *
     * @param column 查询的字段
     * @param val    查询的值
     * @param id     主键ID值
     * @return boolean
     */
    default boolean columnExists(SFunction<T, ?> column, Object val, Serializable id) {
        return this.getBaseMapper().columnExists(column, val, id);
    }

    /**
     * 判断当前实体对象中某个字段值是否已存在，不包括本身，已存在时抛出异常
     *
     * @param column 查询的字段
     * @param val    查询的值
     * @param id     主键ID值
     * @param retBO  结果对象
     * @param args   将为消息中的参数填充的参数数组（参数在消息中类似于“{0}”、“{1,date}”、“{2,time}”），如果没有则为null
     */
    default void columnExists(SFunction<T, ?> column, Object val, Serializable id, RetBO retBO, Object... args) {
        this.getBaseMapper().columnExists(column, val, id, retBO, args);
    }

    /**
     * 根据 UpdateWrapper 条件，更新记录 需要设置sqlset
     *
     * @param updateWrapper 实体对象封装操作类 {@link UpdateWrapper}
     * @return boolean
     */
    @Override
    default boolean update(Wrapper<T> updateWrapper) {
        // 由于调用update(T t,Wrapper updateWrapper)时t不能为空,否则自动填充失效
        TableInfo tableInfo = TableInfoHelper.getTableInfo(this.getEntityClass());
        return this.update(tableInfo.newInstance(), updateWrapper);
    }

    /**
     * <p>更新某一个字段的值</p>
     * <p>例如：更新禁用/启用状态</p>
     *
     * @param id     主键ID值
     * @param column 需要更新的字段
     * @param val    更新后的值
     * @return boolean
     */
    default boolean updateColumnById(Serializable id, SFunction<T, ?> column, Serializable val) {
        return this.getBaseMapper().updateColumnById(id, column, val);
    }

    /**
     * 根据 TableId 逻辑删除
     *
     * @param id id
     * @return boolean
     */
    default boolean logicDeleteById(Serializable id) {
        return this.getBaseMapper().logicDeleteById(id);
    }

    /**
     * 根据主键ID查询（不区分是否已逻辑删除）
     *
     * @param id id
     * @return T
     */
    default T getIncludeDelById(Serializable id) {
        return this.getBaseMapper().selectIncludeDelById(id);
    }

    /**
     * 翻页查询
     *
     * @param page         翻页对象
     * @param queryWrapper 实体对象封装操作类 {@link com.baomidou.mybatisplus.core.conditions.query.QueryWrapper}
     * @param <P>          p
     * @param <V>          V
     * @return TakeshiPage
     */
    default <P extends TakeshiPage<V>, V> TakeshiPage<V> selectPojoPage(P page, Wrapper<T> queryWrapper) {
        return this.getBaseMapper().selectPojoPage(page, queryWrapper);
    }

    /**
     * 获取对应 entity 的 BaseMapper
     *
     * @return BaseMapper
     */
    @Override
    TakeshiMapper<T> getBaseMapper();

}
