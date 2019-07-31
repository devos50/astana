package main;

public class ControlFlowGraphJump {

    public ControlFlowGraphNode fromNode;
    public ControlFlowGraphNode toNode;
    public int jumpType;

    public ControlFlowGraphJump(ControlFlowGraphNode fromNode, ControlFlowGraphNode toSection, int jumpType) {
        this.fromNode = fromNode;
        this.toNode = toSection;
        this.jumpType = jumpType;
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof ControlFlowGraphJump)) {
            return false;
        }
        ControlFlowGraphJump other = (ControlFlowGraphJump) obj;
        return this.fromNode == other.fromNode && this.toNode == other.toNode && this.jumpType == other.jumpType;
    }

    @Override
    public String toString() {
        return this.fromNode.toString() + " -> " + this.toNode.toString();
    }
}
