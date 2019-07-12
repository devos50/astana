import ammonite.interp.Pressy;
import com.googlecode.d2j.DexLabel;
import com.googlecode.d2j.node.DexClassNode;
import com.googlecode.d2j.node.DexMethodNode;
import com.googlecode.d2j.node.insn.*;
import com.googlecode.d2j.reader.Op;
import com.googlecode.d2j.smali.Smali;
import org.bouncycastle.util.Pack;

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
//            System.out.println("Processing statement: " + currentNode.op);
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
                // if we already did the jump, bail out
                JumpStmtNode jumpStmtNode = (JumpStmtNode) currentNode;
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

            if(currentNode instanceof ConstStmtNode) {
                ConstStmtNode constStmtNode = (ConstStmtNode) currentNode;
                currentState.definedVariables.add(constStmtNode.a);
            }

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
                    // we found a static invocation that returns a string. Find the variables that are not defined yet and look for them

                    boolean resolvedVariables = true;

                    // TODO: assume this variable is set with a const statement (it could ofcourse also be a return value of a function)
                    if(mnn.args.length == mnn.method.getParameterTypes().length) {
                        Set<Integer> missingVariables = new HashSet<>();
                        Map<Integer, Integer> variableIndex = new HashMap<>();
                        for(int argIndex = 0; argIndex < mnn.args.length; argIndex++) {
                            int arg = mnn.args[argIndex];
                            if(!currentState.definedVariables.contains(arg)) {
                                missingVariables.add(arg);
                                variableIndex.put(arg, argIndex);
                            }
                        }

                        for(int arg : missingVariables) {
                            String argType = mnn.method.getParameterTypes()[variableIndex.get(arg)];
                            if(!currentState.definedVariables.contains(arg)) {
                                // look for this variable
                                boolean found = false;
                                for(int currentBackIndex = currentState.stringInitStatementIndex - 1; currentBackIndex >= 0; currentBackIndex--) {
                                    DexStmtNode currentStmtNode = currentState.methodNode.codeNode.stmts.get(currentBackIndex);
                                    if(currentStmtNode instanceof ConstStmtNode) {
                                        ConstStmtNode currentConstStmtNode = (ConstStmtNode) currentStmtNode;
                                        if(currentConstStmtNode.a == arg) {
                                            if(argType.equals("Ljava/lang/String;") && (currentConstStmtNode.op == Op.CONST_STRING || currentConstStmtNode.op == Op.CONST_STRING_JUMBO)) {
                                                currentState.statements.add(1, currentStmtNode); // add it after the const-string definition
                                                found = true;
                                                break;
                                            }
                                            else if(argType.equals("C") && (currentConstStmtNode.op == Op.CONST_4 || currentConstStmtNode.op == Op.CONST_16)) {
                                                currentState.statements.add(1, currentStmtNode); // add it after the const-string definition
                                                found = true;
                                                break;
                                            }
                                        }
                                    }
                                }

                                if(!found) {
                                    resolvedVariables = false;
                                }
                            }
                        }
                    }

                    if(!resolvedVariables) {
                        // we could not resolve all variables, this string is probably not encrypted
                        return states;
                    }

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

    public StringSnippet processString(DexMethodNode methodNode, int statementIndex) {
        ConstStmtNode stringInitNode = (ConstStmtNode) methodNode.codeNode.stmts.get(statementIndex);
        StringSnippet snippet = new StringSnippet(this.smaliFile, methodNode, stringInitNode);
        SmaliFileParserState startState = new SmaliFileParserState(snippet, methodNode, statementIndex);
        ArrayList<SmaliFileParserState> states = processState(new ArrayList<>(), startState, 0);

        SmaliFileParserState best = null;
        int bestLength = 1000000;
        for(SmaliFileParserState state : states) {
            if(state.statements.size() <= 2) {
                continue;
            }
            if(state.foundDecryptedString && state.statements.size() < bestLength) {
                best = state;
                bestLength = state.statements.size();
            }
        }

        if(best != null) {
            snippet.statements = best.statements;
            snippet.stringResultRegister = best.stringResultRegister;
            boolean result = snippet.finalizeSnippet();
            if(result) { return snippet; }
        }
        return null;
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
            //System.out.println("Method: " + methodNode.method.getName());
            for (int stmtIndex = 0; stmtIndex < methodNode.codeNode.stmts.size(); stmtIndex++) {
                DexStmtNode stmtNode = methodNode.codeNode.stmts.get(stmtIndex);
                if (stmtNode.op == Op.CONST_STRING || stmtNode.op == Op.CONST_STRING_JUMBO) {
                    ConstStmtNode stringInitNode = (ConstStmtNode) stmtNode;
                    if(stringInitNode.value.toString().length() > 0) {
//                        System.out.println("Processing str: " + stringInitNode.value.toString());
                        StringSnippet snippet = this.processString(methodNode, stmtIndex);
                        if(snippet != null) {
                            this.snippets.add(snippet);
                        }
                        //System.out.println("Snippets: " + snippets.size());
                    }
                }
            }
        }
    }
}
