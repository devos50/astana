package main;

import java.util.ArrayList;
import java.util.List;

public class ControlFlowGraphNode {

    public int stmtIndex;
    public List<ControlFlowGraphNode> prevNodes = new ArrayList<>();

    public ControlFlowGraphNode(int stmtIndex) {
        this.stmtIndex = stmtIndex;
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof ControlFlowGraphNode)) {
            return false;
        }
        ControlFlowGraphNode other = (ControlFlowGraphNode) obj;
        return other.stmtIndex == this.stmtIndex;
    }

    @Override
    public String toString() {
        return "<" + stmtIndex + ">";
    }
}