package com.chuhezhe.common.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes(float[].class)
public class VectorTypeHandler extends BaseTypeHandler<float[]> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, float[] parameter, JdbcType jdbcType) throws SQLException {
        PGobject pgObject = new PGobject();
        pgObject.setType("vector");
        pgObject.setValue(toVectorString(parameter));
        ps.setObject(i, pgObject);
    }

    @Override
    public float[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return value != null ? fromVectorString(value) : null;
    }

    @Override
    public float[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return value != null ? fromVectorString(value) : null;
    }

    @Override
    public float[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return value != null ? fromVectorString(value) : null;
    }

    private String toVectorString(float[] values) {
        StringBuilder sb = new StringBuilder("[");
        for (int j = 0; j < values.length; j++) {
            if (j > 0) {
                sb.append(",");
            }
            sb.append(values[j]);
        }
        sb.append("]");
        return sb.toString();
    }

    private float[] fromVectorString(String value) {
        String trimmed = value.replaceAll("[\\[\\]\\s]", "");
        if (trimmed.isEmpty()) {
            return new float[0];
        }
        String[] parts = trimmed.split(",");
        float[] result = new float[parts.length];
        for (int j = 0; j < parts.length; j++) {
            result[j] = Float.parseFloat(parts[j]);
        }
        return result;
    }
}
