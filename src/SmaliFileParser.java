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

    public StringSnippet processString(DexMethodNode methodNode, int statementIndex) {
        ConstStmtNode stringInitNode = (ConstStmtNode) methodNode.codeNode.stmts.get(statementIndex);
        StringSnippet snippet = new StringSnippet(this.smaliFile, methodNode, stringInitNode);
        List<DexLabel> sectionsToInclude = new ArrayList<>();
        int stringInitStatementIndex = statementIndex;
        int currentStatementIndex = statementIndex;
        List<DexLabel> jumpedTo = new ArrayList<>();
        Set<Integer> definedVariables = new HashSet<>();

        while(true) {
            if(currentStatementIndex >= methodNode.codeNode.stmts.size()) {
                break;
            }

            DexStmtNode currentNode = methodNode.codeNode.stmts.get(currentStatementIndex);

            // if we are returning the string, bail out
            // TODO we assume registers are not edited in-between! We need to better track the string...
            if(currentNode.op == Op.RETURN_OBJECT) {
                Stmt1RNode castNode = (Stmt1RNode) currentNode;
                if(castNode.a == stringInitNode.a) {
                    return null;
                }
            }

            if(currentNode.op == Op.PACKED_SWITCH) {
                PackedSwitchStmtNode switchNode = (PackedSwitchStmtNode) currentNode;

                if(!definedVariables.contains(switchNode.a)) {
                    // find it
                    for(int currentBackIndex = stringInitStatementIndex - 1; currentBackIndex >= 0; currentBackIndex--) {
                        DexStmtNode currentStmtNode = methodNode.codeNode.stmts.get(currentBackIndex);
                        if(currentStmtNode instanceof ConstStmtNode) {
                            ConstStmtNode currentConstStmtNode = (ConstStmtNode) currentStmtNode;
                            if(currentConstStmtNode.a == switchNode.a) {
                                snippet.statements.add(1, currentStmtNode); // add it after the const-string definition
                                break;
                            }
                        }
                    }
                }

                for(DexLabel label : switchNode.labels) { sectionsToInclude.add(label); }
            }

//            if(currentNode.op == Op.GOTO) {
//                JumpStmtNode jumpNode = (JumpStmtNode) currentNode;
//
//                // we should find the label in the method
//                int jumpIndex = -1;
//                DexLabel jumpLabel = null;
//                for(int i = 0; i < methodNode.codeNode.stmts.size(); i++) {
//                    DexStmtNode loopStmtNode = methodNode.codeNode.stmts.get(i);
//                    if(loopStmtNode instanceof DexLabelStmtNode) {
//                        DexLabelStmtNode labelLoopStmtNode = (DexLabelStmtNode) loopStmtNode;
//                        if(labelLoopStmtNode.label == jumpNode.label) {
//                            jumpIndex = i;
//                            jumpLabel = labelLoopStmtNode.label;
//                            break;
//                        }
//                    }
//                }
//
//                if(jumpIndex != -1 && jumpedTo.contains(jumpLabel)) {
//                    // we already jumped to here, ignore it
//                    currentStatementIndex++;
//                    continue;
//                }
//                else if(jumpIndex != -1) {
//                    currentStatementIndex = jumpIndex;
//                    jumpedTo.add(jumpLabel);
//                    continue;
//                }
//                else {
//                    snippet.statements.add(currentNode);
//                    break;
//                }
//            }

            // if we reach return-void (without having reached a location where a string is made), bail out
            if(currentNode.op == Op.RETURN_VOID || currentNode.op == Op.RETURN_OBJECT || currentNode.op == Op.THROW) {
                return null;
            }

            snippet.statements.add(currentNode);

            if(currentNode instanceof ConstStmtNode) {
                ConstStmtNode constStmtNode = (ConstStmtNode) currentNode;
                definedVariables.add(constStmtNode.a);
            }

            if(currentNode.op == Op.INVOKE_DIRECT) {
                MethodStmtNode mnn = (MethodStmtNode) currentNode;
                if(mnn.method.getName().equals("<init>") && mnn.method.getOwner().equals("Ljava/lang/String;")) {
                    snippet.stringResultRegister = mnn.args[0];
                    break;
                }
            }
            else if(currentNode.op == Op.INVOKE_STATIC) {
                MethodStmtNode mnn = (MethodStmtNode) currentNode;
                if(mnn.method.getReturnType().equals("Ljava/lang/String;") && mnn.args.length > 0) {
                    // we found a static invocation that returns a string. Find the variables that are not defined yet and look for them
                    // TODO: assume this variable is set with a const statement (it could ofcourse also be a return value of a function)
                    for(int arg : mnn.args) {
                        if(!definedVariables.contains(arg)) {
                            // look for this variable
                            for(int currentBackIndex = stringInitStatementIndex - 1; currentBackIndex >= 0; currentBackIndex--) {
                                DexStmtNode currentStmtNode = methodNode.codeNode.stmts.get(currentBackIndex);
                                if(currentStmtNode instanceof ConstStmtNode) {
                                    ConstStmtNode currentConstStmtNode = (ConstStmtNode) currentStmtNode;
                                    if(currentConstStmtNode.a == arg) {
                                        snippet.statements.add(1, currentStmtNode); // add it after the const-string definition
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    // we want to move this result to a register
                    Stmt1RNode moveStmtNode = new Stmt1RNode(Op.MOVE_RESULT_OBJECT, 1);
                    snippet.statements.add(moveStmtNode);
                    snippet.stringResultRegister = 1;
                    break;
                }
            }

            currentStatementIndex += 1;
        }

        if(snippet.statements.size() <= 2) {
            return null;
        }

        // we might still have to include a few sections (e.g., when there is a switch)
        for(DexLabel includeLabel : sectionsToInclude) {
            for(int i = 0; i < methodNode.codeNode.stmts.size(); i++) {

            }
        }

        System.out.println(snippet.statements);

        snippet.finalize();
        return snippet;
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
                    if(stringInitNode.value.toString().length() > 0 && stringInitNode.value.toString().contains("n1A=")) {
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
