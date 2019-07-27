package main;


import com.googlecode.d2j.DexLabel;
import com.googlecode.d2j.node.TryCatchNode;
import com.googlecode.d2j.node.insn.DexLabelStmtNode;
import com.googlecode.d2j.node.insn.DexStmtNode;
import com.googlecode.d2j.node.insn.JumpStmtNode;
import com.googlecode.d2j.node.insn.PackedSwitchStmtNode;
import com.googlecode.d2j.reader.Op;

import javax.naming.ldap.Control;
import java.util.*;

public class ControlFlowGraph {
    public Map<Integer, ControlFlowGraphNode> nodes = new HashMap<>();
    public Map<ControlFlowGraphNode, List<ControlFlowGraphNode>> adjacency = new HashMap<>();
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

    public List<ControlFlowGraphNode> getNextNodes(int stmtIndex) {
        return adjacency.get(getNode(stmtIndex));
    }

    public ControlFlowGraphNode getNextStmtNode(int stmtIndex) {
        int currentStmtIndex = stmtIndex + 1;
        DexStmtNode currentNode = method.methodNode.codeNode.stmts.get(currentStmtIndex);
        while(currentNode instanceof DexLabelStmtNode) {
            currentStmtIndex++;
            currentNode = method.methodNode.codeNode.stmts.get(currentStmtIndex);
        }
        return getNode(currentStmtIndex);
    }

    public void build() {
        // add the initial root node
        ControlFlowGraphNode rootNode = addNewNode(-1);
        ControlFlowGraphNode firstNode = addNewNode(0);
        adjacency.get(rootNode).add(firstNode);
        firstNode.prevNodes.add(rootNode);

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
                adjacency.get(fromNode).add(toNode);
                toNode.prevNodes.add(fromNode);

                if(currentStmtNode.op != Op.GOTO && currentStmtNode.op != Op.GOTO_16 && currentStmtNode.op != Op.GOTO_32) {
                    // we can also go to the next statement
                    ControlFlowGraphNode nextNode = getNextStmtNode(currentStmtIndex);
                    adjacency.get(fromNode).add(nextNode);
                    nextNode.prevNodes.add(fromNode);
                }
            }
            else if(currentStmtNode instanceof PackedSwitchStmtNode) {
                PackedSwitchStmtNode switchNode = (PackedSwitchStmtNode) currentStmtNode;
                ControlFlowGraphNode fromNode = getNode(currentStmtIndex);
                for(DexLabel label : switchNode.labels) {
                    MethodSection toSection = method.getSectionForLabel(label);
                    ControlFlowGraphNode toNode = getNode(toSection.beginIndex);
                    adjacency.get(fromNode).add(toNode);
                    toNode.prevNodes.add(fromNode);
                }
                ControlFlowGraphNode nextNode = getNextStmtNode(currentStmtIndex);
                adjacency.get(fromNode).add(nextNode);
                nextNode.prevNodes.add(fromNode);
            }
            else {
                // we can go to the next statement
                ControlFlowGraphNode fromNode = getNode(currentStmtIndex);
                ControlFlowGraphNode nextNode = getNextStmtNode(currentStmtIndex);
                adjacency.get(fromNode).add(nextNode);
                nextNode.prevNodes.add(fromNode);
            }
        }

        // consider try/catches
        // for simplicity, just make a connection to the last statement of all try sections
        if(method.methodNode.codeNode.tryStmts != null) {
            for(TryCatchNode tryCatchNode : method.methodNode.codeNode.tryStmts) {
                MethodSection startSection = method.getSectionForLabel(tryCatchNode.start);
                MethodSection endSection = method.getSectionForLabel(tryCatchNode.end);
                Set<MethodSection> trySections = method.getSectionsRange(startSection, endSection);
                for(DexLabel handler : tryCatchNode.handler) {
                    MethodSection catchSection = method.getSectionForLabel(handler);
                    ControlFlowGraphNode toNode = getNode(catchSection.beginIndex);
                    for(MethodSection trySection : trySections) {
                        ControlFlowGraphNode fromNode = getNode(trySection.endIndex - 1);
                        adjacency.get(fromNode).add(toNode);
                    }
                }
            }
        }
    }
}
