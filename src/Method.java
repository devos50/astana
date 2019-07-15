
import com.googlecode.d2j.DexLabel;
import com.googlecode.d2j.node.DexMethodNode;
import com.googlecode.d2j.node.insn.DexLabelStmtNode;
import com.googlecode.d2j.node.insn.DexStmtNode;
import com.googlecode.d2j.node.insn.JumpStmtNode;
import com.googlecode.d2j.reader.Op;

import java.util.ArrayList;
import java.util.List;

public class Method {

    public DexMethodNode methodNode;
    public ControlFlowGraph controlFlowGraph;
    public List<MethodSection> sections = new ArrayList<>();

    public Method(DexMethodNode methodNode) {
        this.methodNode = methodNode;

        // create new sections if needed
        for(int currentIndex = 0; currentIndex < methodNode.codeNode.stmts.size(); currentIndex++) {
            DexStmtNode currentNode = methodNode.codeNode.stmts.get(currentIndex);
            if(currentNode instanceof JumpStmtNode && currentNode.op != Op.GOTO) {
                if(currentIndex != methodNode.codeNode.stmts.size() - 1) {
                    DexStmtNode nextNode = methodNode.codeNode.stmts.get(currentIndex + 1);
                    if(!(nextNode instanceof DexLabelStmtNode)) {
                        DexLabel newLabel = new DexLabel(currentIndex + 1);
                        DexLabelStmtNode newNode = new DexLabelStmtNode(newLabel);
                        methodNode.codeNode.stmts.add(currentIndex + 1, newNode);
                    }
                }
            }
        }

        // determine sections
        DexLabel beginSectionLabel = new DexLabel();
        beginSectionLabel.displayName = "start";
        MethodSection beginSection = new MethodSection(methodNode, beginSectionLabel);
        sections.add(beginSection);

        for(int currentIndex = beginSection.endIndex; currentIndex < methodNode.codeNode.stmts.size(); currentIndex++) {
            DexStmtNode currentNode = methodNode.codeNode.stmts.get(currentIndex);
            if(currentNode instanceof DexLabelStmtNode) {
                DexLabelStmtNode labelNode = (DexLabelStmtNode) currentNode;
                sections.add(new MethodSection(methodNode, labelNode.label));
            }
        }

        // build CFG
        controlFlowGraph = ControlFlowGraph.build(this);
    }

    public MethodSection getSectionForStatement(int stmtIndex) {
        for(MethodSection section : sections) {
            if(stmtIndex >= section.beginIndex && stmtIndex < section.endIndex) {
                return section;
            }
        }
        return null;
    }

    public MethodSection getSectionForLabel(DexLabel label) {
        for(MethodSection section : sections) {
            if(section.sectionLabel == label) {
                return section;
            }
        }
        return null;
    }

}
