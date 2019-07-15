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
        queue.add(new Pair<>(sourceSection, new MethodExecutionPath(snippet.method, snippet.stringInitIndex, snippet.stringDecryptedIndex, snippet.stringResultRegister)));
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
                    MethodExecutionPath copied = currentPath.copy();
                    copied.path.add(jump);
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
            System.out.println(stmtNode.op + " (" + involvedStatements[i] + ")");
            if(involvedStatements[i]) {
                snippet.extractedStatements.add(stmtNode);
            }
        }

        try {
            StringDecryptor.decrypt(snippet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Pair<Boolean, Integer> isPotentialStringDecryption(DexMethodNode methodNode, int stmtIndex) {
        DexStmtNode stmtNode = methodNode.codeNode.stmts.get(stmtIndex);
        if(stmtNode.op == Op.INVOKE_DIRECT) {
            MethodStmtNode mnn = (MethodStmtNode) stmtNode;
            if(mnn.method.getName().equals("<init>") && mnn.method.getOwner().equals("Ljava/lang/String;")) {
                return new Pair<>(true, mnn.args[0]);
            }
        }
        else if(stmtNode.op == Op.INVOKE_STATIC) {
            MethodStmtNode mnn = (MethodStmtNode) stmtNode;
            if(mnn.method.getReturnType().equals("Ljava/lang/String;") && mnn.args.length > 0) {
                return new Pair<>(true, 1);
            }
        }
        return new Pair<>(false, -1);
    }

    public Pair<Integer, Integer> findPossibleStringDecryptionStatement(StringSnippet snippet) {
        // first, analyze the section, from the point where the string is declared
        MethodSection rootSection = snippet.method.getSectionForStatement(snippet.stringInitIndex);
        for(int stmtIndex = snippet.stringInitIndex + 1; stmtIndex < rootSection.endIndex; stmtIndex++) {
            Pair<Boolean, Integer> pair = isPotentialStringDecryption(snippet.method.methodNode, stmtIndex);
            if(pair.getKey()) {
                return new Pair<>(stmtIndex, pair.getValue());
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
                        Pair<Boolean, Integer> pair = isPotentialStringDecryption(snippet.method.methodNode, stmtIndex);
                        if(pair.getKey()) {
                            return new Pair<>(stmtIndex, pair.getValue());
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
//        System.out.println("Processing file: " + smaliFile.getPath());
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
            if(!methodNode.method.getName().equals("p")) {
                continue;
            }

            Method method = new Method(methodNode);

            System.out.println(method.controlFlowGraph.adjacency);

            //System.out.println("Method: " + methodNode.method.getName());
            for (int stmtIndex = 0; stmtIndex < methodNode.codeNode.stmts.size(); stmtIndex++) {
                DexStmtNode stmtNode = methodNode.codeNode.stmts.get(stmtIndex);
                if (stmtNode.op == Op.CONST_STRING || stmtNode.op == Op.CONST_STRING_JUMBO) {
                    ConstStmtNode stringInitNode = (ConstStmtNode) stmtNode;
                    if(stringInitNode.value.toString().length() > 0 && stringInitNode.value.toString().contains("Z\\\\T")) {
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
