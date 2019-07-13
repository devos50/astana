import com.googlecode.d2j.DexLabel;
import com.googlecode.d2j.node.DexMethodNode;
import com.googlecode.d2j.node.insn.ConstStmtNode;
import com.googlecode.d2j.node.insn.DexLabelStmtNode;
import com.googlecode.d2j.node.insn.DexStmtNode;
import com.googlecode.d2j.node.insn.JumpStmtNode;
import com.googlecode.d2j.reader.Op;

import java.util.*;

public class SmaliFileParserState {

    public final DexMethodNode methodNode;
    public final StringSnippet snippet;
    public int stringInitStatementIndex;
    public int currentStatementIndex;
    public ConstStmtNode stringInitNode;
    public boolean foundDecryptedString = false;
    public Set<DexLabel> jumps = new HashSet<>();
    public List<DexStmtNode> statements;
    public int stringResultRegister = 0;

    public SmaliFileParserState(StringSnippet snippet, DexMethodNode methodNode, int stringInitStatementIndex) {
        this.stringInitStatementIndex = stringInitStatementIndex;
        this.currentStatementIndex = stringInitStatementIndex;
        this.methodNode = methodNode;
        this.snippet = snippet;
        this.stringInitNode = (ConstStmtNode) methodNode.codeNode.stmts.get(stringInitStatementIndex);
        this.statements = new ArrayList<>();
    }

    public DexStmtNode advanceStatement() {
        DexStmtNode currentNode = methodNode.codeNode.stmts.get(currentStatementIndex);
        if(currentNode.op == Op.GOTO || currentNode.op == Op.GOTO_16 || currentNode.op == Op.GOTO_32) {
            JumpStmtNode jumpStmtNode = (JumpStmtNode) currentNode;
            // find where to jump to
            for(int index = 0; index < methodNode.codeNode.stmts.size(); index++) {
                DexStmtNode loopNode = methodNode.codeNode.stmts.get(index);
                if(loopNode instanceof DexLabelStmtNode) {
                    DexLabelStmtNode loopLabelNode = (DexLabelStmtNode) loopNode;
                    if(loopLabelNode.label == jumpStmtNode.label) {
                        this.currentStatementIndex = index;
                        return this.methodNode.codeNode.stmts.get(this.currentStatementIndex);
                    }
                }
            }
        }
        else {
            this.currentStatementIndex++;
            if(this.currentStatementIndex < this.methodNode.codeNode.stmts.size()) {
                return this.methodNode.codeNode.stmts.get(this.currentStatementIndex);
            }
        }
        return null;
    }

    public SmaliFileParserState copy() {
        SmaliFileParserState copy = new SmaliFileParserState(this.snippet, this.methodNode, this.stringInitStatementIndex);
        copy.statements.addAll(statements);
        copy.jumps.addAll(jumps);
        return copy;
    }
}
