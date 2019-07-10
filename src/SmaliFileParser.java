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
import java.util.ArrayList;
import java.util.List;

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
        int currentStatementIndex = statementIndex;
        List<DexLabel> jumpedTo = new ArrayList<>();

        while(true) {
            if(currentStatementIndex >= methodNode.codeNode.stmts.size()) {
                break;
            }

            DexStmtNode currentNode = methodNode.codeNode.stmts.get(currentStatementIndex);

            // if we are returning the string, bail out
            // TODO we assume registers are not edited!
            if(currentNode.op == Op.RETURN_OBJECT) {
                Stmt1RNode castNode = (Stmt1RNode) currentNode;
                if(castNode.a == stringInitNode.a) {
                    return null;
                }
            }

            if(currentNode.op == Op.GOTO) {
                JumpStmtNode jumpNode = (JumpStmtNode) currentNode;

                // we should find the label in the method
                int jumpIndex = -1;
                DexLabel jumpLabel = null;
                for(int i = 0; i < methodNode.codeNode.stmts.size(); i++) {
                    DexStmtNode loopStmtNode = methodNode.codeNode.stmts.get(i);
                    if(loopStmtNode instanceof DexLabelStmtNode) {
                        DexLabelStmtNode labelLoopStmtNode = (DexLabelStmtNode) loopStmtNode;
                        if(labelLoopStmtNode.label == jumpNode.label) {
                            jumpIndex = i;
                            jumpLabel = labelLoopStmtNode.label;
                            break;
                        }
                    }
                }

                if(jumpIndex != -1 && jumpedTo.contains(jumpLabel)) {
                    // we already jumped to here, ignore it
                    currentStatementIndex++;
                    continue;
                }
                else if(jumpIndex != -1) {
                    currentStatementIndex = jumpIndex;
                    jumpedTo.add(jumpLabel);
                    continue;
                }
                else {
                    snippet.statements.add(currentNode);
                    break;
                }
            }

            // if we reach return-void (without having reached a location where a string is made), bail out
            if(currentNode.op == Op.RETURN_VOID || currentNode.op == Op.RETURN_OBJECT || currentNode.op == Op.THROW) {
                return null;
            }

            if(!(currentNode instanceof DexLabelStmtNode)) {
                snippet.statements.add(currentNode);
            }

            if(currentNode.op == Op.INVOKE_DIRECT) {
                MethodStmtNode mnn = (MethodStmtNode) currentNode;
                if(mnn.method.getName().equals("<init>") && mnn.method.getOwner().equals("Ljava/lang/String;")) {
                    break;
                }
            }
            else if(currentNode.op == Op.INVOKE_STATIC) {
                MethodStmtNode mnn = (MethodStmtNode) currentNode;
                if(mnn.method.getReturnType().equals("Ljava/lang/String;") && mnn.args.length > 0) {
                    // we want to move this result to a register
                    Stmt1RNode moveStmtNode = new Stmt1RNode(Op.MOVE_RESULT_OBJECT, 1);
                    snippet.statements.add(moveStmtNode);
                    break;
                }
            }

            currentStatementIndex += 1;
        }

        if(snippet.statements.size() <= 2) { return null; }

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
                if (stmtNode.op == Op.CONST_STRING) {
                    ConstStmtNode stringInitNode = (ConstStmtNode) stmtNode;
                    if(stringInitNode.value.toString().length() > 0) {
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
