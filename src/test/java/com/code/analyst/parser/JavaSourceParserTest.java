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
}
