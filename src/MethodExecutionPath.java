import com.googlecode.d2j.node.insn.DexStmtNode;

import java.util.*;

public class MethodExecutionPath {

    public Method method;
    public int sourceStmtIndex;
    public int destStmtIndex;
    public List<MethodSection> sectionsVisited = new ArrayList<>();
    public List<MethodSectionJump> path = new ArrayList<>();
    public RegisterDependencyGraph registerDependencyGraph;

    public MethodExecutionPath(Method method, int stringInitIndex, int stringDecryptIndex) {
        this.method = method;
        this.sourceStmtIndex = stringInitIndex;
        this.destStmtIndex = stringDecryptIndex;
    }

    public MethodExecutionPath copy() {
        MethodExecutionPath copy = new MethodExecutionPath(this.method, this.sourceStmtIndex, this.destStmtIndex);
        copy.sectionsVisited.addAll(this.sectionsVisited);
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

            for(RegisterDependencyNode adjacentNode : adjacent) {
                if(!visited.contains(adjacentNode)) {
                    visited.add(adjacentNode);
                    queue.add(adjacentNode);
                }
            }
        }

        return visited;
    }

    public boolean[] computeInvolvedStatements(int stringResultRegister) {
        if(this.registerDependencyGraph == null) {
            throw new RuntimeException("register dependency graph should be computed first!");
        }

        boolean[] involvedStatements = new boolean[this.method.methodNode.codeNode.stmts.size()];

        // first, include obvious statements, like the sections we visit in this path
        MethodSection stringInitSection = method.getSectionForStatement(sourceStmtIndex);
        if(stringInitSection.sectionLabel.displayName != null && !stringInitSection.sectionLabel.displayName.equals("start")) {
            involvedStatements[stringInitSection.beginIndex - 1] = true; // label declaration
        }

        for(MethodSectionJump jump : path) {
            if(jump.fromSection.sectionLabel.displayName != null && !jump.fromSection.sectionLabel.displayName.equals("start")) {
                int fromSectionLabelStmtIndex = jump.fromSection.beginIndex - 1;
                involvedStatements[fromSectionLabelStmtIndex] = true;
            }

            int toSectionLabelStmtIndex = jump.toSection.beginIndex - 1;
            involvedStatements[toSectionLabelStmtIndex] = true;
            involvedStatements[jump.jumpStmtIndex] = true;
        }

        DexStmtNode lastStmtNode = method.methodNode.codeNode.stmts.get(destStmtIndex);
        //if(lastStmtNode.op != Op.MOVE_RESULT_OBJECT && lastStmtNode.op != Op.INVOKE_DIRECT) {
        //    throw new RuntimeException("Pruning: last node not move-result-object or invoke-direct!");
        //}

        // perform a BFS from the statement where the string is possibly decrypted, to the init method
        RegisterDependencyNode rootNode = registerDependencyGraph.activeRegister.get(stringResultRegister);
        Set<RegisterDependencyNode> involvedRegisters = getDependenciesForRegister(rootNode);

        // also include dependencies for the jumps
        for(MethodSectionJump jump : path) {
            for(RegisterDependencyNode node : registerDependencyGraph.statementToRegister.get(jump.jumpStmtIndex)) {
                involvedRegisters.addAll(getDependenciesForRegister(node));
            }
        }

//        // check whether the original string declaration is visited. If not, this string is probably not encrypted
//        ConstStmtNode stringInitNode = (ConstStmtNode) method.methodNode.codeNode.stmts.get(sourceStmtIndex);
//        RegisterDependencyNode stringNode = new RegisterDependencyNode(stringInitNode.a, 1);
//        if(!involvedRegisters.contains(stringNode)) {
//            return;
//        }

        // get all statements that are involved
        for(RegisterDependencyNode visitedNode : involvedRegisters) {
            for(int i = 0; i < registerDependencyGraph.statementToRegister.size(); i++) {
                if(registerDependencyGraph.statementToRegister.get(i).contains(visitedNode)) {
                    involvedStatements[i] = true;
                }
            }
        }

        return involvedStatements;
    }
}
