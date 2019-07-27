package main;

import com.googlecode.d2j.node.insn.*;
import com.googlecode.d2j.reader.Op;

import java.io.File;
import java.util.*;

public class ProgramSlice {

    public final String apkPath;
    public final File file;
    public final Method method;
    public final int criterionStmtIndex;
    public int resultRegisterIndex;
    public List<DexStmtNode> extractedStatements = new ArrayList<>();
    public int exectionResultCode = -1;
    public String executionStdout = null;
    public String executionStderr = null;
    public Set<Integer> unresolvedAtEnd = new HashSet<>();
    public Set<FieldStmtNode> fieldDependencies = new HashSet<>();

    public ProgramSlice(String apkPath, File file, Method method, int criterionStmtIndex, int resultRegisterIndex) {
        this.apkPath = apkPath;
        this.file = file;
        this.method = method;
        this.criterionStmtIndex = criterionStmtIndex;
        this.resultRegisterIndex = resultRegisterIndex;
    }

    public void compute() {
        Set<Integer> allIncludedStatements = new HashSet<>();

        for(int run = 0; run < 20; run++) { // TODO adapt!
            // take random path through the method
            int currentStmtIndex = criterionStmtIndex;
            Set<Integer> unresolved = new HashSet<>();
            unresolved.add(this.resultRegisterIndex);
            int moveNodeIndex = -1;
            Set<Integer> includedStatements = new HashSet<>();

            while(currentStmtIndex >= 0) {
                DexStmtNode stmtNode = method.methodNode.codeNode.stmts.get(currentStmtIndex);
                if(stmtNode.op == Op.MOVE_RESULT || stmtNode.op == Op.MOVE_RESULT_OBJECT || stmtNode.op == Op.MOVE_RESULT_WIDE) {
                    // store the move node for when we encounter the invoke method
                    moveNodeIndex = currentStmtIndex;
                }
                else if(stmtNode instanceof MethodStmtNode) {
                    MethodStmtNode methodStmtNode = (MethodStmtNode) stmtNode;
                    if(moveNodeIndex != -1) {
                        Stmt1RNode moveNode = (Stmt1RNode) method.methodNode.codeNode.stmts.get(moveNodeIndex);

                        if(unresolved.contains(moveNode.a)) {
                            // we have resolved a dependency - add new ones (the arguments of the method)
                            includedStatements.add(moveNodeIndex);
                            includedStatements.add(currentStmtIndex);
                            unresolved.remove(moveNode.a);
                            if(methodStmtNode.args != null) {
                                for(int arg : methodStmtNode.args) {
                                    unresolved.add(arg);
                                }
                            }
                        }
                        moveNodeIndex = -1;
                    }
                    else {
                        // the method does not seem to return anything. Only include the parameters if something is called on an unresolved variable
                        if((stmtNode.op == Op.INVOKE_DIRECT || stmtNode.op == Op.INVOKE_VIRTUAL) && unresolved.contains(methodStmtNode.args[0])) {
                            includedStatements.add(currentStmtIndex);
                            for(int arg : methodStmtNode.args) {
                                unresolved.add(arg);
                            }
                        }
                    }
                }
                else if(stmtNode.op == Op.SUB_INT || stmtNode.op == Op.DIV_DOUBLE || stmtNode.op == Op.MUL_INT || stmtNode.op == Op.SUB_LONG || stmtNode.op == Op.MUL_DOUBLE ||
                        stmtNode.op == Op.ADD_INT || stmtNode.op == Op.OR_INT || stmtNode.op == Op.AND_INT || stmtNode.op == Op.XOR_INT || stmtNode.op == Op.REM_INT ||
                        stmtNode.op == Op.SHR_LONG || stmtNode.op == Op.DIV_INT || stmtNode.op == Op.SUB_DOUBLE || stmtNode.op == Op.SHL_INT || stmtNode.op == Op.SHL_LONG ||
                        stmtNode.op == Op.OR_LONG || stmtNode.op == Op.AND_LONG || stmtNode.op == Op.DIV_LONG || stmtNode.op == Op.ADD_LONG || stmtNode.op == Op.XOR_LONG ||
                        stmtNode.op == Op.REM_LONG) {
                    Stmt3RNode castStmtNode = (Stmt3RNode) stmtNode;
                    if(unresolved.contains(castStmtNode.a)) {
                        includedStatements.add(currentStmtIndex);
                        unresolved.remove(castStmtNode.a);
                        unresolved.add(castStmtNode.b);
                        unresolved.add(castStmtNode.c);
                    }
                }
                else if(stmtNode.op == Op.INT_TO_LONG || stmtNode.op == Op.INT_TO_DOUBLE || stmtNode.op == Op.LONG_TO_DOUBLE ||
                        stmtNode.op == Op.DOUBLE_TO_INT || stmtNode.op == Op.INT_TO_FLOAT || stmtNode.op == Op.INT_TO_CHAR ||
                        stmtNode.op == Op.INT_TO_SHORT || stmtNode.op == Op.FLOAT_TO_DOUBLE || stmtNode.op == Op.LONG_TO_INT ||
                        stmtNode.op == Op.LONG_TO_FLOAT || stmtNode.op == Op.INT_TO_BYTE || stmtNode.op == Op.NEG_LONG ||
                        stmtNode.op == Op.NEG_DOUBLE || stmtNode.op == Op.NEG_FLOAT || stmtNode.op == Op.NEG_INT || stmtNode.op == Op.DOUBLE_TO_LONG ||
                        stmtNode.op == Op.DOUBLE_TO_FLOAT || stmtNode.op == Op.FLOAT_TO_INT || stmtNode.op == Op.NOT_INT) {
                    Stmt2RNode castNode = (Stmt2RNode) stmtNode;
                    if(unresolved.contains(castNode.a)) {
                        includedStatements.add(currentStmtIndex);
                        unresolved.remove(castNode.a);
                        unresolved.add(castNode.b);
                    }
                }
                else if(stmtNode.op == Op.ADD_INT_2ADDR || stmtNode.op == Op.MUL_INT_2ADDR || stmtNode.op == Op.REM_INT_2ADDR || stmtNode.op == Op.SHL_INT_2ADDR ||
                        stmtNode.op == Op.AND_INT_2ADDR || stmtNode.op == Op.SUB_LONG_2ADDR || stmtNode.op == Op.ADD_LONG_2ADDR || stmtNode.op == Op.DIV_DOUBLE_2ADDR ||
                        stmtNode.op == Op.DIV_FLOAT_2ADDR || stmtNode.op == Op.DIV_INT_2ADDR || stmtNode.op == Op.SHR_INT_2ADDR || stmtNode.op == Op.OR_INT_2ADDR ||
                        stmtNode.op == Op.XOR_INT_2ADDR || stmtNode.op == Op.SUB_INT_2ADDR || stmtNode.op == Op.MUL_LONG_2ADDR || stmtNode.op == Op.AND_LONG_2ADDR ||
                        stmtNode.op == Op.DIV_LONG_2ADDR || stmtNode.op == Op.SHR_LONG_2ADDR || stmtNode.op == Op.MUL_DOUBLE_2ADDR || stmtNode.op == Op.SUB_DOUBLE_2ADDR ||
                        stmtNode.op == Op.USHR_LONG_2ADDR || stmtNode.op == Op.MUL_FLOAT_2ADDR || stmtNode.op == Op.SUB_FLOAT_2ADDR || stmtNode.op == Op.ADD_FLOAT_2ADDR ||
                        stmtNode.op == Op.OR_LONG_2ADDR || stmtNode.op == Op.SHL_LONG_2ADDR || stmtNode.op == Op.REM_LONG_2ADDR) {
                    Stmt2RNode castNode = (Stmt2RNode) stmtNode;
                    if(unresolved.contains(castNode.a)) {
                        includedStatements.add(currentStmtIndex);
                        unresolved.add(castNode.b); // in addition to a, we need the value of b
                    }
                }
                else if(stmtNode.op == Op.XOR_INT_LIT8 || stmtNode.op == Op.ADD_INT_LIT8 || stmtNode.op == Op.MUL_INT_LIT16 ||
                        stmtNode.op == Op.SHL_INT_LIT8 || stmtNode.op == Op.MUL_INT_LIT8 || stmtNode.op == Op.AND_INT_LIT16 ||
                        stmtNode.op == Op.DIV_INT_LIT8 || stmtNode.op == Op.ADD_INT_LIT16 || stmtNode.op == Op.SHR_INT_LIT8 ||
                        stmtNode.op == Op.AND_INT_LIT8 || stmtNode.op == Op.RSUB_INT_LIT8 || stmtNode.op == Op.REM_INT_LIT8 ||
                        stmtNode.op == Op.DIV_INT_LIT16 || stmtNode.op == Op.OR_INT_LIT8 || stmtNode.op == Op.OR_INT_LIT16 ||
                        stmtNode.op == Op.USHR_INT_LIT8 || stmtNode.op == Op.REM_INT_LIT16) {
                    Stmt2R1NNode castStmtNode = (Stmt2R1NNode) stmtNode;
                    if(unresolved.contains(castStmtNode.distReg)) {
                        includedStatements.add(currentStmtIndex);
                        unresolved.remove(castStmtNode.distReg);
                        unresolved.add(castStmtNode.srcReg);
                    }
                }
                else if(stmtNode.op == Op.APUT || stmtNode.op == Op.APUT_OBJECT || stmtNode.op == Op.APUT_CHAR || stmtNode.op == Op.APUT_BYTE) {
                    Stmt3RNode castNode = (Stmt3RNode) stmtNode;
                    if(unresolved.contains(castNode.b)) {
                        includedStatements.add(currentStmtIndex);
                        unresolved.add(castNode.a);
                        unresolved.add(castNode.c);
                    }
                }
                else if(stmtNode instanceof ConstStmtNode) {
                    // definition of a constant -> set new active register
                    ConstStmtNode constStmtNode = (ConstStmtNode) stmtNode;
                    if(unresolved.contains(constStmtNode.a)) {
                        includedStatements.add(currentStmtIndex);
                        unresolved.remove(constStmtNode.a);
                    }
                }
                else if(stmtNode instanceof JumpStmtNode) {
                    JumpStmtNode jumpStmtNode = (JumpStmtNode) stmtNode;
                    includedStatements.add(currentStmtIndex);
                    if(stmtNode.op != Op.GOTO && stmtNode.op != Op.GOTO_16 && stmtNode.op != Op.GOTO_32) {
                        unresolved.add(jumpStmtNode.a);
                    }
                }
                else if(stmtNode.op == Op.NEW_INSTANCE) {
                    TypeStmtNode typeStmtNode = (TypeStmtNode) stmtNode;
                    if(unresolved.contains(typeStmtNode.a)) {
                        includedStatements.add(currentStmtIndex);
                        unresolved.remove(typeStmtNode.a);
                    }
                }
                else if(stmtNode.op == Op.SGET || stmtNode.op == Op.SGET_BOOLEAN || stmtNode.op == Op.SGET_OBJECT || stmtNode.op == Op.SGET_WIDE ||
                        stmtNode.op == Op.IGET_OBJECT || stmtNode.op == Op.IGET || stmtNode.op == Op.IGET_BOOLEAN || stmtNode.op == Op.IGET_WIDE ||
                        stmtNode.op == Op.IGET_CHAR || stmtNode.op == Op.IGET_BYTE || stmtNode.op == Op.IGET_SHORT) {
                    // we are getting a field which is stored in a new register
                    FieldStmtNode fieldStmtNode = (FieldStmtNode) stmtNode;
                    if(unresolved.contains(fieldStmtNode.a)) {
                        includedStatements.add(currentStmtIndex);
                        unresolved.remove(fieldStmtNode.a);
                        fieldDependencies.add(fieldStmtNode);
                    }
                }
                else if(stmtNode.op == Op.NEW_ARRAY) {
                    TypeStmtNode typeStmtNode = (TypeStmtNode) stmtNode;
                    if(unresolved.contains(typeStmtNode.a)) {
                        includedStatements.add(currentStmtIndex);
                        unresolved.remove(typeStmtNode.a);
                        unresolved.add(typeStmtNode.b);
                    }
                }
                else if(stmtNode.op == Op.THROW) {
                    // ignore throw
                }
                else if(stmtNode.op == Op.CHECK_CAST) {
                    // ignore check-cast
                }
                else if(stmtNode.op == Op.CMP_LONG || stmtNode.op == Op.CMPL_DOUBLE || stmtNode.op == Op.CMPL_FLOAT ||
                        stmtNode.op == Op.CMPG_DOUBLE || stmtNode.op == Op.CMPG_FLOAT) {
                    Stmt3RNode castNode = (Stmt3RNode) stmtNode;
                    if(unresolved.contains(castNode.a)) {
                        includedStatements.add(currentStmtIndex);
                        unresolved.remove(castNode.a);
                        unresolved.add(castNode.b);
                        unresolved.add(castNode.c);
                    }
                }
                else if(stmtNode.op == Op.MOVE || stmtNode.op == Op.MOVE_OBJECT || stmtNode.op == Op.MOVE_OBJECT_FROM16 ||
                        stmtNode.op == Op.MOVE_WIDE || stmtNode.op == Op.MOVE_WIDE_FROM16 || stmtNode.op == Op.MOVE_FROM16) {
                    Stmt2RNode castNode = (Stmt2RNode) stmtNode;
                    if(unresolved.contains(castNode.a)) {
                        includedStatements.add(currentStmtIndex);
                        unresolved.remove(castNode.a);
                        unresolved.add(castNode.b);
                    }
                }
                else {
                    throw new RuntimeException("Unknown statement type when performing backwards slice! " + stmtNode.toString() + ", " + stmtNode.op);
                }

//                System.out.println(unresolved);

                if(unresolved.size() == 0) {
                    // we found all the variables we need
                    break;
                }

                // find how we can reach this node, make a random decision if there are multiple options
                List<ControlFlowGraphNode> prevNodes = method.controlFlowGraph.getNode(currentStmtIndex).prevNodes;
                ControlFlowGraphNode prevNode = prevNodes.get((new Random()).nextInt(prevNodes.size()));
                currentStmtIndex = prevNode.stmtIndex;
                // TODO do not take path twice!
            }

            // done - merge involved statements
            allIncludedStatements.addAll(includedStatements);
        }

        List<Integer> includedStmtList = new ArrayList<>(allIncludedStatements);
        Collections.sort(includedStmtList);
        for(int stmtIndex : includedStmtList) {
            extractedStatements.add(method.methodNode.codeNode.stmts.get(stmtIndex));
        }
    }

