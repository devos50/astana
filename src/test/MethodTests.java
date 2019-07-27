package test;

import main.Method;
import main.SmaliFileParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Paths;


public class MethodTests {

    @Test
    public void testSimpleMethod() throws FileNotFoundException {
        SmaliFileParser parser = new SmaliFileParser("test.apk", new File(Paths.get("src", "test", "resources", "cfg.smali").toString()));

        assertNotNull(parser.rootNode.methods);
//        Method m1 = new Method(parser.rootNode.methods.get(0));
//        assertNotNull(m1.controlFlowGraph);
    }
}