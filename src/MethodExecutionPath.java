import com.googlecode.d2j.node.insn.DexStmtNode;
import javafx.util.Pair;

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

    public Pair<Set<RegisterDependencyNode>, boolean[]> computeInvolvedStatements(int stringResultRegister) {
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
            if(jump.jumpStmtIndex == -1) { continue; }

            if(jump.fromSection.sectionLabel.displayName != null && !jump.fromSection.sectionLabel.displayName.equals("start")) {
                int fromSectionLabelStmtIndex = jump.fromSection.beginIndex - 1;
                involvedStatements[fromSectionLabelStmtIndex] = true;
            }

            int toSectionLabelStmtIndex = jump.toSection.beginIndex - 1;
            involvedStatements[toSectionLabelStmtIndex] = true;
            involvedStatements[jump.jumpStmtIndex] = true;
        }

        // perform a BFS from the destination statement, to the source statement
        RegisterDependencyNode rootNode = registerDependencyGraph.activeRegister.get(stringResultRegister);
        if(rootNode == null) {
            return new Pair<>(new HashSet<>(), new boolean[this.method.methodNode.codeNode.stmts.size()]);
        }

        Set<RegisterDependencyNode> involvedRegisters = getDependenciesForRegister(rootNode);

        // also include dependencies for the jumps
        for(MethodSectionJump jump : path) {
            if(jump.jumpStmtIndex == -1) { continue; }
            for(RegisterDependencyNode node : registerDependencyGraph.statementToRegister.get(jump.jumpStmtIndex)) {
                involvedRegisters.addAll(getDependenciesForRegister(node));
            }
        }

        // get all statements that are involved
        for(RegisterDependencyNode visitedNode : involvedRegisters) {
            for(int i = 0; i < registerDependencyGraph.statementToRegister.size(); i++) {
                if(registerDependencyGraph.statementToRegister.get(i).contains(visitedNode)) {
                    involvedStatements[i] = true;
                }
            }
        }

        return new Pair<>(involvedRegisters, involvedStatements);
    }
}
