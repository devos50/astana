import com.googlecode.d2j.DexLabel;
import com.googlecode.d2j.node.DexMethodNode;
import com.googlecode.d2j.node.insn.ConstStmtNode;
import com.googlecode.d2j.node.insn.DexStmtNode;

import java.util.*;

public class SmaliFileParserState {

    public final DexMethodNode methodNode;
    public final StringSnippet snippet;
    public int stringInitStatementIndex;
    public int currentStatementIndex;
    public Set<Integer> definedVariables;
    public ConstStmtNode stringInitNode;
    public boolean foundDecryptedString = false;
    public Map<DexStmtNode, DexLabel> jumpDecisions = new HashMap<>();
    public List<DexStmtNode> statements;
    public int stringResultRegister = 0;

    public SmaliFileParserState(StringSnippet snippet, DexMethodNode methodNode, int stringInitStatementIndex) {
        this.stringInitStatementIndex = stringInitStatementIndex;
        this.currentStatementIndex = stringInitStatementIndex;
        this.methodNode = methodNode;
        this.snippet = snippet;
        this.definedVariables = new HashSet<>();
        this.stringInitNode = (ConstStmtNode) methodNode.codeNode.stmts.get(stringInitStatementIndex);
        this.statements = new ArrayList<>();
    }

    public DexStmtNode advanceStatement() {
        this.currentStatementIndex++;
        if(this.currentStatementIndex < this.methodNode.codeNode.stmts.size()) {
            return this.methodNode.codeNode.stmts.get(this.currentStatementIndex);
        }
        return null;
    }

    public SmaliFileParserState copy() {
        SmaliFileParserState copy = new SmaliFileParserState(this.snippet, this.methodNode, this.stringInitStatementIndex);
        copy.definedVariables.addAll(definedVariables);
        copy.statements.addAll(statements);
        return copy;
    }
}
