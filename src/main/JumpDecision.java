package main;

public class JumpDecision {

    public int fromStmtIndex;
    public int toStmtIndex;
    public int jumpType;

    public JumpDecision(int fromStmtIndex, int toStmtIndex, int jumpType) {
        this.fromStmtIndex = fromStmtIndex;
        this.toStmtIndex = toStmtIndex;
        this.jumpType = jumpType;
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof JumpDecision)) {
            return false;
        }
        JumpDecision other = (JumpDecision) obj;
        return fromStmtIndex == other.fromStmtIndex && toStmtIndex == other.toStmtIndex;
    }

    @Override
    public String toString() {
        return fromStmtIndex + " -> " + toStmtIndex;
    }
}
