import com.googlecode.d2j.node.insn.ConstStmtNode;
import com.googlecode.d2j.node.insn.DexStmtNode;
import com.googlecode.d2j.reader.Op;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class MethodExecutionPath {

    public Method method;
    public int stringInitIndex;
    public int stringDecryptIndex;
    public int stringResultRegister;
    public List<MethodSectionJump> path = new ArrayList<>();
    public RegisterDependencyGraph registerDependencyGraph;
    public boolean[] involvedStatements;

    public MethodExecutionPath(Method method, int stringInitIndex, int stringDecryptIndex, int stringResultRegister) {
        this.method = method;
        this.stringInitIndex = stringInitIndex;
        this.stringDecryptIndex = stringDecryptIndex;
        this.stringResultRegister = stringResultRegister;
        this.involvedStatements = new boolean[this.method.methodNode.codeNode.stmts.size()];
    }

    public MethodExecutionPath copy() {
        MethodExecutionPath copy = new MethodExecutionPath(this.method, this.stringInitIndex, this.stringDecryptIndex, this.stringResultRegister);
        copy.path.addAll(this.path);
        return copy;
    }

    public void buildRegisterDependencyGraph() {
        this.registerDependencyGraph = new RegisterDependencyGraph(this);
        this.registerDependencyGraph.build();
    }

    public void computeInvolvedStatements() {
        if(this.registerDependencyGraph == null) {
            throw new RuntimeException("register dependency graph should be computed first!");
        }

        // perform a BFS from the statement where the string is possibly decrypted, to the init method
        DexStmtNode lastStmtNode = method.methodNode.codeNode.stmts.get(stringDecryptIndex);
        if(lastStmtNode.op != Op.MOVE_RESULT_OBJECT && lastStmtNode.op != Op.INVOKE_DIRECT) {
            throw new RuntimeException("Pruning: last node not move-result-object or invoke-direct!");
        }

        RegisterDependencyNode rootNode = registerDependencyGraph.activeRegister.get(stringResultRegister);

        List<RegisterDependencyNode> visited = new ArrayList<>();
        LinkedList<RegisterDependencyNode> queue = new LinkedList<>();
        visited.add(rootNode);
        queue.add(rootNode);

        while(!queue.isEmpty()) {
            RegisterDependencyNode currentNode = queue.remove();
            List<RegisterDependencyNode> adjacent = registerDependencyGraph.adjacency.get(currentNode);

            if(adjacent == null) {
                // it seems we are missing variables
                // TODO: assume that the string is not encrypted!
                System.out.println("MISSING VARIABLES!!!!!!");
                return;
            }

            for(RegisterDependencyNode adjacentNode : adjacent) {
                if(!visited.contains(adjacentNode)) {
                    visited.add(adjacentNode);
                    queue.add(adjacentNode);
                }
            }
        }

        // check whether the original string declaration is visited. If not, this string is probably not encrypted
        ConstStmtNode stringInitNode = (ConstStmtNode) method.methodNode.codeNode.stmts.get(stringInitIndex);
        RegisterDependencyNode stringNode = new RegisterDependencyNode(stringInitNode.a, 1);
        if(!visited.contains(stringNode)) {
            return;
        }

        // get all statements that are involved
        for(RegisterDependencyNode visitedNode : visited) {
            for(int i = 0; i < registerDependencyGraph.statementToRegister.length; i++) {
                if(registerDependencyGraph.statementToRegister[i] == visitedNode) {
                    involvedStatements[i] = true;
                }
            }
        }
    }
}
