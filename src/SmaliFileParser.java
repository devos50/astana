import com.googlecode.d2j.node.DexClassNode;
import com.googlecode.d2j.node.DexMethodNode;
import com.googlecode.d2j.node.insn.*;
import com.googlecode.d2j.reader.Op;
import com.googlecode.d2j.smali.Smali;
import javafx.util.Pair;

import java.io.*;
import java.util.*;

public class SmaliFileParser {

    private final File smaliFile;
    private DexClassNode rootNode;
    public List<StringSnippet> snippets = new ArrayList<>();

    public SmaliFileParser(File smaliFile) {
        this.smaliFile = smaliFile;
    }

    public void processSnippet(StringSnippet snippet) {
        // find all possible paths from the string declaration to the point where the string is decrypted
        // TODO check whether we can even make the jump!

        Set<MethodExecutionPath> paths = new HashSet<>();
        LinkedList<Pair<MethodSection, MethodExecutionPath>> queue = new LinkedList<>();
        MethodSection sourceSection = snippet.method.getSectionForStatement(snippet.stringInitIndex);
        MethodSection destinationSection = snippet.method.getSectionForStatement(snippet.stringDecryptedIndex);
        MethodExecutionPath firstPath = new MethodExecutionPath(snippet.method, snippet.stringInitIndex, snippet.stringDecryptedIndex, snippet.stringResultRegister);
        firstPath.sectionsVisited.add(sourceSection);
        queue.add(new Pair<>(sourceSection, firstPath));
        while(!queue.isEmpty()) {
            Pair<MethodSection, MethodExecutionPath> pair = queue.remove();
            MethodSection currentNode = pair.getKey();
            MethodExecutionPath currentPath = pair.getValue();
            if(currentNode == destinationSection) {
                paths.add(currentPath);
                continue;
            }

            // get outgoing edges and add them to the queue
            for(MethodSectionJump jump : snippet.method.controlFlowGraph.adjacency.get(currentNode)) {
                if(!currentPath.path.contains(jump)) {

                    // can we even make the jump?
                    if(currentNode == sourceSection && snippet.stringInitIndex > jump.jumpStmtIndex) {
                        continue;
                    }

                    MethodExecutionPath copied = currentPath.copy();
                    copied.path.add(jump);
                    copied.sectionsVisited.add(jump.toSection);
                    queue.add(new Pair<>(jump.toSection, copied));
                }
            }
        }

        // for each possible path, build a register dependency graph and compute involved statements
        boolean[] involvedStatements = new boolean[snippet.method.methodNode.codeNode.stmts.size()];
        for(MethodExecutionPath path : paths) {
            path.buildRegisterDependencyGraph();
            path.computeInvolvedStatements();
            for(int i = 0; i < path.involvedStatements.length; i++) {
                involvedStatements[i] = involvedStatements[i] || path.involvedStatements[i];
            }
        }

        for(int i = 0; i < involvedStatements.length; i++) {
            DexStmtNode stmtNode = snippet.method.methodNode.codeNode.stmts.get(i);
//            System.out.println(stmtNode.op + " (" + involvedStatements[i] + ")");
            if(involvedStatements[i]) {
                snippet.extractedStatements.add(stmtNode);
            }
        }

        snippets.add(snippet);
    }

    public Pair<Integer, Integer> isPotentialStringDecryption(DexMethodNode methodNode, int stmtIndex) {
        DexStmtNode stmtNode = methodNode.codeNode.stmts.get(stmtIndex);
        if(stmtNode.op == Op.INVOKE_DIRECT) {
            MethodStmtNode mnn = (MethodStmtNode) stmtNode;
            if(mnn.method.getName().equals("<init>") && mnn.method.getOwner().equals("Ljava/lang/String;")) {
                return new Pair<>(stmtIndex, mnn.args[0]);
            }
        }
        else if(stmtNode.op == Op.INVOKE_STATIC) {
            MethodStmtNode mnn = (MethodStmtNode) stmtNode;
            if(mnn.method.getReturnType().equals("Ljava/lang/String;") && mnn.args.length > 0) {
                // get the next statement -> this should be a move-result-object
                DexStmtNode nextStmtNode = methodNode.codeNode.stmts.get(stmtIndex + 1);
                if(nextStmtNode.op != Op.MOVE_RESULT_OBJECT) {
                    throw new RuntimeException("Next stement not move-result-object!");
                }
                Stmt1RNode castNode = (Stmt1RNode) nextStmtNode;
                return new Pair<>(stmtIndex + 1, castNode.a);
            }
        }
        return new Pair<>(-1, -1);
    }

