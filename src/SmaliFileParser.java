import com.googlecode.d2j.DexLabel;
import com.googlecode.d2j.node.DexClassNode;
import com.googlecode.d2j.node.DexMethodNode;
import com.googlecode.d2j.node.insn.*;
import com.googlecode.d2j.reader.Op;
import com.googlecode.d2j.smali.Smali;
import javafx.util.Pair;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.*;

public class SmaliFileParser {

    private final File smaliFile;
    private DexClassNode rootNode;
    public List<StringSnippet> snippets = new ArrayList<>();

    public SmaliFileParser(File smaliFile) {
        this.smaliFile = smaliFile;
    }

    public int getIndexForLabel(DexLabel label, DexMethodNode methodNode) {
        for(int index = 0; index < methodNode.codeNode.stmts.size(); index++) {
            DexStmtNode loopNode = methodNode.codeNode.stmts.get(index);
            if(loopNode instanceof DexLabelStmtNode) {
                DexLabelStmtNode labelNode = (DexLabelStmtNode) loopNode;
                if(labelNode.label == label) {
                    return index;
                }
            }
        }
        return -1;
    }

    public ArrayList<SmaliFileParserState> processState(ArrayList<SmaliFileParserState> states, SmaliFileParserState currentState, int level) {
        if(level == 5) {
            // don't recurse too deep
            return states;
        }

        //System.out.println("Level: " + level);
        //System.out.println("jumps: " + currentState.jumps.size());
        DexStmtNode currentNode = currentState.methodNode.codeNode.stmts.get(currentState.currentStatementIndex);
        while(currentNode != null) {
            System.out.println("Processing statement: " + currentNode.op);
//            if(currentNode instanceof ConstStmtNode) {
//                ConstStmtNode cast = (ConstStmtNode) currentNode;
//                System.out.println(cast.value);
//            }
            // if we are returning the string, bail out
            // TODO we assume registers are not edited in-between! We need to better track the string...
            if(currentNode.op == Op.RETURN_OBJECT) {
                Stmt1RNode castNode = (Stmt1RNode) currentNode;
                if(castNode.a == currentState.stringInitNode.a) {
                    return states;
                }
            }

            if(currentNode.op == Op.GOTO || currentNode.op == Op.GOTO_16 || currentNode.op == Op.GOTO_32) {
                JumpStmtNode jumpStmtNode = (JumpStmtNode) currentNode;

                // if we already did the jump, bail out
                if(currentState.jumps.contains(jumpStmtNode.label)) {
                    return states;
                }

                // follow the goto statement
                currentState.jumps.add(jumpStmtNode.label);
                currentNode = currentState.advanceStatement();
                continue;
            }

            if(currentNode instanceof JumpStmtNode) {
                // we found a jump -> make a decision: follow it or not

                // have we already made this jump? If so, do not consider it
                JumpStmtNode jumpStmtNode = (JumpStmtNode) currentNode;
                if(!currentState.jumps.contains(jumpStmtNode.label)) {
                    SmaliFileParserState newState = currentState.copy();

                    // find the point where we are jumping to
                    int jumpIndex = getIndexForLabel(jumpStmtNode.label, currentState.methodNode);
                    newState.currentStatementIndex = jumpIndex + 1; // we want to jump to the statement after the label

                    // add the decision that we made
                    newState.jumps.add(jumpStmtNode.label);

                    // process this state
                    ArrayList<SmaliFileParserState> finalStates = processState(new ArrayList<>(), newState, level + 1);
                    states.addAll(finalStates);
                }
            }

            if(currentNode instanceof PackedSwitchStmtNode) {
                PackedSwitchStmtNode packedSwitchStmtNode = (PackedSwitchStmtNode) currentNode;
                for(DexLabel label : packedSwitchStmtNode.labels) {
                    if(!currentState.jumps.contains(label)) {
                        // we found a jump -> make a decision: follow it or not
                        SmaliFileParserState newState = currentState.copy();

                        // find the point where we are jumping to
                        int jumpIndex = getIndexForLabel(label, currentState.methodNode);
                        newState.currentStatementIndex = jumpIndex + 1; // we want to jump to the statement after the label

                        // add the decision that we made
                        newState.jumps.add(label);

                        // process this state
                        ArrayList<SmaliFileParserState> finalStates = processState(new ArrayList<>(), newState, level + 1);
                        states.addAll(finalStates);
                    }
                }
                break; // TODO do we really have to follow one of the labels? Or can we continue (default switch case)?
            }

            // if we reach return-void (without having reached a location where a string is made), bail out
            if(currentNode.op == Op.RETURN_VOID || currentNode.op == Op.RETURN_OBJECT || currentNode.op == Op.THROW) {
                return states;
            }

            currentState.statements.add(currentNode);

            if(currentNode.op == Op.INVOKE_DIRECT) {
                MethodStmtNode mnn = (MethodStmtNode) currentNode;
                if(mnn.method.getName().equals("<init>") && mnn.method.getOwner().equals("Ljava/lang/String;")) {
                    currentState.stringResultRegister = mnn.args[0];
                    currentState.foundDecryptedString = true;
                    break;
                }
            }
            else if(currentNode.op == Op.INVOKE_STATIC) {
                MethodStmtNode mnn = (MethodStmtNode) currentNode;
                if(mnn.method.getReturnType().equals("Ljava/lang/String;") && mnn.args.length > 0) {
                    // we want to move this result to a register
                    Stmt1RNode moveStmtNode = new Stmt1RNode(Op.MOVE_RESULT_OBJECT, 1);
                    currentState.statements.add(moveStmtNode);
                    currentState.stringResultRegister = 1;
                    currentState.foundDecryptedString = true;
                    break;
                }
            }

            currentNode = currentState.advanceStatement();
        }

        states.add(currentState);
        return states;
    }

//    public StringSnippet processString(DexMethodNode methodNode, int statementIndex) {
//        ConstStmtNode stringInitNode = (ConstStmtNode) methodNode.codeNode.stmts.get(statementIndex);
//        StringSnippet snippet = new StringSnippet(this.smaliFile, methodNode, statementIndex);
//        SmaliFileParserState startState = new SmaliFileParserState(snippet, methodNode, statementIndex);
//        ArrayList<SmaliFileParserState> states = processState(new ArrayList<>(), startState, 0);
//
//        SmaliFileParserState best = null;
//        int bestLength = 1000000;
//        for(SmaliFileParserState state : states) {
//            if(state.statements.size() <= 2) {
//                continue;
//            }
//            if(state.foundDecryptedString && state.statements.size() < bestLength) {
//                best = state;
//                bestLength = state.statements.size();
//            }
//        }
//
//        if(best != null) {
//            snippet.statements = best.statements;
//            snippet.stringResultRegister = best.stringResultRegister;
//            boolean result = snippet.finalizeSnippet();
//            if(result) { return snippet; }
//        }
//        return null;
//    }

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

        System.out.println(Arrays.toString(involvedStatements));
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
