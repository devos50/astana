import com.googlecode.d2j.node.insn.DexStmtNode;
import com.googlecode.d2j.node.insn.JumpStmtNode;
import com.googlecode.d2j.reader.Op;

import java.util.*;

public class ControlFlowGraph {
    public Map<MethodSection, List<MethodSectionJump>> adjacency = new HashMap<>();

    public static ControlFlowGraph build(Method method) {
        // TODO handle switches!
        // TODO handle try/catch jumps!

        ControlFlowGraph graph = new ControlFlowGraph();

        // create nodes
        for(MethodSection section : method.sections) {
            graph.adjacency.put(section, new ArrayList<>());
        }

        // for each section, determine where we can possibly go
        for(int sectionIndex = 0; sectionIndex < method.sections.size(); sectionIndex++) {
            MethodSection currentSection = method.sections.get(sectionIndex);
//            System.out.println("Section: " + currentSection);
            for(int stmtIndex = currentSection.beginIndex; stmtIndex < currentSection.endIndex; stmtIndex++) {
                DexStmtNode stmtNode = method.methodNode.codeNode.stmts.get(stmtIndex);

                if(stmtNode instanceof JumpStmtNode) {
                    JumpStmtNode jumpStmtNode = (JumpStmtNode) stmtNode;
                    MethodSection jumpSection = method.getSectionForLabel(jumpStmtNode.label);
                    System.out.println(jumpStmtNode.label);
                    if(jumpSection == null) {
                        throw new RuntimeException("Cannot find section where jump to is made!");
                    }

                    MethodSectionJump jump = new MethodSectionJump(currentSection, jumpSection, stmtIndex);
                    if(!graph.adjacency.get(currentSection).contains(jump)) {
                        graph.adjacency.get(currentSection).add(jump);
                    }
                }
            }

            // can we go to the next section?
            if(sectionIndex != method.sections.size() - 1) {
                DexStmtNode lastStmtNodeInSection = method.methodNode.codeNode.stmts.get(currentSection.endIndex - 1);
                if(lastStmtNodeInSection.op != Op.THROW && lastStmtNodeInSection.op != Op.GOTO && lastStmtNodeInSection.op != Op.RETURN && lastStmtNodeInSection.op != Op.RETURN_OBJECT && lastStmtNodeInSection.op != Op.RETURN_VOID && lastStmtNodeInSection.op != Op.RETURN_WIDE) {
                    // we can indeed reach the next section from the current one
                    MethodSection nextSection = method.sections.get(sectionIndex + 1);
                    MethodSectionJump jump = new MethodSectionJump(currentSection, nextSection, currentSection.endIndex - 1);

                    if(!graph.adjacency.get(currentSection).contains(jump)) {
                        graph.adjacency.get(currentSection).add(jump);
                    }
                }
            }
        }

        return graph;
    }
}
