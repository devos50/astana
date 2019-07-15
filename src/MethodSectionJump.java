public class MethodSectionJump {

    public MethodSection fromSection;
    public MethodSection toSection;
    public int jumpStmtIndex;

    public MethodSectionJump(MethodSection fromSection, MethodSection toSection, int jumpStmtIndex) {
        this.fromSection = fromSection;
        this.toSection = toSection;
        this.jumpStmtIndex = jumpStmtIndex;
    }

    @Override
    public String toString() {
        return this.fromSection.toString() + " -> " + this.toSection.toString();
    }
}
