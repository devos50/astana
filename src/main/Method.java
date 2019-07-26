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

        // debugging
        for(int i = 0; i < methodNode.codeNode.stmts.size(); i++) {
            DexStmtNode node = methodNode.codeNode.stmts.get(i);
            if(node instanceof DexLabelStmtNode) {
                DexLabelStmtNode labelNode = (DexLabelStmtNode) node;
                System.out.println(i + ": " + labelNode.label);
            }
            else if(node.op == Op.CONST_STRING && node instanceof ConstStmtNode) {
                ConstStmtNode constStmtNode = (ConstStmtNode) node;
                System.out.println(i + ": " + constStmtNode.op + " (" + constStmtNode.value.toString() + ")");
            }
            else {
                System.out.println(i + ": " + node.op);
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

        // build CFG
        controlFlowGraph = ControlFlowGraph.build(this);
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

    public Set<MethodExecutionPath> getExecutionPaths(int sourceStmtIndex, int destStmtIndex) {
        Set<MethodExecutionPath> paths = new HashSet<>();
        LinkedList<Pair<MethodSection, MethodExecutionPath>> queue = new LinkedList<>();
        MethodSection sourceSection = getSectionForStatement(sourceStmtIndex);
        MethodSection destinationSection = getSectionForStatement(destStmtIndex);
        MethodExecutionPath firstPath = new MethodExecutionPath(this, sourceStmtIndex, destStmtIndex);
        firstPath.sectionsVisited.add(sourceSection);
        queue.add(new ImmutablePair<>(sourceSection, firstPath));
        while(!queue.isEmpty()) {
            // if there are too many items in the queue, the method is very complex; return an empty set
            if(queue.size() >= 10000) {
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
                    if(currentNode == sourceSection && jump.jumpStmtIndex != -1 && sourceStmtIndex > jump.jumpStmtIndex) {
                        continue;
                    }

                    MethodExecutionPath copied = currentPath.copy();
                    copied.path.add(jump);
                    copied.sectionsVisited.add(jump.toSection);
                    queue.add(new ImmutablePair<>(jump.toSection, copied));
                }
            }
        }
        return paths;
    }

    public Set<MethodSectionJump> getJumpsToSection(MethodSection section) {
        Set<MethodSectionJump> jumps = new HashSet<>();
        for(Map.Entry<MethodSection, List<MethodSectionJump>> entry : controlFlowGraph.adjacency.entrySet()) {
            for(MethodSectionJump jump : entry.getValue()) {
                if(jump.toSection.equals(section)) {
                    jumps.add(jump);
                }
            }
        }
        return jumps;
    }

    public ProgramSlice getBackwardsSlice(int criterionStmtIndex, int resultRegisterIndex) {
        Set<MethodExecutionPath> stringPaths = this.getExecutionPaths(0, criterionStmtIndex);
        if(stringPaths.size() > 1000) {
            return null;
        }
        List<Set<Integer>> statementsSet = new ArrayList<>();
        ProgramSlice slice = new ProgramSlice(apkPath, file, this, criterionStmtIndex, resultRegisterIndex);

        // build register dependency graphs and compute all involved statements
        boolean[] involvedStatements = new boolean[methodNode.codeNode.stmts.size()];
        Set<Integer> undefinedRegisters = new HashSet<>();
        for(MethodExecutionPath path : stringPaths) {
            path.buildRegisterDependencyGraph();
            RegisterDependencyNode rootNode = path.registerDependencyGraph.activeRegister.get(resultRegisterIndex);
            statementsSet.add(path.registerDependencyGraph.getInvolvedStatementsForNode(rootNode));
            Set<RegisterDependencyNode> dependencies = path.registerDependencyGraph.getDependencies(rootNode);
            for(Integer undefinedRegister : path.registerDependencyGraph.undefinedRegisters) {
                if(dependencies.contains(new RegisterDependencyNode(undefinedRegister, 0))) {
                    undefinedRegisters.add(undefinedRegister);
                }
            }
        }

        // test whether the statement sets are the same, if so, jumps do not matter and we can exclude them
        boolean areEqual = true;
        for(int i = 0; i < statementsSet.size() - 1; i++) {
            if(!statementsSet.get(i).equals(statementsSet.get(i + 1))) {
                areEqual = false;
                break;
            }
        }

        boolean includeJumps = true;
        if(areEqual) {
            includeJumps = false;
        }

        // compute involved statements
        for(MethodExecutionPath path : stringPaths) {
            boolean[] pathInvolvedStatements = path.computeInvolvedStatements(resultRegisterIndex, includeJumps);
            for(int i = 0; i < pathInvolvedStatements.length; i++) {
                involvedStatements[i] = involvedStatements[i] || pathInvolvedStatements[i];
            }
        }

        for(int i = 0; i < involvedStatements.length; i++) {
            DexStmtNode stmtNode = methodNode.codeNode.stmts.get(i);
            if(involvedStatements[i]) {
                slice.extractedStatements.add(stmtNode);
            }
        }

        return slice;
    }

}
