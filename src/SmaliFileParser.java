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
        Set<MethodExecutionPath> stringPaths = snippet.method.getExecutionPaths(snippet.stringInitIndex, snippet.stringDecryptedIndex);

        // build register dependency graphs and compute involved statements
        boolean[] involvedStatements = new boolean[snippet.method.methodNode.codeNode.stmts.size()];
        Set<Integer> undefinedRegisters = new HashSet<>();
        for(MethodExecutionPath path : stringPaths) {
            path.buildRegisterDependencyGraph();
            undefinedRegisters.addAll(path.registerDependencyGraph.undefinedRegisters);

            Pair<Set<RegisterDependencyNode>, boolean[]> pair = path.computeInvolvedStatements(snippet.stringResultRegister);
            Set<RegisterDependencyNode> involvedRegisters = pair.getKey();
            boolean[] pathInvolvedStatements = pair.getValue();

            // check whether the original string declaration is an involved register. If not, this string is probably not encrypted
            ConstStmtNode stringInitNode = (ConstStmtNode) snippet.method.methodNode.codeNode.stmts.get(snippet.stringInitIndex);
            RegisterDependencyNode destNode = new RegisterDependencyNode(stringInitNode.a, 1);
            RegisterDependencyNode sourceNode = path.registerDependencyGraph.activeRegister.get(snippet.stringResultRegister);
            if(!path.registerDependencyGraph.hasDependency(sourceNode, destNode)) {
                snippet.stringIsEncrypted = false;
                break;
            }

            for(int i = 0; i < pathInvolvedStatements.length; i++) {
                involvedStatements[i] = involvedStatements[i] || pathInvolvedStatements[i];
            }
        }

        // we now check if there are undefined registers - if there are, create another dependency graph
        if(undefinedRegisters.size() > 0) {
            Set<MethodExecutionPath> backwardPaths = snippet.method.getExecutionPaths(0, snippet.stringInitIndex);
            for(MethodExecutionPath path : backwardPaths) {
                path.buildRegisterDependencyGraph();
                // TODO check whether there are still undefined variables, if so, the string is not encrypted (dependency on parameter)!
                for(Integer undefinedRegister : undefinedRegisters) {
                    Pair<Set<RegisterDependencyNode>, boolean[]> pair = path.computeInvolvedStatements(undefinedRegister);
                    boolean[] pathInvolvedStatements = pair.getValue();
                    for(int i = 0; i < pathInvolvedStatements.length; i++) {
                        involvedStatements[i] = involvedStatements[i] || pathInvolvedStatements[i];
                    }
                }
            }
        }

        for(int i = 0; i < involvedStatements.length; i++) {
            DexStmtNode stmtNode = snippet.method.methodNode.codeNode.stmts.get(i);
//            System.out.println(stmtNode.op + " (" + involvedStatements[i] + ")");
            if(involvedStatements[i]) {
                snippet.extractedStatements.add(stmtNode);
            }
        }

        snippet.finalize();

        if(snippet.stringIsEncrypted) {
            snippets.add(snippet);
        }
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
                // skip possible jump statements
                int offset = 1;
                DexStmtNode nextStmtNode = methodNode.codeNode.stmts.get(stmtIndex + offset);
                while(nextStmtNode.op == null) {
                    offset += 1;
                    nextStmtNode = methodNode.codeNode.stmts.get(stmtIndex + offset);
                }

                if(nextStmtNode.op != Op.MOVE_RESULT_OBJECT) {
                    throw new RuntimeException("Next statement not move-result-object!");
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
                if(jump.jumpStmtIndex == -1) {
                    // This is a jump made by a catch. If the string init is in a try block, then the decryption method will (most likely) be not be in the catch block.
                    // Therefore, we ignore this jump.
                    continue;
                }

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
            if(methodNode.codeNode.stmts.size() == 0) {
                continue;
            }
//            if(!methodNode.method.getName().equals("onHandleIntent")) {
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
