package com.code.analyst.parser;

import java.util.ArrayList;
import java.util.List;

public class DbOperation {
    private String tableName;
    private String operationType;
    private List<String> columns;
    private String whereCondition;
    private String sql;

    public DbOperation(String tableName, String operationType) {
        this.tableName = tableName;
        this.operationType = operationType;
        this.columns = new ArrayList<>();
        this.whereCondition = "";
        this.sql = "";
    }

    public DbOperation(String tableName, String operationType, List<String> columns, String whereCondition, String sql) {
        this.tableName = tableName;
        this.operationType = operationType;
        this.columns = columns;
        this.whereCondition = whereCondition;
        this.sql = sql;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public String getWhereCondition() {
        return whereCondition;
    }

    public void setWhereCondition(String whereCondition) {
        this.whereCondition = whereCondition;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }
}