    public Pair<Integer, Integer> findPossibleStringDecryptionStatement(StringSnippet snippet) {
        // first, analyze the section, from the point where the string is declared
        MethodSection rootSection = snippet.method.getSectionForStatement(snippet.stringInitIndex);
        for(int stmtIndex = snippet.stringInitIndex + 1; stmtIndex < rootSection.endIndex; stmtIndex++) {
            Pair<Integer, Integer> pair = isPotentialStringDecryption(snippet.method.methodNode, stmtIndex);
            if(pair.getKey() != -1) {
                return pair;
            }
        }

        // we could not find it in the root section; look further
        List<MethodSection> visited = new ArrayList<>();
        LinkedList<MethodSection> queue = new LinkedList<>();
        visited.add(rootSection);
        queue.add(rootSection);

        // analyze the section that contains the string init
        while(!queue.isEmpty()) {
            MethodSection currentSection = queue.remove();
            List<MethodSectionJump> adjacent = snippet.method.controlFlowGraph.adjacency.get(currentSection);

            for(MethodSectionJump jump : adjacent) {
                MethodSection adjacentSection = jump.toSection;
                if(!visited.contains(adjacentSection)) {
                    for(int stmtIndex = adjacentSection.beginIndex; stmtIndex < adjacentSection.endIndex; stmtIndex++) {
                        Pair<Integer, Integer> pair = isPotentialStringDecryption(snippet.method.methodNode, stmtIndex);
                        if(pair.getKey() != -1) {
                            return pair;
                        }
                    }

                    visited.add(adjacentSection);
                    queue.add(adjacentSection);
                }
            }
        }
        return new Pair<>(-1, -1);
    }

    public void process() throws FileNotFoundException {
        InputStream input = new FileInputStream(smaliFile);
        try {
            this.rootNode = Smali.smaliFile2Node("test.smali", input);
        } catch (Exception e) {
            System.out.println("Could not parse file " + smaliFile.getPath());
            return;
        }

        if(rootNode.methods == null) {
            return;
        }

        for (DexMethodNode methodNode : rootNode.methods) {
//            if(!methodNode.method.getName().equals("nBp")) {
//                continue;
//            }

            System.out.println("Processing method: " + methodNode.method.getName());
            Method method = new Method(methodNode);
            for (int stmtIndex = 0; stmtIndex < methodNode.codeNode.stmts.size(); stmtIndex++) {
                DexStmtNode stmtNode = methodNode.codeNode.stmts.get(stmtIndex);
                if (stmtNode.op == Op.CONST_STRING || stmtNode.op == Op.CONST_STRING_JUMBO) {
                    ConstStmtNode stringInitNode = (ConstStmtNode) stmtNode;
                    if(stringInitNode.value.toString().length() > 0) {
                        System.out.println("Processing str: " + stringInitNode.value.toString());
                        StringSnippet snippet = new StringSnippet(smaliFile, method, stmtIndex);
                        Pair<Integer, Integer> pair = findPossibleStringDecryptionStatement(snippet);
                        if(pair.getKey() != -1) {
                            snippet.stringDecryptedIndex = pair.getKey();
                            snippet.stringResultRegister = pair.getValue();
                            processSnippet(snippet);
                        }
                    }
                }
            }
        }
    }
}
