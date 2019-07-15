import com.googlecode.d2j.node.insn.*;
import com.googlecode.d2j.reader.Op;

import java.util.*;

public class RegisterDependencyGraph {

    public MethodExecutionPath methodExecutionPath;
    public Map<RegisterDependencyNode, List<RegisterDependencyNode>> adjacency = new HashMap<>();
    public Map<Integer, RegisterDependencyNode> activeRegister = new HashMap<>();
    public RegisterDependencyNode[] statementToRegister;

    public RegisterDependencyGraph(MethodExecutionPath methodExecutionPath) {
        this.methodExecutionPath = methodExecutionPath;
        this.statementToRegister = new RegisterDependencyNode[this.methodExecutionPath.method.methodNode.codeNode.stmts.size()];
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
        }
        return -1;
    }

    private RegisterDependencyNode getActiveRegister(int registerIndex) {
        return activeRegister.get(registerIndex);
    }

    public void build() {
        // we now build the register dependency graph in a forward way. Start from the string declaration and end at the potential line where the string is decrypted, while following the path.
        System.out.println("Building register dependency graph: " + methodExecutionPath.path);
        int currentStmtIndex = methodExecutionPath.stringInitIndex;
        MethodSection currentSection = methodExecutionPath.method.getSectionForStatement(methodExecutionPath.stringInitIndex);
        int currentJumpIndex = 0;
        while(true) {
            DexStmtNode stmtNode = methodExecutionPath.method.methodNode.codeNode.stmts.get(currentStmtIndex);
            if(stmtNode instanceof ConstStmtNode) {
                // definition of a constant -> set new active register
                ConstStmtNode constStmtNode = (ConstStmtNode) stmtNode;
                RegisterDependencyNode newRegister = makeNewRegister(constStmtNode.a);
                statementToRegister[currentStmtIndex] = newRegister;
            }
            else if(stmtNode instanceof MethodStmtNode) {
                // ignore for now, instead, process the move-result statement instead

                // there is one edge case though: where we have an invoke-direct on a string. We should actually consider this one since there is no subsequent move statement
                if(stmtNode.op == Op.INVOKE_DIRECT) {
                    MethodStmtNode mnn = (MethodStmtNode) stmtNode;
                    if(mnn.method.getName().equals("<init>") && mnn.method.getOwner().equals("Ljava/lang/String;")) {
                        // we should make a new register for the string
                        RegisterDependencyNode newActive = makeNewRegister(mnn.args[0]);
                        statementToRegister[currentStmtIndex] = newActive;

                        // make dependencies
                        if(mnn.args != null && mnn.args.length > 0) {
                            for (int arg : mnn.args) {
                                makeDependency(newActive, getActiveRegister(arg));
                            }
                        }
                    }
                }
            }
            else if(stmtNode.op == Op.MOVE_RESULT || stmtNode.op == Op.MOVE_RESULT_OBJECT || stmtNode.op == Op.MOVE_RESULT_WIDE) {
                // we store the result from a method call, create dependencies
                Stmt1RNode moveStmtNode = (Stmt1RNode) stmtNode;

                int prevMethodStmtIndex = getCorrespondingMethodNode(currentStmtIndex);
                if(prevMethodStmtIndex == -1) {
                    throw new RuntimeException("Could not find corresponding method for move-result(-object)!");
                }
                MethodStmtNode prevMethodStmtNode = (MethodStmtNode) methodExecutionPath.method.methodNode.codeNode.stmts.get(prevMethodStmtIndex);
                int[] args = prevMethodStmtNode.args;

                // make a new register
                RegisterDependencyNode oldActiveRegister = getActiveRegister(moveStmtNode.a);
                RegisterDependencyNode newRegister = makeNewRegister(moveStmtNode.a);
                statementToRegister[currentStmtIndex] = newRegister;
                statementToRegister[prevMethodStmtIndex] = newRegister;

                // make dependencies
                if(args != null && args.length > 0) {
                    for (int arg : args) {
                        if (arg == moveStmtNode.a) {
                            makeDependency(newRegister, oldActiveRegister);
                        } else {
                            makeDependency(newRegister, getActiveRegister(arg));
                        }
                    }
                }
            }
            else if(stmtNode.op == Op.INSTANCE_OF) {
                TypeStmtNode typeStmtNode = (TypeStmtNode) stmtNode;
                RegisterDependencyNode newRegister = makeNewRegister(typeStmtNode.a);
                makeDependency(newRegister, getActiveRegister(typeStmtNode.b));
                statementToRegister[currentStmtIndex] = newRegister;
            }
            else if(stmtNode.op == Op.CMP_LONG || stmtNode.op == Op.CMPL_DOUBLE || stmtNode.op == Op.CMPL_FLOAT || stmtNode.op == Op.CMPG_DOUBLE) {
                Stmt3RNode castNode = (Stmt3RNode) stmtNode;
                RegisterDependencyNode newRegister = makeNewRegister(castNode.a);
                makeDependency(newRegister, getActiveRegister(castNode.b));
                makeDependency(newRegister, getActiveRegister(castNode.c));
                statementToRegister[currentStmtIndex] = newRegister;
            }
            else if(stmtNode.op == Op.INT_TO_LONG || stmtNode.op == Op.INT_TO_DOUBLE || stmtNode.op == Op.LONG_TO_DOUBLE || stmtNode.op == Op.DOUBLE_TO_INT || stmtNode.op == Op.INT_TO_FLOAT || stmtNode.op == Op.INT_TO_CHAR || stmtNode.op == Op.INT_TO_SHORT) {
                Stmt2RNode castNode = (Stmt2RNode) stmtNode;

                RegisterDependencyNode newRegister = makeNewRegister(castNode.a);
                makeDependency(newRegister, getActiveRegister(castNode.b));
                statementToRegister[currentStmtIndex] = newRegister;
            }
            else if(stmtNode.op == Op.MOVE || stmtNode.op == Op.MOVE_OBJECT || stmtNode.op == Op.MOVE_OBJECT_FROM16 || stmtNode.op == Op.MOVE_WIDE || stmtNode.op == Op.MOVE_WIDE_FROM16 || stmtNode.op == Op.MOVE_FROM16) {
                Stmt2RNode castNode = (Stmt2RNode) stmtNode;
                makeDependency(getActiveRegister(castNode.a), getActiveRegister(castNode.b));
                statementToRegister[currentStmtIndex] = getActiveRegister(castNode.a);
            }
            else if(stmtNode.op == Op.SGET || stmtNode.op == Op.SGET_BOOLEAN || stmtNode.op == Op.SGET_OBJECT) {
                // we are getting a field which is stored in a new register
                FieldStmtNode fieldStmtNode = (FieldStmtNode) stmtNode;
                RegisterDependencyNode newRegister = makeNewRegister(fieldStmtNode.a);
                statementToRegister[currentStmtIndex] = newRegister;
            }
            else if(stmtNode.op == Op.IGET_OBJECT || stmtNode.op == Op.IGET || stmtNode.op == Op.IGET_BOOLEAN || stmtNode.op == Op.IGET_WIDE) {
                FieldStmtNode fieldStmtNode = (FieldStmtNode) stmtNode;
                RegisterDependencyNode newRegister = makeNewRegister(fieldStmtNode.a);
                statementToRegister[currentStmtIndex] = newRegister;
            }
            else if(stmtNode.op == Op.NEW_INSTANCE) {
                TypeStmtNode typeStmtNode = (TypeStmtNode) stmtNode;
                RegisterDependencyNode newRegister = makeNewRegister(typeStmtNode.a);
                statementToRegister[currentStmtIndex] = newRegister;
            }
            else if(stmtNode.op == Op.NEW_ARRAY) {
                TypeStmtNode typeStmtNode = (TypeStmtNode) stmtNode;
                RegisterDependencyNode dependencyRegister = getActiveRegister(typeStmtNode.b);
                RegisterDependencyNode newRegister = makeNewRegister(typeStmtNode.a);
                makeDependency(newRegister, dependencyRegister);
                statementToRegister[currentStmtIndex] = newRegister;
            }
            else if(stmtNode.op == Op.APUT || stmtNode.op == Op.APUT_OBJECT || stmtNode.op == Op.APUT_CHAR) {
                Stmt3RNode castNode = (Stmt3RNode) stmtNode;
                makeDependency(getActiveRegister(castNode.b), getActiveRegister(castNode.a));
                statementToRegister[currentStmtIndex] = getActiveRegister(castNode.b);
            }
            else if(stmtNode.op == Op.AGET_WIDE || stmtNode.op == Op.AGET || stmtNode.op == Op.AGET_BOOLEAN || stmtNode.op == Op.AGET_OBJECT || stmtNode.op == Op.AGET_BYTE) {
                Stmt3RNode castNode = (Stmt3RNode) stmtNode;
                makeDependency(getActiveRegister(castNode.a), getActiveRegister(castNode.b));
                statementToRegister[currentStmtIndex] = getActiveRegister(castNode.a);
            }
            else if(stmtNode.op == Op.SPUT || stmtNode.op == Op.SPUT_OBJECT || stmtNode.op == Op.SPUT_WIDE || stmtNode.op == Op.SPUT_BOOLEAN) {
                FieldStmtNode fieldStmtNode = (FieldStmtNode) stmtNode;
                statementToRegister[currentStmtIndex] = getActiveRegister(fieldStmtNode.a);
            }
            else if(stmtNode.op == Op.ARRAY_LENGTH) {
                Stmt2RNode castNode = (Stmt2RNode) stmtNode;
                RegisterDependencyNode newRegister = makeNewRegister(castNode.a);
                makeDependency(newRegister, getActiveRegister(castNode.b));
                statementToRegister[currentStmtIndex] = newRegister;
            }
            else if(stmtNode.op == Op.SUB_INT || stmtNode.op == Op.DIV_DOUBLE || stmtNode.op == Op.MUL_INT || stmtNode.op == Op.SUB_LONG || stmtNode.op == Op.MUL_DOUBLE || stmtNode.op == Op.ADD_INT || stmtNode.op == Op.OR_INT) {
                Stmt3RNode castStmtNode = (Stmt3RNode) stmtNode;
                // TODO new register!!!!!
                makeDependency(getActiveRegister(castStmtNode.a), getActiveRegister(castStmtNode.b));
                makeDependency(getActiveRegister(castStmtNode.a), getActiveRegister(castStmtNode.c));
                statementToRegister[currentStmtIndex] = getActiveRegister(castStmtNode.a);
            }
            else if(stmtNode.op == Op.ADD_INT_2ADDR || stmtNode.op == Op.MUL_INT_2ADDR || stmtNode.op == Op.REM_INT_2ADDR || stmtNode.op == Op.SHL_INT_2ADDR || stmtNode.op == Op.AND_INT_2ADDR || stmtNode.op == Op.SUB_LONG_2ADDR || stmtNode.op == Op.ADD_LONG_2ADDR || stmtNode.op == Op.DIV_DOUBLE_2ADDR || stmtNode.op == Op.DIV_FLOAT_2ADDR || stmtNode.op == Op.DIV_INT_2ADDR || stmtNode.op == Op.SHR_INT_2ADDR || stmtNode.op == Op.OR_INT_2ADDR || stmtNode.op == Op.XOR_INT_2ADDR) {
                Stmt2RNode castStmtNode = (Stmt2RNode) stmtNode;
                makeDependency(getActiveRegister(castStmtNode.a), getActiveRegister(castStmtNode.b));
                statementToRegister[currentStmtIndex] = getActiveRegister(castStmtNode.a);
            }
            else if(stmtNode.op == Op.XOR_INT_LIT8 || stmtNode.op == Op.ADD_INT_LIT8 || stmtNode.op == Op.MUL_INT_LIT16 || stmtNode.op == Op.SHL_INT_LIT8 || stmtNode.op == Op.MUL_INT_LIT8 || stmtNode.op == Op.AND_INT_LIT16 || stmtNode.op == Op.DIV_INT_LIT8 || stmtNode.op == Op.ADD_INT_LIT16 || stmtNode.op == Op.SHR_INT_LIT8 || stmtNode.op == Op.AND_INT_LIT8 || stmtNode.op == Op.RSUB_INT_LIT8) {
                Stmt2R1NNode castStmtNode = (Stmt2R1NNode) stmtNode;
                makeDependency(getActiveRegister(castStmtNode.distReg), getActiveRegister(castStmtNode.srcReg));
                statementToRegister[currentStmtIndex] = getActiveRegister(castStmtNode.distReg);
            }
            else if(stmtNode.op == Op.IPUT_OBJECT || stmtNode.op == Op.IPUT_BOOLEAN || stmtNode.op == Op.IPUT || stmtNode.op == Op.IPUT_WIDE) {
                // TODO ignore iput for now!
            }
            else if(stmtNode.op == Op.MONITOR_ENTER || stmtNode.op == Op.MONITOR_EXIT) {
                // TODO ignore monitors for now!
            }
            else if(stmtNode.op == Op.THROW) {
                // TODO ignore throw!
            }
            else if(stmtNode.op == Op.CHECK_CAST) {
                // TODO ignore check-cast for now!
            }
            else if(stmtNode.op == Op.RETURN || stmtNode.op == Op.RETURN_OBJECT) {
                // TODO ignore returns for now!
            }
            else if(stmtNode instanceof DexLabelStmtNode) {
                // TODO ignore labels for now!
            }
            else if(stmtNode instanceof SparseSwitchStmtNode) {
                // TODO ignore sparse switch for now!
            }
            else if(stmtNode instanceof PackedSwitchStmtNode) {
                // TODO ignore switches for now!
            }
            else if(stmtNode instanceof JumpStmtNode) {
                // TODO ignore jumps for now!
            }
            else {
                throw new RuntimeException("Unknown statement type when building dependency graph! " + stmtNode.toString() + ", " + stmtNode.op);
            }

            // are we done?
            if(currentStmtIndex == methodExecutionPath.stringDecryptIndex) {
                break;
            }
            else if(currentJumpIndex == methodExecutionPath.path.size() && currentStmtIndex == currentSection.endIndex - 1) {
                break;
            }

            if(currentJumpIndex < methodExecutionPath.path.size()) {
                // are we at a point where we should jump?
                MethodSectionJump nextJump = methodExecutionPath.path.get(currentJumpIndex);
                if(currentStmtIndex == nextJump.jumpStmtIndex) {
                    // take it!
                    currentStmtIndex = nextJump.toSection.beginIndex;
                    currentSection = nextJump.toSection;
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
