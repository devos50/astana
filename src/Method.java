
import com.googlecode.d2j.DexLabel;
import com.googlecode.d2j.node.DexMethodNode;
import com.googlecode.d2j.node.TryCatchNode;
import com.googlecode.d2j.node.insn.ConstStmtNode;
import com.googlecode.d2j.node.insn.DexLabelStmtNode;
import com.googlecode.d2j.node.insn.DexStmtNode;
import com.googlecode.d2j.node.insn.JumpStmtNode;
import com.googlecode.d2j.reader.Op;
import javafx.util.Pair;

import java.util.*;

public class Method {

    public DexMethodNode methodNode;
    public ControlFlowGraph controlFlowGraph;
    public List<MethodSection> sections = new ArrayList<>();

    public Method(DexMethodNode methodNode) {
        this.methodNode = methodNode;

        // prune labels that we are never jumping to
        Set<DexLabel> jumpLabels = new HashSet<>();
        for(int stmtIndex = 0; stmtIndex < methodNode.codeNode.stmts.size(); stmtIndex++) {
            DexStmtNode node = methodNode.codeNode.stmts.get(stmtIndex);
            if(node instanceof JumpStmtNode) {
                JumpStmtNode jumpNode = (JumpStmtNode) node;
                jumpLabels.add(jumpNode.label);
            }
        }
        if(this.methodNode.codeNode.tryStmts != null) {
            for(TryCatchNode tryCatchNode : this.methodNode.codeNode.tryStmts) {
                jumpLabels.add(tryCatchNode.start);
                jumpLabels.add(tryCatchNode.end);
                jumpLabels.addAll(Arrays.asList(tryCatchNode.handler));
            }
        }

        Set<DexLabelStmtNode> toRemove = new HashSet<>();
        for(int stmtIndex = 0; stmtIndex < methodNode.codeNode.stmts.size(); stmtIndex++) {
            DexStmtNode node = methodNode.codeNode.stmts.get(stmtIndex);
            if(node instanceof DexLabelStmtNode) {
                DexLabelStmtNode labelNode = (DexLabelStmtNode) node;
                if(!jumpLabels.contains(labelNode.label)) {
                    toRemove.add(labelNode);
                }
            }
        }
        methodNode.codeNode.stmts.removeAll(toRemove);

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

        // determine begin section
        MethodSection beginSection = null;
        DexStmtNode firstStmtNode = methodNode.codeNode.stmts.get(0);
        if(firstStmtNode instanceof DexLabelStmtNode) {
            DexLabelStmtNode labelNode = (DexLabelStmtNode) firstStmtNode;
            labelNode.label.displayName = "start";
            beginSection = new MethodSection(methodNode, labelNode.label);
            sections.add(beginSection);
        }
        else {
            DexLabel beginSectionLabel = new DexLabel();
            beginSectionLabel.displayName = "start";
            beginSection = new MethodSection(methodNode, beginSectionLabel);
            sections.add(beginSection);
        }


        for(int currentIndex = beginSection.endIndex; currentIndex < methodNode.codeNode.stmts.size(); currentIndex++) {
            DexStmtNode currentNode = methodNode.codeNode.stmts.get(currentIndex);
            if(currentNode instanceof DexLabelStmtNode) {
                DexLabelStmtNode labelNode = (DexLabelStmtNode) currentNode;
                sections.add(new MethodSection(methodNode, labelNode.label));
            }
        }

//        for(int i = 0; i < methodNode.codeNode.stmts.size(); i++) {
//            DexStmtNode node = methodNode.codeNode.stmts.get(i);
//            if(node instanceof DexLabelStmtNode) {
//                DexLabelStmtNode labelNode = (DexLabelStmtNode) node;
//                System.out.println(i + ": " + labelNode.label);
//            }
//            else if(node.op == Op.CONST_STRING && node instanceof ConstStmtNode) {
//                ConstStmtNode constStmtNode = (ConstStmtNode) node;
//                System.out.println(i + ": " + constStmtNode.op + " (" + constStmtNode.value.toString() + ")");
//            }
//            else {
//                System.out.println(i + ": " + node.op);
//            }
//        }

        // build CFG
        controlFlowGraph = ControlFlowGraph.build(this);
    }

    public Set<MethodSection> getSectionsRange(MethodSection from, MethodSection to) {
        Set<MethodSection> sectionsList = new HashSet<>();
        sectionsList.add(from);
        sectionsList.add(to);

        for(MethodSection section : sections) {
            if(section != from && section != to && section.beginIndex >= from.endIndex && section.endIndex <= to.beginIndex) {
                sectionsList.add(section);
            }
        }

        return sectionsList;
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

    public Set<MethodExecutionPath> getExecutionPaths(int sourceStmtIndex, int destStmtIndex) {
        Set<MethodExecutionPath> paths = new HashSet<>();
        LinkedList<Pair<MethodSection, MethodExecutionPath>> queue = new LinkedList<>();
        MethodSection sourceSection = getSectionForStatement(sourceStmtIndex);
        MethodSection destinationSection = getSectionForStatement(destStmtIndex);
        MethodExecutionPath firstPath = new MethodExecutionPath(this, sourceStmtIndex, destStmtIndex);
        firstPath.sectionsVisited.add(sourceSection);
        queue.add(new Pair<>(sourceSection, firstPath));
        while(!queue.isEmpty()) {
            // if there are too many items in the queue, the method is very complex; return an empty set
            if(queue.size() >= 100000) {
                return paths;
            }

            Pair<MethodSection, MethodExecutionPath> pair = queue.remove();
            MethodSection currentNode = pair.getKey();
            MethodExecutionPath currentPath = pair.getValue();
            if(currentNode == destinationSection) {
                paths.add(currentPath);
                continue;
            }

            // get outgoing edges and add them to the queue
            for(MethodSectionJump jump : controlFlowGraph.adjacency.get(currentNode)) {
                if(!currentPath.path.contains(jump)) {

                    // can we even make the jump?
                    if(currentNode == sourceSection && sourceStmtIndex > jump.jumpStmtIndex) {
                        continue;
                    }

                    MethodExecutionPath copied = currentPath.copy();
                    copied.path.add(jump);
                    copied.sectionsVisited.add(jump.toSection);
                    queue.add(new Pair<>(jump.toSection, copied));
                }
            }
        }
        return paths;
    }

}
