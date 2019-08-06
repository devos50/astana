package main;


import com.googlecode.d2j.DexLabel;
import com.googlecode.d2j.node.TryCatchNode;
import com.googlecode.d2j.node.insn.*;
import com.googlecode.d2j.reader.Op;

import java.util.*;

public class ControlFlowGraph {
    public Map<Integer, ControlFlowGraphNode> nodes = new HashMap<>();
    public Map<ControlFlowGraphNode, List<ControlFlowGraphJump>> adjacency = new HashMap<>();
    public Method method;

    public ControlFlowGraph(Method method) {
        this.method = method;
    }

    public ControlFlowGraphNode addNewNode(int stmtIndex) {
        ControlFlowGraphNode newNode = new ControlFlowGraphNode(stmtIndex);
        nodes.put(newNode.stmtIndex, newNode);
        adjacency.put(newNode, new ArrayList<>());
        return newNode;
    }

    public ControlFlowGraphNode getNode(int stmtIndex) {
        if(!nodes.containsKey(stmtIndex)) {
            // create the node
            return addNewNode(stmtIndex);
        }
        return nodes.get(stmtIndex);
    }

    public List<ControlFlowGraphJump> getJumps(int stmtIndex) {
        return adjacency.get(getNode(stmtIndex));
    }

    public ControlFlowGraphNode getNextStmtNode(int stmtIndex) {
        int currentStmtIndex = stmtIndex + 1;
        if(currentStmtIndex >= method.methodNode.codeNode.stmts.size()) {
            return null;
        }

        DexStmtNode currentNode = method.methodNode.codeNode.stmts.get(currentStmtIndex);
        while(currentNode instanceof DexLabelStmtNode) {
            currentStmtIndex++;
            if(currentStmtIndex >= method.methodNode.codeNode.stmts.size()) {
                return null;
            }
            currentNode = method.methodNode.codeNode.stmts.get(currentStmtIndex);
        }
        return getNode(currentStmtIndex);
    }

    public void build() {
        for(int currentStmtIndex = 0; currentStmtIndex < method.methodNode.codeNode.stmts.size(); currentStmtIndex++) {
            DexStmtNode currentStmtNode = method.methodNode.codeNode.stmts.get(currentStmtIndex);
            if(currentStmtNode instanceof DexLabelStmtNode) {
                continue; // we cannot go anywhere from this node
            }
            else if(currentStmtNode.op == Op.RETURN || currentStmtNode.op == Op.RETURN_OBJECT || currentStmtNode.op == Op.RETURN_VOID || currentStmtNode.op == Op.RETURN_WIDE || currentStmtNode.op == Op.THROW) {
                // we cannot go anywhere from this node
                continue;
            }
            else if(currentStmtNode instanceof JumpStmtNode) {
                // this is a conditional - follow it or not
                JumpStmtNode jumpStmtNode = (JumpStmtNode) currentStmtNode;
                MethodSection gotoSection = method.getSectionForLabel(jumpStmtNode.label);
                ControlFlowGraphNode fromNode = getNode(currentStmtIndex);
                ControlFlowGraphNode toNode = getNode(gotoSection.beginIndex);

                int jumpType = JumpType.CONDITIONAL_STATEMENT;
                if(currentStmtNode.op == Op.GOTO || currentStmtNode.op == Op.GOTO_16 || currentStmtNode.op == Op.GOTO_32) {
                    jumpType = JumpType.GOTO_STATEMENT;
                }

                adjacency.get(fromNode).add(new ControlFlowGraphJump(fromNode, toNode, jumpType));
                toNode.prevNodes.add(fromNode);

                if(currentStmtNode.op != Op.GOTO && currentStmtNode.op != Op.GOTO_16 && currentStmtNode.op != Op.GOTO_32) {
                    // we can also go to the next statement
                    ControlFlowGraphNode nextNode = getNextStmtNode(currentStmtIndex);
                    adjacency.get(fromNode).add(new ControlFlowGraphJump(fromNode, nextNode, JumpType.CONDITIONAL_STATEMENT));
                    nextNode.prevNodes.add(fromNode);
                }
            }
            else if(currentStmtNode instanceof BaseSwitchStmtNode) {
                BaseSwitchStmtNode switchNode = (BaseSwitchStmtNode) currentStmtNode;
                ControlFlowGraphNode fromNode = getNode(currentStmtIndex);
                for(DexLabel label : switchNode.labels) {
                    MethodSection toSection = method.getSectionForLabel(label);
                    ControlFlowGraphNode toNode = getNode(toSection.beginIndex);
                    adjacency.get(fromNode).add(new ControlFlowGraphJump(fromNode, toNode, JumpType.SWITCH_STATEMENT));
                    toNode.prevNodes.add(fromNode);
                }
                ControlFlowGraphNode nextNode = getNextStmtNode(currentStmtIndex);
                adjacency.get(fromNode).add(new ControlFlowGraphJump(fromNode, nextNode, JumpType.SWITCH_STATEMENT));
                nextNode.prevNodes.add(fromNode);
            }
            else {
                // we can go to the next statement
                ControlFlowGraphNode fromNode = getNode(currentStmtIndex);
                ControlFlowGraphNode toNode = getNextStmtNode(currentStmtIndex);
                if(toNode != null) {
                    adjacency.get(fromNode).add(new ControlFlowGraphJump(fromNode, toNode, JumpType.NEXT_STATEMENT));
                    toNode.prevNodes.add(fromNode);
                }
            }
        }

        // consider try/catches
        if(method.methodNode.codeNode.tryStmts != null) {
            for(TryCatchNode tryCatchNode : method.methodNode.codeNode.tryStmts) {
                MethodSection startSection = method.getSectionForLabel(tryCatchNode.start);
                MethodSection endSection = method.getSectionForLabel(tryCatchNode.end);
                Set<MethodSection> trySections = method.getSectionsRange(startSection, endSection);
                for(DexLabel handler : tryCatchNode.handler) {
                    MethodSection catchSection = method.getSectionForLabel(handler);
                    ControlFlowGraphNode toNode = getNode(catchSection.beginIndex);
                    for(MethodSection trySection : trySections) {
                        // for simplicity, just make a connection to the last eligible statement of all try sections
                        int fromNodeIndex = -1;
                        for(int stmtIndex = trySection.endIndex - 1; stmtIndex >= trySection.beginIndex; stmtIndex--) {
                            DexStmtNode currentNode = method.methodNode.codeNode.stmts.get(stmtIndex);
                            if(currentNode.op != Op.GOTO && currentNode.op != Op.GOTO_16 && currentNode.op != Op.GOTO_32) {
                                fromNodeIndex = stmtIndex;
                                break;
                            }
                        }

                        if(fromNodeIndex != -1) {
                            ControlFlowGraphNode fromNode = getNode(fromNodeIndex);
                            adjacency.get(fromNode).add(new ControlFlowGraphJump(fromNode, toNode, JumpType.TRY_CATCH));
                        }
                    }
                }
            }
        }
    }
}
