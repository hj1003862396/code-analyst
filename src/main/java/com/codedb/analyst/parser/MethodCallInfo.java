package com.codedb.analyst.parser;

public class MethodCallInfo {
    private String objectName;
    private String methodName;
    private String objectType;

    public MethodCallInfo(String objectName, String methodName, String objectType) {
        this.objectName = objectName;
        this.methodName = methodName;
        this.objectType = objectType;
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }
}
