import com.googlecode.d2j.DexLabel;
import com.googlecode.d2j.node.DexMethodNode;
import com.googlecode.d2j.node.insn.DexLabelStmtNode;
import com.googlecode.d2j.node.insn.DexStmtNode;

public class MethodSection {

    public DexLabel sectionLabel;
    public int beginIndex = -1;
    public int endIndex = -1;

    public MethodSection(DexMethodNode methodNode, DexLabel sectionLabel) {
        this.sectionLabel = sectionLabel;

        // determine begin/end indices
        boolean inSection = false;
        if(sectionLabel.displayName != null && sectionLabel.displayName.equals("start")) {
            inSection = true;
            beginIndex = 0;
        }

        for(int currentIndex = 1; currentIndex < methodNode.codeNode.stmts.size(); currentIndex++) {
            DexStmtNode currentNode = methodNode.codeNode.stmts.get(currentIndex);
            if(currentNode instanceof DexLabelStmtNode) {
                if(inSection) {
                    // we reached the end of this section
                    endIndex = currentIndex;
                    inSection = false;
                    break;
                }

                DexLabelStmtNode labelNode = (DexLabelStmtNode) currentNode;
                if(labelNode.label == sectionLabel) {
                    inSection = true;
                    beginIndex = currentIndex + 1;
                }
            }
        }

        if(inSection) {
            endIndex = methodNode.codeNode.stmts.size();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof MethodSection)) {
            return false;
        }
        MethodSection other = (MethodSection) obj;
        return sectionLabel == other.sectionLabel && beginIndex == other.beginIndex && endIndex == other.endIndex;
    }

    @Override
    public String toString() {
        return sectionLabel.toString() + "(" + beginIndex + " -> " + endIndex + ")";
    }
}
