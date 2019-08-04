package main;

import com.googlecode.d2j.node.insn.DexStmtNode;
import com.googlecode.d2j.node.insn.JumpStmtNode;
import com.googlecode.d2j.node.insn.PackedSwitchStmtNode;
import com.googlecode.d2j.reader.Op;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class MethodExecutionPath {

    public Method method;
    public int sourceStmtIndex;
    public int destStmtIndex;
    public List<JumpDecision> path = new ArrayList<>();
    public RegisterDependencyGraph registerDependencyGraph;
    public List<Pair<Integer, Integer>> potentialStringDecryptionStatements;
    public List<Integer> lastGotosTaken = new ArrayList<>();

    public MethodExecutionPath(Method method, int stringInitIndex, int stringDecryptIndex) {
        this.method = method;
        this.sourceStmtIndex = stringInitIndex;
        this.destStmtIndex = stringDecryptIndex;
        this.potentialStringDecryptionStatements = new ArrayList<>();
    }

    public MethodExecutionPath copy() {
        MethodExecutionPath copy = new MethodExecutionPath(this.method, this.sourceStmtIndex, this.destStmtIndex);
        copy.path.addAll(this.path);
        copy.potentialStringDecryptionStatements.addAll(this.potentialStringDecryptionStatements);
        copy.lastGotosTaken.addAll(this.lastGotosTaken);
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

        if(includeJumps) {
            for(JumpDecision jumpDecision : path) {
                MethodSection fromSection = method.getSectionForStatement(jumpDecision.fromStmtIndex);
                if(fromSection.sectionLabel.displayName != null && !fromSection.sectionLabel.displayName.equals("start")) {
                    int fromSectionLabelStmtIndex = fromSection.beginIndex - 1;
                    involvedStatements[fromSectionLabelStmtIndex] = true;
                }

                MethodSection toSection = method.getSectionForStatement(jumpDecision.toStmtIndex);
                int toSectionLabelStmtIndex = toSection.beginIndex - 1;
                involvedStatements[toSectionLabelStmtIndex] = true;
                if(method.methodNode.codeNode.stmts.get(jumpDecision.fromStmtIndex) instanceof JumpStmtNode ||
                        method.methodNode.codeNode.stmts.get(jumpDecision.fromStmtIndex) instanceof PackedSwitchStmtNode) {
                    involvedStatements[jumpDecision.fromStmtIndex] = true;
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
            for(JumpDecision jumpDecision : path) {
                DexStmtNode jumpNode = method.methodNode.codeNode.stmts.get(jumpDecision.fromStmtIndex);
                if(jumpNode instanceof JumpStmtNode && jumpNode.op != Op.GOTO && jumpNode.op != Op.GOTO_16 && jumpNode.op != Op.GOTO_32) {
                    for(RegisterDependencyNode node : registerDependencyGraph.statementToRegister.get(jumpDecision.fromStmtIndex)) {
                        involvedRegisters.addAll(registerDependencyGraph.getDependencies(node));
                    }
                }
                else if(jumpNode instanceof PackedSwitchStmtNode) {
                    for(RegisterDependencyNode node : registerDependencyGraph.statementToRegister.get(jumpDecision.fromStmtIndex)) {
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

        // include all section labels
        for(int i = 0; i < involvedStatements.length; i++) {
            if(involvedStatements[i]) {
                MethodSection section = method.getSectionForStatement(i);
                if(section == null || (section.sectionLabel.displayName != null && section.sectionLabel.displayName.equals("start"))) {
                    continue;
                }
                involvedStatements[section.beginIndex - 1] = true;
            }
        }

        if(includeJumps) {
            // include GOTO statements
            for(Integer visitedStmtIndex : registerDependencyGraph.visitedStatements) {
                DexStmtNode stmtNode = method.methodNode.codeNode.stmts.get(visitedStmtIndex);
                if(stmtNode.op == Op.GOTO || stmtNode.op == Op.GOTO_16 || stmtNode.op == Op.GOTO_32) {
                    involvedStatements[visitedStmtIndex] = true;
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
