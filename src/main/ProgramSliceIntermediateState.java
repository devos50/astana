package main;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ProgramSliceIntermediateState {

    public MethodSection startSection;
    public Set<Integer> unresolved;
    public List<MethodSectionJump> jumpsTaken;
    public int criterionStmtIndex;
    public Set<Integer> includedStatements;

    public ProgramSliceIntermediateState(int criterionStmtIndex) {
        this.criterionStmtIndex = criterionStmtIndex;
    }

    public ProgramSliceIntermediateState copy() {
        ProgramSliceIntermediateState newState = new ProgramSliceIntermediateState(criterionStmtIndex);
        newState.startSection = startSection;
        newState.unresolved = new HashSet<>(unresolved);
        newState.jumpsTaken = new ArrayList<>(jumpsTaken);
        newState.includedStatements = new HashSet<>(includedStatements);
        return newState;
    }

    public MethodSection getCurrentSection() {
        if(jumpsTaken.size() == 0) { return startSection; }
        return jumpsTaken.get(jumpsTaken.size() - 1).fromSection;
    }

    public int getBeginIndex() {
        if(jumpsTaken.size() == 0) { return criterionStmtIndex; }
        return getCurrentSection().endIndex - 1;
    }
}
