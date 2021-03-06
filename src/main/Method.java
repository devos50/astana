package main;

import com.googlecode.d2j.DexLabel;
import com.googlecode.d2j.node.DexMethodNode;
import com.googlecode.d2j.node.TryCatchNode;
import com.googlecode.d2j.node.insn.*;
import com.googlecode.d2j.reader.Op;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.util.*;

public class Method {

    public final String apkPath;
    public final File file;
    public DexMethodNode methodNode;
    public ControlFlowGraph controlFlowGraph;
    public List<MethodSection> sections = new ArrayList<>();
    public int firstStmtIndex = 0;

    public Method(String apkPath, File file, DexMethodNode methodNode) {
        this.apkPath = apkPath;
        this.file = file;
        this.methodNode = methodNode;

        // prune labels that we are never jumping to
        Set<DexLabel> jumpLabels = new HashSet<>();
        for(int stmtIndex = 0; stmtIndex < methodNode.codeNode.stmts.size(); stmtIndex++) {
            DexStmtNode node = methodNode.codeNode.stmts.get(stmtIndex);
            if(node instanceof JumpStmtNode) {
                JumpStmtNode jumpNode = (JumpStmtNode) node;
                jumpLabels.add(jumpNode.label);
            }
            else if(node instanceof PackedSwitchStmtNode) {
                PackedSwitchStmtNode switchNode = (PackedSwitchStmtNode) node;
                jumpLabels.addAll(Arrays.asList(switchNode.labels));
            }
            else if(node instanceof SparseSwitchStmtNode) {
                SparseSwitchStmtNode switchNode = (SparseSwitchStmtNode) node;
                jumpLabels.addAll(Arrays.asList(switchNode.labels));
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

        // create new sections if needed (after jump statements or packed-switches)
        for(int currentIndex = 0; currentIndex < methodNode.codeNode.stmts.size(); currentIndex++) {
            DexStmtNode currentNode = methodNode.codeNode.stmts.get(currentIndex);
            if((currentNode instanceof JumpStmtNode && currentNode.op != Op.GOTO && currentNode.op != Op.GOTO_16 && currentNode.op != Op.GOTO_32) || (currentNode instanceof PackedSwitchStmtNode)) {
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
            firstStmtIndex = 1;
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

        // make the section nodes
        for(int currentIndex = beginSection.endIndex; currentIndex < methodNode.codeNode.stmts.size(); currentIndex++) {
            DexStmtNode currentNode = methodNode.codeNode.stmts.get(currentIndex);
            if(currentNode instanceof DexLabelStmtNode) {
                DexLabelStmtNode labelNode = (DexLabelStmtNode) currentNode;
                sections.add(new MethodSection(methodNode, labelNode.label));
            }
        }

        // classify try and catch blocks correctly
        if(methodNode.codeNode.tryStmts != null) {
            for(TryCatchNode tryCatchNode : methodNode.codeNode.tryStmts) {
                MethodSection startSection = getSectionForLabel(tryCatchNode.start);
                MethodSection endSection = getSectionForLabel(tryCatchNode.end);
                Set<MethodSection> trySections = getSectionsRange(startSection, endSection);
                for(MethodSection trySection : trySections) {
                    trySection.sectionType = MethodSectionType.TRY_BLOCK;
                }

                for(DexLabel handler : tryCatchNode.handler) {
                    MethodSection catchSection = getSectionForLabel(handler);
                    catchSection.sectionType = MethodSectionType.CATCH_BLOCK;
                }
            }
        }
    }

    public String getName() {
        return methodNode.method.getName();
    }

    public void buildCFG() {
        // debugging
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

        controlFlowGraph = new ControlFlowGraph(this);
        controlFlowGraph.build();
    }

    public Set<MethodSection> getSectionsRange(MethodSection from, MethodSection to) {
        Set<MethodSection> sectionsList = new HashSet<>();
        sectionsList.add(from);

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

    public List<MethodExecutionPath> getRandomExecutionPathsBackwards(int iterations, int sourceStmtIndex, int destStmtIndex) {
        System.out.println("Getting random backwards execution paths...");
        List<MethodExecutionPath> paths = new ArrayList<>();
        for(int iteration = 0; iteration < iterations; iteration++) {
            int currentStmtIndex = destStmtIndex;
            MethodExecutionPath currentPath = new MethodExecutionPath(this, sourceStmtIndex, destStmtIndex);
            Random rand = new Random();
            while(true) {
                if(currentStmtIndex < 0) {
                    break;
                }
                else if(currentStmtIndex == sourceStmtIndex) {
                    paths.add(currentPath);
                    break;
                }

                List<ControlFlowGraphNode> prevNodes = controlFlowGraph.getNode(currentStmtIndex).prevNodes;
                if(prevNodes.size() == 0) { break; }

                // pick a random previous node
                ControlFlowGraphNode prevNode = prevNodes.get(rand.nextInt(prevNodes.size()));
                ControlFlowGraphJump jump = controlFlowGraph.getJumpTo(prevNode.stmtIndex, currentStmtIndex);
                if(jump.jumpType != JumpType.NEXT_STATEMENT && jump.jumpType != JumpType.GOTO_STATEMENT) {
                    JumpDecision decision = new JumpDecision(jump.fromNode.stmtIndex, jump.toNode.stmtIndex, jump.jumpType);
                    currentPath.path.add(0, decision);
                }

                currentStmtIndex = prevNode.stmtIndex;
            }
        }
        return paths;
    }

    public List<MethodExecutionPath> getExecutionPaths(int sourceStmtIndex, int destStmtIndex) {
        List<MethodExecutionPath> paths = new ArrayList<>();
        LinkedList<Pair<Integer, MethodExecutionPath>> queue = new LinkedList<>();
        MethodExecutionPath firstPath = new MethodExecutionPath(this, sourceStmtIndex, destStmtIndex);
        queue.add(new ImmutablePair<>(sourceStmtIndex, firstPath));
        while(!queue.isEmpty()) {
            // if there are too many items in the queue, the method is very complex; return an empty set
            if(queue.size() >= 100000 || paths.size() >= 2000) {
                // simply try a random walk, maybe it succeeds
                if(paths.size() == 0 && sourceStmtIndex == 0) {
                    return getRandomExecutionPathsBackwards(100, sourceStmtIndex, destStmtIndex);
                }
                return paths;
            }

            Pair<Integer, MethodExecutionPath> pair = queue.remove();
            int currentStmtIndex = pair.getKey();
            MethodExecutionPath currentPath = pair.getValue();
            if(currentStmtIndex == destStmtIndex) {
                paths.add(currentPath);
                continue;
            }

            List<ControlFlowGraphJump> jumps = controlFlowGraph.getJumps(currentStmtIndex);
            for(ControlFlowGraphJump jump : jumps) {
                if(jump.jumpType == JumpType.NEXT_STATEMENT || jump.jumpType == JumpType.GOTO_STATEMENT) {
                    // check if we might be in an infinite loop
                    if(currentPath.lastGotosTaken.size() > 5) {
                        boolean areEqual = true;
                        for(int i = currentPath.lastGotosTaken.size() - 1; i >= currentPath.lastGotosTaken.size() - 5; i--) {
                            if(currentPath.lastGotosTaken.get(i) != currentStmtIndex) {
                                areEqual = false;
                                break;
                            }
                        }

                        if(areEqual) {
                            continue; // don't add this state - we are likely in an infinite loop
                        }
                    }

                    if(jump.jumpType == JumpType.GOTO_STATEMENT) {
                        currentPath.lastGotosTaken.add(currentStmtIndex);
                    }

                    queue.add(new ImmutablePair<>(jump.toNode.stmtIndex, currentPath));
                }
                else {
                    JumpDecision decision = new JumpDecision(jump.fromNode.stmtIndex, jump.toNode.stmtIndex, jump.jumpType);
                    if(!currentPath.path.contains(decision)) {
                        MethodExecutionPath copied = currentPath.copy();
                        copied.path.add(decision);
                        queue.add(new ImmutablePair<>(jump.toNode.stmtIndex, copied));
                    }
                }
            }
        }
        return paths;
    }
}
