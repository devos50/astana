import com.googlecode.d2j.DexLabel;
import com.googlecode.d2j.node.TryCatchNode;
import com.googlecode.d2j.node.insn.DexStmtNode;
import com.googlecode.d2j.node.insn.JumpStmtNode;
import com.googlecode.d2j.node.insn.PackedSwitchStmtNode;
import com.googlecode.d2j.reader.Op;

import java.util.*;

public class ControlFlowGraph {
    public Map<MethodSection, List<MethodSectionJump>> adjacency = new HashMap<>();

    public static ControlFlowGraph build(Method method) {
        // TODO handle switches!

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
                    if(jumpSection == null) {
                        throw new RuntimeException("Cannot find section where jump to is made! (" + jumpStmtNode.label + ")");
                    }

                    MethodSectionJump jump = new MethodSectionJump(currentSection, jumpSection, stmtIndex);
                    if(!graph.adjacency.get(currentSection).contains(jump)) {
                        graph.adjacency.get(currentSection).add(jump);
                    }
                }
                else if(stmtNode instanceof PackedSwitchStmtNode) {
                    PackedSwitchStmtNode switchNode = (PackedSwitchStmtNode) stmtNode;
                    for(DexLabel label : switchNode.labels) {
                        MethodSection jumpSection = method.getSectionForLabel(label);
                        if(jumpSection == null) {
                            throw new RuntimeException("Cannot find section where jump to is made! (" + label + ")");
                        }

                        MethodSectionJump jump = new MethodSectionJump(currentSection, jumpSection, stmtIndex);
                        if(!graph.adjacency.get(currentSection).contains(jump)) {
                            graph.adjacency.get(currentSection).add(jump);
                        }
                    }
                }
            }

            // can we go to the next section?
            if(sectionIndex != method.sections.size() - 1) {
                DexStmtNode lastStmtNodeInSection = method.methodNode.codeNode.stmts.get(currentSection.endIndex - 1);
                if(lastStmtNodeInSection.op != Op.THROW && lastStmtNodeInSection.op != Op.GOTO && lastStmtNodeInSection.op != Op.GOTO_16 &&
                        lastStmtNodeInSection.op != Op.GOTO_32 && lastStmtNodeInSection.op != Op.RETURN && lastStmtNodeInSection.op != Op.RETURN_OBJECT &&
                        lastStmtNodeInSection.op != Op.RETURN_VOID && lastStmtNodeInSection.op != Op.RETURN_WIDE) {
                    // we can indeed reach the next section from the current one
                    MethodSection nextSection = method.sections.get(sectionIndex + 1);
                    MethodSectionJump jump = new MethodSectionJump(currentSection, nextSection, currentSection.endIndex - 1);

                    if(!graph.adjacency.get(currentSection).contains(jump)) {
                        graph.adjacency.get(currentSection).add(jump);
                    }
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
                    for(MethodSection trySection : trySections) {
                        MethodSectionJump jump = new MethodSectionJump(trySection, catchSection, -1);
                        graph.adjacency.get(trySection).add(jump);
                    }
                }
            }
        }

        return graph;
    }
}