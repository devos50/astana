package test;

import main.Method;
import main.MethodExecutionPath;
import main.SmaliFileParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestMethod {

    public static SmaliFileParser parser;

    @BeforeAll
    public static void setUpClass() throws FileNotFoundException {
        parser = new SmaliFileParser("test.apk", new File(Paths.get("src", "test", "resources", "methods.smali").toString()));
    }

    @Test
    public void testGetPathsLinear() {
        Method method = parser.getMethod("method1");
        assertNotNull(method);

        method.buildCFG();
        List<MethodExecutionPath> paths = method.getExecutionPaths(0, 2);
        assertEquals(1, paths.size());
        paths = method.getExecutionPaths(0, 8);
        assertEquals(0, paths.size());
    }

    @Test
    public void testGetPathsIfStatement() {
        Method method = parser.getMethod("method2");
        assertNotNull(method);

        method.buildCFG();
        List<MethodExecutionPath> paths = method.getExecutionPaths(0, 8);
        assertEquals(2, paths.size());
    }

    @Test
    public void testGetPathsTryCatch() {
        Method method = parser.getMethod("method3");
        assertNotNull(method);

        method.buildCFG();
        List<MethodExecutionPath> paths = method.getExecutionPaths(1, 7);
        assertEquals(1, paths.size());
    }

    @Test
    public void testGetPathsSwitch() {
        Method method = parser.getMethod("method4");
        assertNotNull(method);

        method.buildCFG();
        List<MethodExecutionPath> paths = method.getExecutionPaths(0, 12);
        assertEquals(3, paths.size());
    }
}
