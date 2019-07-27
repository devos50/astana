package test;

import main.Method;
import main.ProgramSlice;
import main.SmaliFileParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestProgramSlice {

    public static SmaliFileParser parser;

    @BeforeAll
    public static void setUpClass() throws FileNotFoundException {
        parser = new SmaliFileParser("test.apk", new File(Paths.get("src", "test", "resources", "slices.smali").toString()));
    }

    public ProgramSlice getSliceObject(Method method, int stmtCriterion, int register) {
        return new ProgramSlice("test.apk", new File(Paths.get("src", "test", "resources", "slices.smali").toString()),
                method, stmtCriterion, register);
    }

    @Test
    public void testSliceSingleLine() {
        Method method = parser.getMethod("method1");
        method.buildCFG();

        ProgramSlice slice = getSliceObject(method, 1, 2);
        slice.compute();
        assertEquals(1, slice.extractedStatements.size());
    }

    @Test
    public void testSliceAddition() {
        Method method = parser.getMethod("method2");
        method.buildCFG();

        ProgramSlice slice = getSliceObject(method, 3, 3);
        slice.compute();
        assertEquals(3, slice.extractedStatements.size());
        assertTrue(slice.extractedStatements.contains(method.methodNode.codeNode.stmts.get(0)));
        assertTrue(slice.extractedStatements.contains(method.methodNode.codeNode.stmts.get(2)));
        assertTrue(slice.extractedStatements.contains(method.methodNode.codeNode.stmts.get(3)));
    }

    @Test
    public void testSliceIfStatement() {
        Method method = parser.getMethod("method3");
        method.buildCFG();

        ProgramSlice slice = getSliceObject(method, 10, 1);
        slice.compute();
        assertEquals(7, slice.extractedStatements.size());
        assertFalse(slice.extractedStatements.contains(method.methodNode.codeNode.stmts.get(2)));
        assertFalse(slice.extractedStatements.contains(method.methodNode.codeNode.stmts.get(6)));
    }

    @Test
    public void TestUnnecessaryIf() {
        Method method = parser.getMethod("method4");
        method.buildCFG();

        ProgramSlice slice = getSliceObject(method, 9, 2);
        slice.compute();
        System.out.println(slice.extractedStatements);
    }

    @Test
    public void testComplexSlice() {
        Method method = parser.getMethod("method5");
        method.buildCFG();

        ProgramSlice slice = getSliceObject(method, 36, 1);
        slice.compute();

        assertEquals(35, slice.extractedStatements.size());
    }
}
