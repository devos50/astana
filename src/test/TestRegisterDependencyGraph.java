package test;

import main.Method;
import main.MethodExecutionPath;
import main.RegisterDependencyNode;
import main.SmaliFileParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRegisterDependencyGraph {

    public static SmaliFileParser parser;

    @BeforeAll
    public static void setUpClass() throws FileNotFoundException {
        parser = new SmaliFileParser("test.apk", new File(Paths.get("src", "test", "resources", "regdepgraph.smali").toString()));
    }

    @Test
    public void testRegisterDependencyGraphSimple() {
        Method method = parser.getMethod("method1");
        method.buildCFG();
        MethodExecutionPath path = method.getExecutionPaths(0, 2).get(0);
        path.buildRegisterDependencyGraph();
        assertEquals(2, path.registerDependencyGraph.adjacency.keySet().size());
        assertEquals(path.registerDependencyGraph.activeRegister.get(1).sequenceNumber, 1);
        assertEquals(path.registerDependencyGraph.activeRegister.get(2).sequenceNumber, 1);
    }

    @Test
    public void testRegisterDependencyGraphOneDep() {
        Method method = parser.getMethod("method2");
        method.buildCFG();
        MethodExecutionPath path = method.getExecutionPaths(0, 3).get(0);
        path.buildRegisterDependencyGraph();
        assertEquals(3, path.registerDependencyGraph.adjacency.keySet().size());
        RegisterDependencyNode v3 = path.registerDependencyGraph.activeRegister.get(3);
        assertEquals(2, path.registerDependencyGraph.adjacency.get(v3).size());
    }

    @Test
    public void testRegisterDependencyGraphGoto() {
        Method method = parser.getMethod("method3");
        method.buildCFG();
        MethodExecutionPath path = method.getExecutionPaths(0, 5).get(0);
        path.buildRegisterDependencyGraph();
        assertEquals(2, path.registerDependencyGraph.adjacency.keySet().size());
    }

    @Test
    public void testRegisterDependencyGraphIfStatement() {
        Method method = parser.getMethod("method4");
        method.buildCFG();
        List<MethodExecutionPath> paths = method.getExecutionPaths(0, 8);
        assertEquals(2, paths.size());

        // one of the two paths should have defined v2
        boolean hasV2 = false;
        for(MethodExecutionPath path : paths) {
            path.buildRegisterDependencyGraph();
            if(path.registerDependencyGraph.activeRegister.containsKey(2)) {
                hasV2 = true;
            }
        }
        assertTrue(hasV2);
    }

    @Test
    public void testRegisterDependencyGraphSwitch() {
        Method method = parser.getMethod("method5");
        method.buildCFG();
        List<MethodExecutionPath> paths = method.getExecutionPaths(0, 12);
        assertEquals(3, paths.size());

        // one of the two paths should have defined v2 and v3
        boolean hasV2 = false;
        boolean hasV3 = false;
        for(MethodExecutionPath path : paths) {
            path.buildRegisterDependencyGraph();
            if(path.registerDependencyGraph.activeRegister.containsKey(2)) {
                hasV2 = true;
            }
            if(path.registerDependencyGraph.activeRegister.containsKey(3)) {
                hasV3 = true;
            }
        }
        assertTrue(hasV2);
        assertTrue(hasV3);
    }

    @Test
    public void testRegisterDependencyGraphSample1() {
        Method method = parser.getMethod("method6");
        method.buildCFG();
        List<MethodExecutionPath> paths = method.getExecutionPaths(2, 16);
        assertEquals(2, paths.size());

        System.out.println(paths);
        paths.get(0).buildRegisterDependencyGraph();
        System.out.println(paths.get(0).registerDependencyGraph.adjacency);
        paths.get(1).buildRegisterDependencyGraph();
        System.out.println(paths.get(1).registerDependencyGraph.adjacency);

//        path.buildRegisterDependencyGraph();
//        assertEquals(2, path.registerDependencyGraph.adjacency.keySet().size());
//        assertEquals(path.registerDependencyGraph.activeRegister.get(1).sequenceNumber, 1);
//        assertEquals(path.registerDependencyGraph.activeRegister.get(2).sequenceNumber, 1);
    }
}
