package com.takeshi.mybatisplus.typehandler;

import com.takeshi.util.AmazonS3Util;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * <p>AmazonS3TypeHandler</p>
 * <p>注意！！ 使用typeHandler，必须开启autoResultMap映射注解</p>
 * <p>@TableName(autoResultMap = true)</p>
 * <p>@TableField(typeHandler = AmazonS3TypeHandler.class)</p>
 * <p>S3的key存入数据库，不处理，从数据库取出来时通过key获取临时URL返回</p>
 *
 * @author 七濑武【Nanase Takeshi】
 */
public class AmazonS3TypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, parameter);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return AmazonS3Util.getPresignedUrl(rs.getString(columnName)).toString();
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return AmazonS3Util.getPresignedUrl(rs.getString(columnIndex)).toString();
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return AmazonS3Util.getPresignedUrl(cs.getString(columnIndex)).toString();
    }

}
