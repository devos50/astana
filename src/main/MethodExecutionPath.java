package main;

import com.googlecode.d2j.node.insn.DexStmtNode;
import com.googlecode.d2j.node.insn.JumpStmtNode;
import com.googlecode.d2j.node.insn.PackedSwitchStmtNode;
import com.googlecode.d2j.reader.Op;

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

    public boolean[] computeInvolvedStatements(int stringResultRegister, boolean includeJumps) {
        if(this.registerDependencyGraph == null) {
            throw new RuntimeException("register dependency graph should be computed first!");
        }

        boolean[] involvedStatements = new boolean[this.method.methodNode.codeNode.stmts.size()];

        // first, include obvious statements, like the sections we visit in this path
        MethodSection stringInitSection = method.getSectionForStatement(sourceStmtIndex);
        if(stringInitSection.sectionLabel.displayName != null && !stringInitSection.sectionLabel.displayName.equals("start")) {
            involvedStatements[stringInitSection.beginIndex - 1] = true; // label declaration
        }

        if(includeJumps) {
            for(MethodSectionJump jump : path) {
                if(jump.jumpStmtIndex == -1) { continue; }

                if(jump.fromSection.sectionLabel.displayName != null && !jump.fromSection.sectionLabel.displayName.equals("start")) {
                    int fromSectionLabelStmtIndex = jump.fromSection.beginIndex - 1;
                    involvedStatements[fromSectionLabelStmtIndex] = true;
                }

                int toSectionLabelStmtIndex = jump.toSection.beginIndex - 1;
                involvedStatements[toSectionLabelStmtIndex] = true;
                if(method.methodNode.codeNode.stmts.get(jump.jumpStmtIndex) instanceof JumpStmtNode ||
                        method.methodNode.codeNode.stmts.get(jump.jumpStmtIndex) instanceof PackedSwitchStmtNode) {
                    involvedStatements[jump.jumpStmtIndex] = true;
                }
            }
        }

        // perform a BFS from the destination statement, to the source statement
        RegisterDependencyNode rootNode = registerDependencyGraph.activeRegister.get(stringResultRegister);
        if(rootNode == null) {
            return new boolean[this.method.methodNode.codeNode.stmts.size()];
        }

        Set<RegisterDependencyNode> involvedRegisters = registerDependencyGraph.getDependencies(rootNode);

        // also include dependencies for conditional jumps and packed switches
        if(includeJumps) {
            for(MethodSectionJump jump : path) {
                if(jump.jumpStmtIndex == -1) { continue; }

                DexStmtNode jumpNode = method.methodNode.codeNode.stmts.get(jump.jumpStmtIndex);
                if(jumpNode instanceof JumpStmtNode && jumpNode.op != Op.GOTO && jumpNode.op != Op.GOTO_16 && jumpNode.op != Op.GOTO_32) {
                    for(RegisterDependencyNode node : registerDependencyGraph.statementToRegister.get(jump.jumpStmtIndex)) {
                        involvedRegisters.addAll(registerDependencyGraph.getDependencies(node));
                    }
                }
                else if(jumpNode instanceof PackedSwitchStmtNode) {
                    for(RegisterDependencyNode node : registerDependencyGraph.statementToRegister.get(jump.jumpStmtIndex)) {
                        involvedRegisters.addAll(registerDependencyGraph.getDependencies(node));
                    }
                }
            }
        }

        // get all statements that are involved and include the statements that influence them
        for(RegisterDependencyNode visitedNode : involvedRegisters) {
            for(int i = 0; i < registerDependencyGraph.statementToRegister.size(); i++) {
                if(registerDependencyGraph.statementToRegister.get(i).contains(visitedNode)) {
                    involvedStatements[i] = true;
                }
            }
        }

        return involvedStatements;
    }

    @Override
    public String toString() {
        return path.toString();
    }
}
