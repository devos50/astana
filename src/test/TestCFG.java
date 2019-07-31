package test;

import main.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;


public class TestCFG {

    public static SmaliFileParser parser;

    @BeforeAll
    public static void setUpClass() throws FileNotFoundException {
        parser = new SmaliFileParser("test.apk", new File(Paths.get("src", "test", "resources", "methods.smali").toString()));
    }

    @Test
    public void testLinearCFG() {
        Method method = parser.getMethod("method1");
        assertNotNull(method);

        method.buildCFG();
        assertEquals(3, method.controlFlowGraph.nodes.size());
        assertEquals(1, method.controlFlowGraph.getJumps(1).size());
        assertEquals(0, method.controlFlowGraph.getJumps(2).size());
    }

    @Test
    public void testIfGotoCFG() {
        Method method = parser.getMethod("method2");
        assertNotNull(method);

        method.buildCFG();
        assertEquals(2, method.controlFlowGraph.getJumps(3).size()); // if-eqz
        assertEquals(1, method.controlFlowGraph.getJumps(6).size()); // if-eqz
    }

    @Test
    public void testTryCatchCFG() {
        Method method = parser.getMethod("method3");
        assertNotNull(method);

        method.buildCFG();
        assertEquals(2, method.controlFlowGraph.getJumps(2).size()); // try -> catch block

        // check whether we jump to the right statements
        Set<Integer> jumpTo = new HashSet<>();
        for(ControlFlowGraphJump jump : method.controlFlowGraph.getJumps(2)) {
            jumpTo.add(jump.toNode.stmtIndex);
        }
        assertTrue(jumpTo.contains(6));
    }

    @Test
    public void testSwitchCFG() {
        Method method = parser.getMethod("method4");
        assertNotNull(method);

        method.buildCFG();
        assertEquals(3, method.controlFlowGraph.getJumps(1).size()); // switch

        // check whether we jump to the right statements
        Set<Integer> jumpTo = new HashSet<>();
        for(ControlFlowGraphJump jump : method.controlFlowGraph.getJumps(1)) {
            jumpTo.add(jump.toNode.stmtIndex);
        }

        assertTrue(jumpTo.contains(3));
        assertTrue(jumpTo.contains(6));
        assertTrue(jumpTo.contains(9));
    }

    @Test
    public void testComplexMethod() {
        Method method = parser.getMethod("method5");
        assertNotNull(method);

        method.buildCFG();
        assertEquals(128, method.controlFlowGraph.adjacency.size());
    }
}