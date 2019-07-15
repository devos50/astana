import com.googlecode.d2j.node.insn.ConstStmtNode;
import com.googlecode.d2j.node.insn.DexStmtNode;
import com.googlecode.d2j.reader.Op;
import sun.jvm.hotspot.asm.Register;

import java.util.*;

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

    public Set<RegisterDependencyNode> getDependenciesForRegister(RegisterDependencyNode rootNode) {
        Set<RegisterDependencyNode> visited = new HashSet<>();
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
                return null;
            }

            for(RegisterDependencyNode adjacentNode : adjacent) {
                if(!visited.contains(adjacentNode)) {
                    visited.add(adjacentNode);
                    queue.add(adjacentNode);
                }
            }
        }

        return visited;
    }

    public void computeInvolvedStatements() {
        if(this.registerDependencyGraph == null) {
            throw new RuntimeException("register dependency graph should be computed first!");
        }

        // first, include obvious statements, like the sections we visit in this path
        MethodSection stringInitSection = method.getSectionForStatement(stringInitIndex);
        this.involvedStatements[stringInitSection.beginIndex - 1] = true; // label declaration
        for(MethodSectionJump jump : path) {
            int fromSectionLabelStmtIndex = jump.fromSection.beginIndex - 1;
            int toSectionLabelStmtIndex = jump.toSection.beginIndex - 1;
            this.involvedStatements[fromSectionLabelStmtIndex] = true;
            this.involvedStatements[toSectionLabelStmtIndex] = true;
            this.involvedStatements[jump.jumpStmtIndex] = true;
        }

        DexStmtNode lastStmtNode = method.methodNode.codeNode.stmts.get(stringDecryptIndex);
        if(lastStmtNode.op != Op.MOVE_RESULT_OBJECT && lastStmtNode.op != Op.INVOKE_DIRECT) {
            throw new RuntimeException("Pruning: last node not move-result-object or invoke-direct!");
        }

        // perform a BFS from the statement where the string is possibly decrypted, to the init method
        RegisterDependencyNode rootNode = registerDependencyGraph.activeRegister.get(stringResultRegister);
        Set<RegisterDependencyNode> involvedRegisters = getDependenciesForRegister(rootNode);

        // also include dependencies for the jumps
        for(MethodSectionJump jump : path) {
            for(RegisterDependencyNode node : registerDependencyGraph.statementToRegister.get(jump.jumpStmtIndex)) {
                involvedRegisters.addAll(getDependenciesForRegister(node));
            }
        }

        // check whether the original string declaration is visited. If not, this string is probably not encrypted
        ConstStmtNode stringInitNode = (ConstStmtNode) method.methodNode.codeNode.stmts.get(stringInitIndex);
        RegisterDependencyNode stringNode = new RegisterDependencyNode(stringInitNode.a, 1);
        if(!involvedRegisters.contains(stringNode)) {
            return;
        }

        // get all statements that are involved
        for(RegisterDependencyNode visitedNode : involvedRegisters) {
            for(int i = 0; i < registerDependencyGraph.statementToRegister.size(); i++) {
                if(registerDependencyGraph.statementToRegister.get(i).contains(visitedNode)) {
                    involvedStatements[i] = true;
                }
            }
        }
    }
}
