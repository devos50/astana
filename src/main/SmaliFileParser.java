package main;

import com.googlecode.d2j.node.DexClassNode;
import com.googlecode.d2j.node.DexMethodNode;
import com.googlecode.d2j.node.insn.*;
import com.googlecode.d2j.reader.Op;
import com.googlecode.d2j.smali.Smali;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.io.*;
import java.util.*;

public class SmaliFileParser {

    private final String apkPath;
    private final File smaliFile;
    public DexClassNode rootNode;
    public List<Method> methods = new ArrayList<>();
    public int numStrings = 0;
    public List<StringSnippet> snippets = new ArrayList<>();

    public SmaliFileParser(String apkPath, File smaliFile) throws FileNotFoundException {
        this.apkPath = apkPath;
        this.smaliFile = smaliFile;

        InputStream input = new FileInputStream(smaliFile);
        try {
            this.rootNode = Smali.smaliFile2Node("test.smali", input);
        } catch (Exception e) {
            System.out.println("Could not parse file " + smaliFile.getPath());
        }

        if(rootNode == null || rootNode.methods == null) {
            return;
        }

        for (DexMethodNode methodNode : rootNode.methods) {
            if(methodNode.codeNode.stmts.size() > 0) {
                Method method = new Method(apkPath, smaliFile, methodNode);
                methods.add(method);
            }
        }
    }

