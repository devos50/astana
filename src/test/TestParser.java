package test;

import com.googlecode.d2j.node.insn.DexStmtNode;
import com.googlecode.d2j.node.insn.JumpStmtNode;
import main.Method;
import main.SmaliFileParser;
import main.StringSnippet;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TestParser {

    @Test
    public void testGetStringPairsFalse() throws FileNotFoundException {
        SmaliFileParser parser = new SmaliFileParser("test.apk", new File(Paths.get("src", "test", "resources", "parsertest.smali").toString()));
        Method method = parser.getMethod("method1");
        method.buildCFG();
        assertEquals(0, parser.getPotentialStringSnippets(method).size());

        // empty string
        method = parser.getMethod("method5");
        method.buildCFG();
        assertEquals(0, parser.getPotentialStringSnippets(method).size());
    }

    @Test
    public void testGetStringPairsStatic() throws FileNotFoundException {
        SmaliFileParser parser = new SmaliFileParser("test.apk", new File(Paths.get("src", "test", "resources", "parsertest.smali").toString()));
        Method method = parser.getMethod("method2");
        method.buildCFG();
        assertEquals(1, parser.getPotentialStringSnippets(method).size());
    }

    @Test
    public void testGetStringPairsTryCatch() throws FileNotFoundException {
        SmaliFileParser parser = new SmaliFileParser("test.apk", new File(Paths.get("src", "test", "resources", "parsertest.smali").toString()));
        Method method = parser.getMethod("method3");
        method.buildCFG();
        assertEquals(1, parser.getPotentialStringSnippets(method).size());
    }

    @Test
    public void testGetStringIfStatement() throws FileNotFoundException {
        SmaliFileParser parser = new SmaliFileParser("test.apk", new File(Paths.get("src", "test", "resources", "parsertest.smali").toString()));
        Method method = parser.getMethod("method4");
        method.buildCFG();
        assertEquals(1, parser.getPotentialStringSnippets(method).size());
    }

    @Test
    public void testProgramSliceSimple() throws FileNotFoundException {
        SmaliFileParser parser = new SmaliFileParser("test.apk", new File(Paths.get("src", "test", "resources", "parsertest.smali").toString()));
        Method method = parser.getMethod("method6");
        method.buildCFG();
        List<StringSnippet> snippets = parser.getPotentialStringSnippets(method);
        assertEquals(1, snippets.size());
        parser.processSnippet(snippets.get(0));
        assertEquals(1, parser.snippets.size());
        assertEquals(8, parser.snippets.get(0).extractedStatements.size());
        assertEquals(0, parser.snippets.get(0).stringResultRegister);
    }

    @Test
    public void testProgramSliceForLoop() throws FileNotFoundException {
        SmaliFileParser parser = new SmaliFileParser("test.apk", new File(Paths.get("src", "test", "resources", "parsertest.smali").toString()));
        Method method = parser.getMethod("method7");
        method.buildCFG();
        List<StringSnippet> snippets = parser.getPotentialStringSnippets(method);
        assertEquals(1, snippets.size());
        parser.processSnippet(snippets.get(0));
        assertEquals(1, parser.snippets.size());
        assertEquals(37, parser.snippets.get(0).extractedStatements.size());
        assertEquals(1, parser.snippets.get(0).stringResultRegister);
    }

    @Test
    public void testProgramSliceUselessConditional() throws FileNotFoundException {
        SmaliFileParser parser = new SmaliFileParser("test.apk", new File(Paths.get("src", "test", "resources", "parsertest.smali").toString()));
        Method method = parser.getMethod("method8");
        method.buildCFG();
        List<StringSnippet> snippets = parser.getPotentialStringSnippets(method);
        assertEquals(1, snippets.size());
        parser.processSnippet(snippets.get(0));
        assertEquals(1, parser.snippets.size());
        assertEquals(8, parser.snippets.get(0).extractedStatements.size());

        // the conditional should be filtered out
        boolean found = false;
        for(DexStmtNode stmtNode : parser.snippets.get(0).extractedStatements) {
            if(stmtNode instanceof JumpStmtNode) {
                found = true;
                break;
            }
        }
        assertFalse(found);
    }
}
