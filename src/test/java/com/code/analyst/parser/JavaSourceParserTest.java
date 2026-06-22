package com.code.analyst.parser;

import org.junit.jupiter.api.Test;
import java.io.FileWriter;
import java.io.File;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class JavaSourceParserTest {
    @Test
    public void testIgnoredCallFiltering() throws Exception {
        JavaSourceParser parser = new JavaSourceParser();
        File tempFile = File.createTempFile("MockService", ".java");
        tempFile.deleteOnExit();
        
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(
                "package com.example;\n" +
                "public class MockService {\n" +
                "    private UserDTO userDto;\n" +
                "    private OrderEntity orderEntity;\n" +
                "    private BusinessService businessService;\n" +
                "    private ShortLinkRepo shortLinkRepo;\n" +
                "    public void doSomething() {\n" +
                "        userDto.getId();\n" +
                "        orderEntity.save();\n" +
                "        businessService.process();\n" +
                "        shortLinkRepo.selectById(1L);\n" +
                "        String.format(\"abc\");\n" +
                "    }\n" +
                "}\n"
            );
        }
        
        List<MethodCallInfo> calls = parser.parseMethodCalls(tempFile.getAbsolutePath(), "doSomething");
        assertEquals(2, calls.size());
        
        boolean hasService = calls.stream().anyMatch(c -> c.getObjectName().equals("businessService") && c.getMethodName().equals("process"));
        boolean hasRepo = calls.stream().anyMatch(c -> c.getObjectName().equals("shortLinkRepo") && c.getMethodName().equals("selectById"));
        assertTrue(hasService, "Should contain businessService.process");
        assertTrue(hasRepo, "Should contain shortLinkRepo.selectById");
    }

    @Test
    public void testLocalMethodCallParsing() throws Exception {
        JavaSourceParser parser = new JavaSourceParser();
        File tempFile = File.createTempFile("FinanceController", ".java");
        tempFile.deleteOnExit();
        
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(
                "package com.example;\n" +
                "public class FinanceController {\n" +
                "    private InvoiceFacade invoiceFacade;\n" +
                "    public void saveInvoiceWithoutTitle() {\n" +
                "        invoiceFacade.doInvoice();\n" +
                "        nuonuoInvoiceApply();\n" +
                "    }\n" +
                "    private void nuonuoInvoiceApply() {\n" +
                "    }\n" +
                "}\n"
            );
        }
        
        List<MethodCallInfo> calls = parser.parseMethodCalls(tempFile.getAbsolutePath(), "saveInvoiceWithoutTitle");
        assertEquals(2, calls.size());
        
        boolean hasFacade = calls.stream().anyMatch(c -> c.getObjectName().equals("invoiceFacade") && c.getMethodName().equals("doInvoice"));
        boolean hasLocal = calls.stream().anyMatch(c -> c.getObjectName().equals("this") && c.getMethodName().equals("nuonuoInvoiceApply") && c.getObjectType().equals("FinanceController"));
        assertTrue(hasFacade, "Should contain invoiceFacade.doInvoice");
        assertTrue(hasLocal, "Should contain local call this.nuonuoInvoiceApply with type FinanceController");
    }

    @Test
    public void testUtilityClassAndExceptionsFiltering() throws Exception {
        JavaSourceParser parser = new JavaSourceParser();
        File tempFile = File.createTempFile("MockController", ".java");
        tempFile.deleteOnExit();
        
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(
                "package com.example;\n" +
                "public class MockController {\n" +
                "    public void show() {\n" +
                "        StringUtils.isBlank(\"a\");\n" +
                "        ContextUtils.getUserId();\n" +
                "        MyHelper.doWork();\n" +
                "        AppConfig.setup();\n" +
                "        ErrorCodeConstant.SUCCESS.code();\n" +
                "        throw new BusinessException(\"error\");\n" +
                "    }\n" +
                "}\n"
            );
        }
        
        List<MethodCallInfo> calls = parser.parseMethodCalls(tempFile.getAbsolutePath(), "show");
        assertEquals(0, calls.size(), "All utility, config, constant, helper, and exception calls should be filtered out");
    }
}
