import com.googlecode.d2j.DexLabel;
import com.googlecode.d2j.node.DexClassNode;
import com.googlecode.d2j.node.DexMethodNode;
import com.googlecode.d2j.node.insn.*;
import com.googlecode.d2j.reader.Op;
import com.googlecode.d2j.smali.Smali;

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

    public ArrayList<SmaliFileParserState> processState(ArrayList<SmaliFileParserState> states, SmaliFileParserState currentState) {
        System.out.println("Going to process state!");
        DexStmtNode currentNode = currentState.methodNode.codeNode.stmts.get(currentState.currentStatementIndex);
        while(currentNode != null) {
            System.out.println("Processing statement: " + currentNode.op);
            // if we are returning the string, bail out
            // TODO we assume registers are not edited in-between! We need to better track the string...
            if(currentNode.op == Op.RETURN_OBJECT) {
                Stmt1RNode castNode = (Stmt1RNode) currentNode;
                if(castNode.a == currentState.stringInitNode.a) {
                    break;
                }
            }

            // TODO process GOTO statements correctly!

            if(currentNode instanceof JumpStmtNode) {
                // we found a jump -> make a decision: follow it or not
                JumpStmtNode jumpStmtNode = (JumpStmtNode) currentNode;
                SmaliFileParserState newState = currentState.copy();

                // find the point where we are jumping to
                int jumpIndex = getIndexForLabel(jumpStmtNode.label, currentState.methodNode);
                newState.currentStatementIndex = jumpIndex + 1; // we want to jump to the statement after the label

                // add the decision that we made
                newState.jumpDecisions.put(jumpStmtNode, true);

                // process this state
                states.addAll(processState(states, newState));
            }

            // if we reach return-void (without having reached a location where a string is made), bail out
            if(currentNode.op == Op.RETURN_VOID || currentNode.op == Op.RETURN_OBJECT || currentNode.op == Op.THROW) {
                break;
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
                    // TODO: assume this variable is set with a const statement (it could ofcourse also be a return value of a function)
                    for(int arg : mnn.args) {
                        if(!currentState.definedVariables.contains(arg)) {
                            // look for this variable
                            for(int currentBackIndex = currentState.stringInitStatementIndex - 1; currentBackIndex >= 0; currentBackIndex--) {
                                DexStmtNode currentStmtNode = currentState.methodNode.codeNode.stmts.get(currentBackIndex);
                                if(currentStmtNode instanceof ConstStmtNode) {
                                    ConstStmtNode currentConstStmtNode = (ConstStmtNode) currentStmtNode;
                                    if(currentConstStmtNode.a == arg) {
                                        currentState.statements.add(1, currentStmtNode); // add it after the const-string definition
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    // we want to move this result to a register
                    Stmt1RNode moveStmtNode = new Stmt1RNode(Op.MOVE_RESULT_OBJECT, 1);
                    currentState.statements.add(moveStmtNode);
                    currentState.stringResultRegister = 1;
                    currentState.foundDecryptedString = true;
                    return null;
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
        ArrayList<SmaliFileParserState> states = processState(new ArrayList<>(), startState);

        // TODO we take the first eligible state, should probably be smarter here
        SmaliFileParserState best = null;
        for(SmaliFileParserState state : states) {
            System.out.println("FOUND DECRYPTED? " + state.foundDecryptedString);
            System.out.println(state.statements);
            if(state.statements.size() <= 2) {
                continue;
            }
            if(state.foundDecryptedString) {
                best = state;
                break;
            }
        }

        System.out.println(states);

        if(best != null) {
            snippet.statements = best.statements;
            snippet.finalize();
            return snippet;
        }
        return null;
    }

    public void process() throws FileNotFoundException {
        //System.out.println("Processing file: " + smaliFile.getPath());
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
                    if(stringInitNode.value.toString().length() > 0 && stringInitNode.value.toString().contains("ASGSAz")) {
                        StringSnippet snippet = this.processString(methodNode, stmtIndex);
                        if(snippet != null) {
                            this.snippets.add(snippet);
                        }
                    }
                }
            }
        }
    }
}
