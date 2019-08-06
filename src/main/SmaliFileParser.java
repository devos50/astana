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

        if (stmtNode.op == Op.CHECK_CAST) {
            TypeStmtNode typeStmtNode = (TypeStmtNode) stmtNode;
            if(typeStmtNode.type.equals("Ljava/lang/String;")) {
                potentialStringDecryption = new ImmutablePair<>(stmtIndex, typeStmtNode.a);
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
                ConstStmtNode stringInitNode = (ConstStmtNode) stmtNode;
                if(stringInitNode.value.toString().length() > 0 && stringInitNode.value.toString().contains("MEUTPM")) {
                    numStrings++;
//                    System.out.println("Processing str: " + stringInitNode.value.toString());
                    List<Pair<Integer, Integer>> pairs = findPossibleStringDecryptionStatement(method, stmtIndex);
                    StringSnippet snippet = new StringSnippet(apkPath, smaliFile, rootNode, method, stmtIndex);
                    snippet.stringDecryptPairs = pairs;
                    snippets.add(snippet);
                }
            }
        }
        return snippets;
    }

    public List<Pair<Integer, Integer>> findPossibleStringDecryptionStatement(Method method, int stringInitIndex) {
        // find possible places where a string is decrypted.
        // to do so, we start at a string declaration, and end when another string declaration is found.
        List<MethodExecutionPath> paths = new ArrayList<>();
        LinkedList<Pair<Integer, MethodExecutionPath>> queue = new LinkedList<>();
        MethodExecutionPath firstPath = new MethodExecutionPath(method, stringInitIndex, -1);
        queue.add(new ImmutablePair<>(stringInitIndex, firstPath));

        while(!queue.isEmpty()) {
            // if there are too many items in the queue, the method is very complex; return an empty set
            if(queue.size() >= 100000) {
                break;
            }

            Pair<Integer, MethodExecutionPath> pair = queue.remove();
            int currentStmtIndex = pair.getKey();
            MethodExecutionPath currentPath = pair.getValue();

            // are we done?
            if(currentStmtIndex >= method.methodNode.codeNode.stmts.size()) {
                paths.add(currentPath);
                continue;
            }
            else {
                DexStmtNode stmtNode = method.methodNode.codeNode.stmts.get(currentStmtIndex);
                if(stmtNode.op == Op.RETURN || stmtNode.op == Op.RETURN_OBJECT || stmtNode.op == Op.RETURN_VOID || stmtNode.op == Op.RETURN_WIDE || stmtNode.op == Op.THROW) {
                    // we cannot go anywhere from this node
                    paths.add(currentPath);
                    continue;
                }

                if(stringInitIndex != currentStmtIndex && (stmtNode.op == Op.CONST_STRING || stmtNode.op == Op.CONST_STRING_JUMBO)) {
                    paths.add(currentPath);
                    continue;
                }
            }

            // if this is a potential string decryption, keep track of it
            Pair<Integer, Integer> decryptPair = isPotentialStringDecryption(method, stringInitIndex, currentStmtIndex);
            if(decryptPair.getKey() != -1 && decryptPair.getValue() != -1) {
                currentPath.potentialStringDecryptionStatements.add(decryptPair);
            }

            List<ControlFlowGraphJump> jumps = method.controlFlowGraph.getJumps(currentStmtIndex);
            for(ControlFlowGraphJump jump : jumps) {
                if(jump.jumpType == JumpType.NEXT_STATEMENT || jump.jumpType == JumpType.GOTO_STATEMENT) {
                    // check if we might be in an infinite loop
                    if(currentPath.lastGotosTaken.size() > 5) {
                        boolean areEqual = true;
                        for(int i = currentPath.lastGotosTaken.size() - 1; i >= currentPath.lastGotosTaken.size() - 5; i--) {
                            if(currentPath.lastGotosTaken.get(i) != currentStmtIndex) {
                                areEqual = false;
                                break;
                            }
                        }

                        if(areEqual) {
                            continue; // don't add this state - we are likely in an infinite loop
                        }
                    }

                    if(jump.jumpType == JumpType.GOTO_STATEMENT) {
                        currentPath.lastGotosTaken.add(currentStmtIndex);
                    }
                    queue.add(new ImmutablePair<>(jump.toNode.stmtIndex, currentPath));
                }
                else {
                    JumpDecision decision = new JumpDecision(jump.fromNode.stmtIndex, jump.toNode.stmtIndex, jump.jumpType);
                    if(!currentPath.path.contains(decision) && currentPath.path.size() < 4) {
                        MethodExecutionPath copied = currentPath.copy();
                        copied.path.add(decision);
                        queue.add(new ImmutablePair<>(jump.toNode.stmtIndex, copied));
                    }
                }
            }
        }

        if(paths.size() == 0) {
            return new ArrayList<>();
        }

        // determine whether there are common elements - if so, take the latest
        Set<Pair<Integer, Integer>> intersection = null;
        for(MethodExecutionPath path : paths) {
            if(path.containsExceptionJump()) {
                continue; // ignore this path - we only consider "happy flow" paths
            }

            if(intersection == null) {
                intersection = new HashSet<>(path.potentialStringDecryptionStatements);
            }
            else {
                intersection.retainAll(path.potentialStringDecryptionStatements);
            }
        }

        List<Pair<Integer, Integer>> intersectionList = new ArrayList<>(intersection);
        Collections.sort(intersectionList);
        Collections.reverse(intersectionList);
        return intersectionList;
    }

    public void process() {
        for (Method method : methods) {
//            System.out.println("Processing method: " + method.getName());
            DexMethodNode methodNode = method.methodNode;
            if(!methodNode.method.getName().equals("zfd")) {
                continue;
            }

            method.buildCFG();

            List<StringSnippet> methodSnippets = getPotentialStringSnippets(method);
            snippets.addAll(methodSnippets);
        }
    }
}
