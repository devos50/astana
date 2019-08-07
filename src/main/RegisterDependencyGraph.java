package main;

import com.googlecode.d2j.node.insn.*;
import com.googlecode.d2j.reader.Op;

import java.util.*;

public class RegisterDependencyGraph {

    public MethodExecutionPath methodExecutionPath;
    public Map<RegisterDependencyNode, List<RegisterDependencyNode>> adjacency = new HashMap<>();
    public Map<Integer, RegisterDependencyNode> activeRegister = new HashMap<>();
    public ArrayList<Set<RegisterDependencyNode>> statementToRegister;
    public Set<Integer> visitedStatements = new HashSet<>();
    public Set<Integer> undefinedRegisters;

    public RegisterDependencyGraph(MethodExecutionPath methodExecutionPath) {
        this.methodExecutionPath = methodExecutionPath;
        this.statementToRegister = new ArrayList<>();
        this.undefinedRegisters = new HashSet<>();
        for(int i = 0; i < this.methodExecutionPath.method.methodNode.codeNode.stmts.size(); i++) {
            statementToRegister.add(new HashSet<>());
        }
    }

    private RegisterDependencyNode makeNewRegister(int registerIndex) {
        int registerSequenceNumber = 1;
        if(activeRegister.containsKey(registerIndex)) {
            registerSequenceNumber = activeRegister.get(registerIndex).sequenceNumber + 1;
        }
        RegisterDependencyNode newNode = new RegisterDependencyNode(registerIndex, registerSequenceNumber);
        activeRegister.put(registerIndex, newNode);
        adjacency.put(newNode, new ArrayList<>());

        return newNode;
    }

    private void makeDependency(RegisterDependencyNode a, RegisterDependencyNode b) {
        // make a dependency from a to b
        adjacency.get(a).add(b);
    }

    private int getCorrespondingMethodNode(int stmtIndex) {
        for(int index = stmtIndex; index >= 0; index--) {
            DexStmtNode stmtNode = methodExecutionPath.method.methodNode.codeNode.stmts.get(index);
            if(stmtNode instanceof MethodStmtNode) {
                return index;
            }
            else if(stmtNode.op == Op.FILLED_NEW_ARRAY || stmtNode.op == Op.FILLED_NEW_ARRAY_RANGE) {
                return index;
            }
        }
        return -1;
    }

    private RegisterDependencyNode getActiveRegister(int registerIndex) {
        RegisterDependencyNode node = activeRegister.get(registerIndex);
        if(node == null) {
            // this register is not defined so far!
            undefinedRegisters.add(registerIndex);

            // define it and return the new active register
            RegisterDependencyNode newNode = new RegisterDependencyNode(registerIndex, 0);
            activeRegister.put(registerIndex, newNode);
            adjacency.put(newNode, new ArrayList<>());
            return newNode;
        }
        return node;
    }

    public Set<RegisterDependencyNode> getDependencies(RegisterDependencyNode rootNode) {
        Set<RegisterDependencyNode> visited = new HashSet<>();
        LinkedList<RegisterDependencyNode> queue = new LinkedList<>();
        visited.add(rootNode);
        queue.add(rootNode);

        while(!queue.isEmpty()) {
            RegisterDependencyNode currentNode = queue.remove();
            List<RegisterDependencyNode> adjacent = adjacency.get(currentNode);
            if(adjacent == null) { continue; }

            for(RegisterDependencyNode adjacentNode : adjacent) {
                if(!visited.contains(adjacentNode)) {
                    visited.add(adjacentNode);
                    queue.add(adjacentNode);
                }
            }
        }

        return visited;
    }

    public Set<Integer> getInvolvedStatementsForNode(RegisterDependencyNode rootNode) {
        Set<RegisterDependencyNode> dependencies = getDependencies(rootNode);
        Set<Integer> involvedStatements = new HashSet<>();
        for(RegisterDependencyNode dependency : dependencies) {
            for(int stmtIndex = 0; stmtIndex < statementToRegister.size(); stmtIndex++) {
                if(statementToRegister.get(stmtIndex).contains(dependency)) {
                    involvedStatements.add(stmtIndex);
                }
            }
        }
        return involvedStatements;
    }