    public void oldCompute() {
        Set<MethodExecutionPath> paths = method.getExecutionPaths(0, this.criterionStmtIndex);
        System.out.println("paths: " + paths.size());

        // compute a program slice with a backwards BFS
        LinkedList<ProgramSliceIntermediateState> queue = new LinkedList<>();
        Set<Integer> allIncludedStatements = new HashSet<>();

        // backwards search
        ProgramSliceIntermediateState initialState = new ProgramSliceIntermediateState(criterionStmtIndex);
        initialState.startSection = this.method.getSectionForStatement(criterionStmtIndex);
        initialState.unresolved = new HashSet<>();
        initialState.unresolved.add(this.resultRegisterIndex);
        initialState.jumpsTaken = new ArrayList<>();
        initialState.includedStatements = new HashSet<>();
        queue.add(initialState);

        while(!queue.isEmpty()) {
            ProgramSliceIntermediateState currentState = queue.remove();
            Set<Integer> unresolved = currentState.unresolved;
            MethodSection section = currentState.getCurrentSection();
            Set<Integer> includedStatements = currentState.includedStatements;
//            System.out.println("Considering section: " + section);

            int currentStmtIndex = currentState.getBeginIndex();
            int moveNodeIndex = -1;
            while(currentStmtIndex != section.beginIndex - 1) {
                DexStmtNode stmtNode = method.methodNode.codeNode.stmts.get(currentStmtIndex);
//                System.out.println(stmtNode.op + " (" + currentStmtIndex + ")");

                currentStmtIndex--;
            }

            // include section label
            if(currentStmtIndex >= 0) {
                includedStatements.add(currentStmtIndex);
            }

            // are we done?
            Set<MethodSectionJump> jumps = method.getJumpsToSection(section);
            if(unresolved.size() == 0 || jumps.size() == 0) {
                unresolvedAtEnd.addAll(unresolved);
                allIncludedStatements.addAll(includedStatements);
            }
            else {
                for(MethodSectionJump jump : jumps) {
                    if(!currentState.jumpsTaken.contains(jump)) {
//                        System.out.println("Adding jump " + jump + " to queue (size: " + queue.size() + ")");
                        ProgramSliceIntermediateState newState = currentState.copy();
                        newState.jumpsTaken.add(jump);
                        newState.unresolved = new HashSet<>();
                        newState.unresolved.addAll(unresolved);
                        newState.includedStatements = new HashSet<>();
                        newState.includedStatements.addAll(includedStatements);
                        queue.add(newState);
                    }
                }
            }
        }

        List<Integer> includedStmtList = new ArrayList<>(allIncludedStatements);
        Collections.sort(includedStmtList);
        for(int stmtIndex : includedStmtList) {
            extractedStatements.add(method.methodNode.codeNode.stmts.get(stmtIndex));
        }
    }

    public boolean isSubslice(ProgramSlice other) {
        // check whether the other slice is a subslice of this one
        if(other.extractedStatements.size() < extractedStatements.size()) {
            return false;
        }

        Set<DexStmtNode> thisSet = new HashSet<>(extractedStatements);
        Set<DexStmtNode> otherSet = new HashSet<>(extractedStatements);
        return otherSet.containsAll(thisSet);
    }

}
