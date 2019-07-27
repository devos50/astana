package test;

import main.ControlFlowGraphNode;
import main.Method;
import main.SmaliFileParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;


public class TestCFG {

    public static SmaliFileParser parser;

    @BeforeAll
    public static void setUpClass() throws FileNotFoundException {
        parser = new SmaliFileParser("test.apk", new File(Paths.get("src", "test", "resources", "cfg.smali").toString()));
    }

    @Test
    public void testLinearCFG() {
        Method method = parser.getMethod("method1");
        assertNotNull(method);

        method.buildCFG();
        assertEquals(4, method.controlFlowGraph.nodes.size());
        assertEquals(1, method.controlFlowGraph.getNextNodes(1).size());
        assertEquals(0, method.controlFlowGraph.getNextNodes(2).size());
    }

    @Test
    public void testIfGotoCFG() {
        Method method = parser.getMethod("method2");
        assertNotNull(method);

        method.buildCFG();
        assertEquals(2, method.controlFlowGraph.getNextNodes(3).size()); // if-eqz
        assertEquals(1, method.controlFlowGraph.getNextNodes(6).size()); // if-eqz
    }

    @Test
    public void testTryCatchCFG() {
        Method method = parser.getMethod("method3");
        assertNotNull(method);

        method.buildCFG();
        assertEquals(2, method.controlFlowGraph.getNextNodes(2).size()); // try -> catch block
        assertTrue(method.controlFlowGraph.getNextNodes(2).contains(new ControlFlowGraphNode(6)));
    }

    @Test
    public void testSwitchCFG() {
        Method method = parser.getMethod("method4");
        assertNotNull(method);

        method.buildCFG();
        assertEquals(3, method.controlFlowGraph.getNextNodes(1).size()); // switch
        assertTrue(method.controlFlowGraph.getNextNodes(1).contains(new ControlFlowGraphNode(3)));
        assertTrue(method.controlFlowGraph.getNextNodes(1).contains(new ControlFlowGraphNode(6)));
        assertTrue(method.controlFlowGraph.getNextNodes(1).contains(new ControlFlowGraphNode(9)));
    }

    @Test
    public void testComplexMethod() {
        Method method = parser.getMethod("method5");
        assertNotNull(method);

        method.buildCFG();
        assertEquals(129, method.controlFlowGraph.adjacency.size());
    }
}