    public boolean hasDependency(RegisterDependencyNode source, RegisterDependencyNode dest) {
        return getDependencies(source).contains(dest);
    }

    public void build() {
        // we now build the register dependency graph in a forward way. Start from the string declaration and end at the potential line where the string is decrypted, while following the path.
//        System.out.println("Building register dependency graph: ");
        int currentStmtIndex = methodExecutionPath.sourceStmtIndex;
        int currentJumpIndex = 0;
        while(true) {
            DexStmtNode stmtNode = methodExecutionPath.method.methodNode.codeNode.stmts.get(currentStmtIndex);
            visitedStatements.add(currentStmtIndex);
//            System.out.println(stmtNode.op + "(" + currentStmtIndex + ")");

            if(stmtNode instanceof ConstStmtNode) {
                // definition of a constant -> set new active register
                ConstStmtNode constStmtNode = (ConstStmtNode) stmtNode;
                RegisterDependencyNode newRegister = makeNewRegister(constStmtNode.a);
                statementToRegister.get(currentStmtIndex).add(newRegister);
            }
            else if(stmtNode instanceof MethodStmtNode) {
                // ignore for now, instead, process the move-result statement instead
                MethodStmtNode mnn = (MethodStmtNode) stmtNode;

                // there is one edge case though: where we have an invoke-direct on a string. We should actually consider this one since there is no subsequent move statement
                if(stmtNode.op == Op.INVOKE_DIRECT) {
                    if(mnn.method.getName().equals("<init>")) {
                        int[] args = new int[0];
                        if(mnn.args != null) { args = mnn.args; }
                        RegisterDependencyNode[] argNodes = new RegisterDependencyNode[args.length];
                        for(int i = 0; i < args.length; i++) { argNodes[i] = getActiveRegister(mnn.args[i]); }

                        RegisterDependencyNode newActive = makeNewRegister(mnn.args[0]);
                        statementToRegister.get(currentStmtIndex).add(newActive);

                        // make dependencies
                        for(int i = 0; i < args.length; i++) {
                            makeDependency(newActive, argNodes[i]);
                        }
                    }
                }
            }
            else if(stmtNode.op == Op.MOVE_EXCEPTION) {
                // TODO ignore move-exception for now!
            }
            else if(stmtNode.op == Op.MOVE_RESULT || stmtNode.op == Op.MOVE_RESULT_OBJECT || stmtNode.op == Op.MOVE_RESULT_WIDE) {
                // we store the result from a method call, create dependencies
                Stmt1RNode moveStmtNode = (Stmt1RNode) stmtNode;

                int prevMethodStmtIndex = getCorrespondingMethodNode(currentStmtIndex);
                if(prevMethodStmtIndex == -1) {
                    throw new RuntimeException("Could not find corresponding method for move-result(-object)! " + currentStmtIndex);
                }

                int[] args = new int[0];
                RegisterDependencyNode[] argNodes = new RegisterDependencyNode[0];
                DexStmtNode prevStmtNode = methodExecutionPath.method.methodNode.codeNode.stmts.get(prevMethodStmtIndex);
                if(prevStmtNode instanceof FilledNewArrayStmtNode) {
                    FilledNewArrayStmtNode arrayStmtNode = (FilledNewArrayStmtNode) prevStmtNode;
                    if(arrayStmtNode.args != null) { args = arrayStmtNode.args; }
                    argNodes = new RegisterDependencyNode[args.length];
                    for(int i = 0; i < args.length; i++) { argNodes[i] = getActiveRegister(arrayStmtNode.args[i]); }
                }
                else if(prevStmtNode instanceof MethodStmtNode) {
                    MethodStmtNode prevMethodStmtNode = (MethodStmtNode) prevStmtNode;
                    if(prevMethodStmtNode.args != null) { args = prevMethodStmtNode.args; }
                    argNodes = new RegisterDependencyNode[args.length];
                    for(int i = 0; i < args.length; i++) { argNodes[i] = getActiveRegister(prevMethodStmtNode.args[i]); }
                }

                RegisterDependencyNode newRegister = makeNewRegister(moveStmtNode.a);
                statementToRegister.get(currentStmtIndex).add(newRegister);
                statementToRegister.get(prevMethodStmtIndex).add(newRegister);

                // make dependencies
                for(int i = 0; i < args.length; i++) {
                    makeDependency(newRegister, argNodes[i]);
                }
            }
            else if(stmtNode.op == Op.INSTANCE_OF) {
                TypeStmtNode typeStmtNode = (TypeStmtNode) stmtNode;
                RegisterDependencyNode newRegister = makeNewRegister(typeStmtNode.a);
                makeDependency(newRegister, getActiveRegister(typeStmtNode.b));
                statementToRegister.get(currentStmtIndex).add(newRegister);
            }
            else if(stmtNode.op == Op.CMP_LONG || stmtNode.op == Op.CMPL_DOUBLE || stmtNode.op == Op.CMPL_FLOAT ||
                    stmtNode.op == Op.CMPG_DOUBLE || stmtNode.op == Op.CMPG_FLOAT) {
                Stmt3RNode castNode = (Stmt3RNode) stmtNode;
                RegisterDependencyNode newRegister = makeNewRegister(castNode.a);
                makeDependency(newRegister, getActiveRegister(castNode.b));
                makeDependency(newRegister, getActiveRegister(castNode.c));
                statementToRegister.get(currentStmtIndex).add(newRegister);
            }
            else if(stmtNode.op == Op.INT_TO_LONG || stmtNode.op == Op.INT_TO_DOUBLE || stmtNode.op == Op.LONG_TO_DOUBLE ||
                    stmtNode.op == Op.DOUBLE_TO_INT || stmtNode.op == Op.INT_TO_FLOAT || stmtNode.op == Op.INT_TO_CHAR ||
                    stmtNode.op == Op.INT_TO_SHORT || stmtNode.op == Op.FLOAT_TO_DOUBLE || stmtNode.op == Op.LONG_TO_INT ||
                    stmtNode.op == Op.LONG_TO_FLOAT || stmtNode.op == Op.INT_TO_BYTE || stmtNode.op == Op.NEG_LONG ||
                    stmtNode.op == Op.NEG_DOUBLE || stmtNode.op == Op.NEG_FLOAT || stmtNode.op == Op.NEG_INT || stmtNode.op == Op.DOUBLE_TO_LONG ||
                    stmtNode.op == Op.DOUBLE_TO_FLOAT || stmtNode.op == Op.FLOAT_TO_INT || stmtNode.op == Op.NOT_INT) {
                Stmt2RNode castNode = (Stmt2RNode) stmtNode;
                RegisterDependencyNode dependencyRegister = getActiveRegister(castNode.b);
                RegisterDependencyNode newRegister = makeNewRegister(castNode.a);
                makeDependency(newRegister, dependencyRegister);
                statementToRegister.get(currentStmtIndex).add(newRegister);
            }
            else if(stmtNode.op == Op.MOVE || stmtNode.op == Op.MOVE_OBJECT || stmtNode.op == Op.MOVE_OBJECT_FROM16 ||
                    stmtNode.op == Op.MOVE_WIDE || stmtNode.op == Op.MOVE_WIDE_FROM16 || stmtNode.op == Op.MOVE_FROM16) {
                Stmt2RNode castNode = (Stmt2RNode) stmtNode;
                RegisterDependencyNode dependencyRegister = getActiveRegister(castNode.b);
                RegisterDependencyNode newRegister = makeNewRegister(castNode.a);
                makeDependency(newRegister, dependencyRegister);
                statementToRegister.get(currentStmtIndex).add(newRegister);
            }
            else if(stmtNode.op == Op.SGET || stmtNode.op == Op.SGET_BOOLEAN || stmtNode.op == Op.SGET_OBJECT || stmtNode.op == Op.SGET_WIDE ||
                    stmtNode.op == Op.SGET_SHORT || stmtNode.op == Op.SGET_CHAR || stmtNode.op == Op.SGET_BYTE) {
                // we are getting a field which is stored in a new register
                FieldStmtNode fieldStmtNode = (FieldStmtNode) stmtNode;
                RegisterDependencyNode newRegister = makeNewRegister(fieldStmtNode.a);
                statementToRegister.get(currentStmtIndex).add(newRegister);
            }
            else if(stmtNode.op == Op.IGET_OBJECT || stmtNode.op == Op.IGET || stmtNode.op == Op.IGET_BOOLEAN || stmtNode.op == Op.IGET_WIDE ||
                    stmtNode.op == Op.IGET_CHAR || stmtNode.op == Op.IGET_BYTE || stmtNode.op == Op.IGET_SHORT) {
                FieldStmtNode fieldStmtNode = (FieldStmtNode) stmtNode;
                RegisterDependencyNode newRegister = makeNewRegister(fieldStmtNode.a);
                statementToRegister.get(currentStmtIndex).add(newRegister);
            }
            else if(stmtNode.op == Op.NEW_INSTANCE) {
                TypeStmtNode typeStmtNode = (TypeStmtNode) stmtNode;
                RegisterDependencyNode newRegister = makeNewRegister(typeStmtNode.a);
                statementToRegister.get(currentStmtIndex).add(newRegister);
            }
            else if(stmtNode.op == Op.NEW_ARRAY) {
                TypeStmtNode typeStmtNode = (TypeStmtNode) stmtNode;
                RegisterDependencyNode dependencyRegister = getActiveRegister(typeStmtNode.b);
                RegisterDependencyNode newRegister = makeNewRegister(typeStmtNode.a);
                makeDependency(newRegister, dependencyRegister);
                statementToRegister.get(currentStmtIndex).add(newRegister);
            }
            else if(stmtNode.op == Op.APUT || stmtNode.op == Op.APUT_OBJECT || stmtNode.op == Op.APUT_CHAR || stmtNode.op == Op.APUT_BYTE || stmtNode.op == Op.APUT_WIDE || stmtNode.op == Op.APUT_SHORT) {
                Stmt3RNode castNode = (Stmt3RNode) stmtNode;
                makeDependency(getActiveRegister(castNode.b), getActiveRegister(castNode.a));
                makeDependency(getActiveRegister(castNode.b), getActiveRegister(castNode.c));
                statementToRegister.get(currentStmtIndex).add(getActiveRegister(castNode.b));
            }
            else if(stmtNode.op == Op.AGET_WIDE || stmtNode.op == Op.AGET || stmtNode.op == Op.AGET_BOOLEAN ||
                    stmtNode.op == Op.AGET_OBJECT || stmtNode.op == Op.AGET_BYTE || stmtNode.op == Op.AGET_CHAR || stmtNode.op == Op.AGET_SHORT) {
                Stmt3RNode castNode = (Stmt3RNode) stmtNode;
                makeDependency(getActiveRegister(castNode.a), getActiveRegister(castNode.b));
                statementToRegister.get(currentStmtIndex).add(getActiveRegister(castNode.a));
            }
            else if(stmtNode.op == Op.SPUT || stmtNode.op == Op.SPUT_OBJECT || stmtNode.op == Op.SPUT_WIDE || stmtNode.op == Op.SPUT_BOOLEAN ||
                    stmtNode.op == Op.SPUT_BYTE || stmtNode.op == Op.SPUT_CHAR || stmtNode.op == Op.SPUT_SHORT) {
                // TODO ignore sput for now
            }
            else if(stmtNode.op == Op.ARRAY_LENGTH) {
                Stmt2RNode castNode = (Stmt2RNode) stmtNode;
                RegisterDependencyNode newRegister = makeNewRegister(castNode.a);
                makeDependency(newRegister, getActiveRegister(castNode.b));
                statementToRegister.get(currentStmtIndex).add(newRegister);
            }
            else if(stmtNode.op == Op.SUB_INT || stmtNode.op == Op.DIV_DOUBLE || stmtNode.op == Op.MUL_INT || stmtNode.op == Op.SUB_LONG || stmtNode.op == Op.MUL_DOUBLE ||
                    stmtNode.op == Op.ADD_INT || stmtNode.op == Op.OR_INT || stmtNode.op == Op.AND_INT || stmtNode.op == Op.XOR_INT || stmtNode.op == Op.REM_INT ||
                    stmtNode.op == Op.SHR_LONG || stmtNode.op == Op.DIV_INT || stmtNode.op == Op.SUB_DOUBLE || stmtNode.op == Op.SHL_INT || stmtNode.op == Op.SHL_LONG ||
                    stmtNode.op == Op.OR_LONG || stmtNode.op == Op.AND_LONG || stmtNode.op == Op.DIV_LONG || stmtNode.op == Op.ADD_LONG || stmtNode.op == Op.XOR_LONG ||
                    stmtNode.op == Op.REM_LONG || stmtNode.op == Op.MUL_LONG || stmtNode.op == Op.REM_FLOAT || stmtNode.op == Op.DIV_FLOAT || stmtNode.op == Op.SUB_FLOAT ||
                    stmtNode.op == Op.ADD_DOUBLE || stmtNode.op == Op.MUL_FLOAT || stmtNode.op == Op.REM_DOUBLE) {
                Stmt3RNode castStmtNode = (Stmt3RNode) stmtNode;
                RegisterDependencyNode oldRegisterA = getActiveRegister(castStmtNode.b);
                RegisterDependencyNode oldRegisterB = getActiveRegister(castStmtNode.c);
                RegisterDependencyNode newRegister = makeNewRegister(castStmtNode.a);
                makeDependency(newRegister, oldRegisterA);
                makeDependency(newRegister, oldRegisterB);
                statementToRegister.get(currentStmtIndex).add(newRegister);
            }
            else if(stmtNode.op == Op.ADD_INT_2ADDR || stmtNode.op == Op.MUL_INT_2ADDR || stmtNode.op == Op.REM_INT_2ADDR || stmtNode.op == Op.SHL_INT_2ADDR ||
                    stmtNode.op == Op.AND_INT_2ADDR || stmtNode.op == Op.SUB_LONG_2ADDR || stmtNode.op == Op.ADD_LONG_2ADDR || stmtNode.op == Op.DIV_DOUBLE_2ADDR ||
                    stmtNode.op == Op.DIV_FLOAT_2ADDR || stmtNode.op == Op.DIV_INT_2ADDR || stmtNode.op == Op.SHR_INT_2ADDR || stmtNode.op == Op.OR_INT_2ADDR ||
                    stmtNode.op == Op.XOR_INT_2ADDR || stmtNode.op == Op.SUB_INT_2ADDR || stmtNode.op == Op.MUL_LONG_2ADDR || stmtNode.op == Op.AND_LONG_2ADDR ||
                    stmtNode.op == Op.DIV_LONG_2ADDR || stmtNode.op == Op.SHR_LONG_2ADDR || stmtNode.op == Op.MUL_DOUBLE_2ADDR || stmtNode.op == Op.SUB_DOUBLE_2ADDR ||
                    stmtNode.op == Op.USHR_LONG_2ADDR || stmtNode.op == Op.MUL_FLOAT_2ADDR || stmtNode.op == Op.SUB_FLOAT_2ADDR || stmtNode.op == Op.ADD_FLOAT_2ADDR ||
                    stmtNode.op == Op.OR_LONG_2ADDR || stmtNode.op == Op.SHL_LONG_2ADDR || stmtNode.op == Op.REM_LONG_2ADDR || stmtNode.op == Op.ADD_DOUBLE_2ADDR) {
                Stmt2RNode castStmtNode = (Stmt2RNode) stmtNode;

                RegisterDependencyNode oldRegister = getActiveRegister(castStmtNode.a);
                RegisterDependencyNode newRegister = makeNewRegister(castStmtNode.a);
                makeDependency(newRegister, oldRegister);
                makeDependency(newRegister, getActiveRegister(castStmtNode.b));
                statementToRegister.get(currentStmtIndex).add(newRegister);
            }
            else if(stmtNode.op == Op.XOR_INT_LIT8 || stmtNode.op == Op.ADD_INT_LIT8 || stmtNode.op == Op.MUL_INT_LIT16 ||
                    stmtNode.op == Op.SHL_INT_LIT8 || stmtNode.op == Op.MUL_INT_LIT8 || stmtNode.op == Op.AND_INT_LIT16 ||
                    stmtNode.op == Op.DIV_INT_LIT8 || stmtNode.op == Op.ADD_INT_LIT16 || stmtNode.op == Op.SHR_INT_LIT8 ||
                    stmtNode.op == Op.AND_INT_LIT8 || stmtNode.op == Op.RSUB_INT_LIT8 || stmtNode.op == Op.REM_INT_LIT8 ||
                    stmtNode.op == Op.DIV_INT_LIT16 || stmtNode.op == Op.OR_INT_LIT8 || stmtNode.op == Op.OR_INT_LIT16 ||
                    stmtNode.op == Op.USHR_INT_LIT8 || stmtNode.op == Op.REM_INT_LIT16 || stmtNode.op == Op.XOR_INT_LIT16) {
                Stmt2R1NNode castStmtNode = (Stmt2R1NNode) stmtNode;
                RegisterDependencyNode oldRegisterSrc = getActiveRegister(castStmtNode.srcReg);
                RegisterDependencyNode newRegister = makeNewRegister(castStmtNode.distReg);
                makeDependency(newRegister, oldRegisterSrc);
                statementToRegister.get(currentStmtIndex).add(newRegister);
            }
            else if(stmtNode.op == Op.IPUT_OBJECT || stmtNode.op == Op.IPUT_BOOLEAN || stmtNode.op == Op.IPUT || stmtNode.op == Op.IPUT_WIDE || stmtNode.op == Op.IPUT_SHORT || stmtNode.op == Op.IPUT_BYTE) {
                // TODO ignore iput for now!
            }
            else if(stmtNode.op == Op.FILLED_NEW_ARRAY || stmtNode.op == Op.FILLED_NEW_ARRAY_RANGE) {
                // filled new arrays are processed by move-result statements
            }
            else if(stmtNode.op == Op.FILL_ARRAY_DATA) {
                // TODO ignore fill array data for now!
            }
            else if(stmtNode.op == Op.MONITOR_ENTER || stmtNode.op == Op.MONITOR_EXIT) {
                // TODO ignore monitors for now!
            }
            else if(stmtNode.op == Op.THROW) {
                // TODO ignore throw!
            }
            else if(stmtNode.op == Op.CHECK_CAST) {
                TypeStmtNode typeStmtNode = (TypeStmtNode) stmtNode;
                statementToRegister.get(currentStmtIndex).add(getActiveRegister(typeStmtNode.a));
            }
            else if(stmtNode.op == Op.RETURN || stmtNode.op == Op.RETURN_OBJECT || stmtNode.op == Op.RETURN_VOID) {
                // TODO ignore returns for now!
            }
            else if(stmtNode instanceof JumpStmtNode) {
                JumpStmtNode jumpStmtNode = (JumpStmtNode) stmtNode;
                if(stmtNode.op != Op.GOTO && stmtNode.op != Op.GOTO_16 && stmtNode.op != Op.GOTO_32) {
                    statementToRegister.get(currentStmtIndex).add(getActiveRegister(jumpStmtNode.a));
                }
            }
            else if(stmtNode instanceof DexLabelStmtNode) {
                // TODO ignore labels for now!
            }
            else if(stmtNode instanceof SparseSwitchStmtNode) {
                // TODO ignore sparse switch for now!
            }
            else if(stmtNode instanceof PackedSwitchStmtNode) {
                // TODO ignore packed switches for now!
            }
            else if(stmtNode.op == Op.NOP) {
                // TODO ignore nops
            }
            else {
                throw new RuntimeException("Unknown statement type when building dependency graph! " + stmtNode.toString() + ", " + stmtNode.op);
            }

//            System.out.println(adjacency);

            // are we done?
            if(currentStmtIndex == methodExecutionPath.destStmtIndex) {
                break;
            }

            // are we at a jump that we should take it?
            if(stmtNode.op == Op.GOTO || stmtNode.op == Op.GOTO_16 || stmtNode.op == Op.GOTO_32) {
                JumpStmtNode jumpStmtNode = (JumpStmtNode) stmtNode;
                currentStmtIndex = methodExecutionPath.method.getSectionForLabel(jumpStmtNode.label).beginIndex;
            }
            else if(currentJumpIndex < methodExecutionPath.path.size()) {
                JumpDecision jumpDecision = methodExecutionPath.path.get(currentJumpIndex);
                if(currentStmtIndex == jumpDecision.fromStmtIndex) {
                    currentStmtIndex = jumpDecision.toStmtIndex;
                    currentJumpIndex++;
                }
                else {
                    currentStmtIndex++;
                }
            }
            else {
                currentStmtIndex++;
            }
        }
    }
}