    public Method getMethod(String methodName) {
        for(Method method : methods) {
            if(method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }

    public void processSnippet(StringSnippet snippet) {
        // find all possible paths from the string declaration to the point where the string is decrypted
        List<MethodExecutionPath> stringPaths = snippet.method.getExecutionPaths(snippet.stringInitIndex, snippet.stringDecryptedIndex);
        List<Set<Integer>> statementsSet = new ArrayList<>();

        if(stringPaths.size() == 0) {
            snippet.stringIsEncrypted = false;
            snippet.finalize();
            return;
        }

        // build register dependency graphs and compute involved statements
        boolean[] involvedStatements = new boolean[snippet.method.methodNode.codeNode.stmts.size()];
        Set<Integer> undefinedRegisters = new HashSet<>();
        for(MethodExecutionPath path : stringPaths) {
            path.buildRegisterDependencyGraph();
            RegisterDependencyNode rootNode = path.registerDependencyGraph.activeRegister.get(snippet.stringResultRegister);
            statementsSet.add(path.registerDependencyGraph.getInvolvedStatementsForNode(rootNode));
            Set<RegisterDependencyNode> dependencies = path.registerDependencyGraph.getDependencies(rootNode);
            for(Integer undefinedRegister : path.registerDependencyGraph.undefinedRegisters) {
                if(dependencies.contains(new RegisterDependencyNode(undefinedRegister, 0))) {
                    undefinedRegisters.add(undefinedRegister);
                }
            }

            // check whether the original string declaration is an involved register. If not, this string is probably not encrypted
            ConstStmtNode stringInitNode = (ConstStmtNode) snippet.method.methodNode.codeNode.stmts.get(snippet.stringInitIndex);
            RegisterDependencyNode destNode = new RegisterDependencyNode(stringInitNode.a, 1);
            RegisterDependencyNode sourceNode = path.registerDependencyGraph.activeRegister.get(snippet.stringResultRegister);
            if(!path.registerDependencyGraph.hasDependency(sourceNode, destNode)) {
                snippet.stringIsEncrypted = false;
                snippet.finalize();
                return;
            }
        }

        // test whether the statement sets are the same, if so, jumps do not matter and we can exclude them
        boolean areEqual = true;
        for(int i = 0; i < statementsSet.size() - 1; i++) {
            if(!statementsSet.get(i).equals(statementsSet.get(i + 1))) {
                areEqual = false;
                break;
            }
        }

        // compute involved statements
        for(MethodExecutionPath path : stringPaths) {
            boolean[] pathInvolvedStatements = path.computeInvolvedStatements(snippet.stringResultRegister, !areEqual);
            for(int i = 0; i < pathInvolvedStatements.length; i++) {
                involvedStatements[i] = involvedStatements[i] || pathInvolvedStatements[i];
            }
        }

        // we now check if there are undefined registers - if there are, create another dependency graph
        if(undefinedRegisters.size() > 0) {
            List<MethodExecutionPath> backwardPaths = snippet.method.getExecutionPaths(0, snippet.stringInitIndex);
            statementsSet = new ArrayList<>();
            for(MethodExecutionPath backwardPath : backwardPaths) {
                Set<Integer> statementsForPath = new HashSet<>();
                backwardPath.buildRegisterDependencyGraph();
                for(Integer undefinedRegister : undefinedRegisters) {
                    RegisterDependencyNode rootNode = backwardPath.registerDependencyGraph.activeRegister.get(undefinedRegister);
                    Set<RegisterDependencyNode> dependencies = backwardPath.registerDependencyGraph.getDependencies(rootNode);
                    statementsForPath.addAll(backwardPath.registerDependencyGraph.getInvolvedStatementsForNode(rootNode));

                    // if this undefined register depends on another undefined register, the string is probably not encrypted
                    for(Integer undefinedRegister2 : backwardPath.registerDependencyGraph.undefinedRegisters) {
                        if(dependencies.contains(new RegisterDependencyNode(undefinedRegister2, 0))) {
                            snippet.stringIsEncrypted = false;
                            snippet.finalize();
                            return;
                        }
                    }
                }
                statementsSet.add(statementsForPath);
            }

            // test whether the statement sets are the same, if so, jumps do not matter and we can exclude them
            areEqual = true;
            for(int i = 0; i < statementsSet.size() - 1; i++) {
                if(!statementsSet.get(i).equals(statementsSet.get(i + 1))) {
                    areEqual = false;
                    break;
                }
            }

            for(MethodExecutionPath backwardPath : backwardPaths) {
                for(Integer undefinedRegister : undefinedRegisters) {
                    boolean[] pathInvolvedStatements = backwardPath.computeInvolvedStatements(undefinedRegister, !areEqual);
                    for(int i = 0; i < pathInvolvedStatements.length; i++) {
                        involvedStatements[i] = involvedStatements[i] || pathInvolvedStatements[i];
                    }
                }
            }
        }

        for(int i = 0; i < involvedStatements.length; i++) {
            DexStmtNode stmtNode = snippet.method.methodNode.codeNode.stmts.get(i);
            if(involvedStatements[i]) {
                snippet.extractedStatements.add(stmtNode);
            }
        }

        snippet.finalize();

        // we check whether the snippet is not "too basic", i.e., it is not just the creation of a new string based on our encrypted string
        List<DexStmtNode> prunedStatements = snippet.getPrunedStatementsList();
        if(prunedStatements.size() == 3 && prunedStatements.get(1).op == Op.NEW_INSTANCE && prunedStatements.get(2).op == Op.INVOKE_DIRECT) {
            snippet.stringIsEncrypted = false;
        }

        if(snippet.stringIsEncrypted) {
            snippets.add(snippet);
        }
    }

    public Pair<Integer, Integer> isPotentialStringDecryption(Method method, int stringInitStmtIndex, int stmtIndex) {
        DexStmtNode stmtNode = method.methodNode.codeNode.stmts.get(stmtIndex);
        Pair<Integer, Integer> potentialStringDecryption = null;
        if(stmtNode.op == Op.INVOKE_DIRECT) {
            MethodStmtNode mnn = (MethodStmtNode) stmtNode;
            if(mnn.method.getName().equals("<init>") && mnn.method.getOwner().equals("Ljava/lang/String;")) {
                potentialStringDecryption = new ImmutablePair<>(stmtIndex, mnn.args[0]);
            }
        }
        else if(stmtNode.op == Op.INVOKE_STATIC) {
            MethodStmtNode mnn = (MethodStmtNode) stmtNode;
            if(mnn.method.getReturnType().equals("Ljava/lang/String;") && mnn.args.length > 0 && !mnn.method.getOwner().equals("Ljava/lang/String;")) {
                // get the next statement -> this should be a move-result-object
                // skip possible jump statements
                int currentStmtIndex = stmtIndex + 1;
                DexStmtNode nextStmtNode = method.methodNode.codeNode.stmts.get(currentStmtIndex);
                while(nextStmtNode.op != Op.MOVE_RESULT_OBJECT && currentStmtIndex < method.methodNode.codeNode.stmts.size()) {
                    currentStmtIndex += 1;
                    if(currentStmtIndex < method.methodNode.codeNode.stmts.size()) {
                        nextStmtNode = method.methodNode.codeNode.stmts.get(currentStmtIndex);
                    }
                }

                if(nextStmtNode.op != Op.MOVE_RESULT_OBJECT) {
                    return new ImmutablePair<>(-1, -1);
                }
                Stmt1RNode castNode = (Stmt1RNode) nextStmtNode;
                potentialStringDecryption = new ImmutablePair<>(currentStmtIndex, castNode.a);
            }
        }

        if(potentialStringDecryption == null) {
            return new ImmutablePair<>(-1, -1);
        }

        MethodSection stringInitSection = method.getSectionForStatement(stringInitStmtIndex);
        MethodSection stringDecryptSection = method.getSectionForStatement(stmtIndex);
        if(stringInitSection.sectionType == MethodSectionType.TRY_BLOCK && stringDecryptSection.sectionType == MethodSectionType.CATCH_BLOCK) {
            return new ImmutablePair<>(-1, -1);
        }

        return potentialStringDecryption;
    }

    public List<StringSnippet> getPotentialStringSnippets(Method method) {
        List<StringSnippet> snippets = new ArrayList<>();
        for (int stmtIndex = 0; stmtIndex < method.methodNode.codeNode.stmts.size(); stmtIndex++) {
            DexStmtNode stmtNode = method.methodNode.codeNode.stmts.get(stmtIndex);
            if (stmtNode.op == Op.CONST_STRING || stmtNode.op == Op.CONST_STRING_JUMBO) {
                numStrings++;
                ConstStmtNode stringInitNode = (ConstStmtNode) stmtNode;
                if(stringInitNode.value.toString().length() > 0) {
//                    System.out.println("Processing str: " + stringInitNode.value.toString());
                    Pair<Integer, Integer> pair = findPossibleStringDecryptionStatement(method, stmtIndex);
                    if(pair.getKey() != -1) {
                        StringSnippet snippet = new StringSnippet(apkPath, smaliFile, method, stmtIndex);
                        snippet.stringDecryptedIndex = pair.getKey();
                        snippet.stringResultRegister = pair.getValue();
                        snippets.add(snippet);
                    }
                }
            }
        }
        return snippets;
    }

    public Pair<Integer, Integer> findPossibleStringDecryptionStatement(Method method, int stringInitIndex) {
        LinkedList<Pair<Integer, MethodExecutionPath>> queue = new LinkedList<>();
        MethodExecutionPath firstPath = new MethodExecutionPath(method, stringInitIndex, -1);
        queue.add(new ImmutablePair<>(stringInitIndex, firstPath));

        while(!queue.isEmpty()) {
            // if there are too many items in the queue, the method is very complex; return an empty set
            if(queue.size() >= 100000) {
                return new ImmutablePair<>(-1, -1);
            }

            Pair<Integer, MethodExecutionPath> pair = queue.remove();
            int currentStmtIndex = pair.getKey();
            MethodExecutionPath currentPath = pair.getValue();
            Pair<Integer, Integer> decryptPair = isPotentialStringDecryption(method, stringInitIndex, currentStmtIndex);
            if(decryptPair.getKey() != -1 && decryptPair.getValue() != -1) {
                return decryptPair;
            }

            List<ControlFlowGraphJump> jumps = method.controlFlowGraph.getJumps(currentStmtIndex);
            for(ControlFlowGraphJump jump : jumps) {
                if(jump.jumpType == JumpType.NEXT_STATEMENT || jump.jumpType == JumpType.GOTO_STATEMENT) {
                    queue.add(new ImmutablePair<>(jump.toNode.stmtIndex, currentPath));
                }
                else {
                    JumpDecision decision = new JumpDecision(jump.fromNode.stmtIndex, jump.toNode.stmtIndex);
                    if(!currentPath.path.contains(decision)) {
                        MethodExecutionPath copied = currentPath.copy();
                        copied.path.add(decision);
                        queue.add(new ImmutablePair<>(jump.toNode.stmtIndex, copied));
                    }
                }
            }
        }
        return new ImmutablePair<>(-1, -1);
    }

    public void process() {
        for (Method method : methods) {
//            System.out.println("Processing method: " + method.getName());
            DexMethodNode methodNode = method.methodNode;
//            if(!methodNode.method.getName().equals("ch")) {
//                continue;
//            }

            method.buildCFG();

            List<StringSnippet> snippets = getPotentialStringSnippets(method);
            for(StringSnippet snippet : snippets) {
                processSnippet(snippet);
            }
        }
    }
}
