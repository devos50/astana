public class RegisterDependencyNode {

    private final int index;
    public final int sequenceNumber;

    public RegisterDependencyNode(int index, int sequenceNumber) {
        this.index = index;
        this.sequenceNumber = sequenceNumber;
    }

    @Override
    public boolean equals(Object other) {
        if(!(other instanceof RegisterDependencyNode)) {
            return false;
        }

        RegisterDependencyNode otherNode = (RegisterDependencyNode) other;
        return this.index == otherNode.index && this.sequenceNumber == otherNode.sequenceNumber;
    }

    @Override
    public int hashCode() {
        return this.index * 255 + this.sequenceNumber;
    }

    @Override
    public String toString() {
        return "(" + this.index + ", " + this.sequenceNumber + ")";
    }
}
