import com.googlecode.d2j.node.insn.*;
import com.googlecode.d2j.reader.Op;

import java.util.*;

public class RegisterDependencyGraph {

    private final StringSnippet snippet;
    public Map<RegisterDependencyNode, List<RegisterDependencyNode>> adjacency = new HashMap<>();
    public Map<Integer, RegisterDependencyNode> activeRegister = new HashMap<>();
    public RegisterDependencyNode[] statementToRegister;

    public RegisterDependencyGraph(StringSnippet snippet) {
        this.snippet = snippet;
        this.statementToRegister = new RegisterDependencyNode[this.snippet.statements.size()];
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

    private void makeDependency(int a, int b) {
        // make a dependency from a to b
        RegisterDependencyNode argNode = activeRegister.get(b);
        if(argNode == null) {
            throw new RuntimeException("Arg node is null so variable is not correctly set!");
        }
        adjacency.get(activeRegister.get(a)).add(argNode);
    }

    private int getCorrespondingMethodNode(int stmtIndex) {
        for(int index = stmtIndex; index >= 0; index--) {
            DexStmtNode stmtNode = snippet.statements.get(index);
            if(stmtNode instanceof MethodStmtNode) {
                return index;
            }
        }
        return -1;
    }

    public void build() {
        System.out.println("Building register dependency graph for string " + snippet.getString());

        for(int statementIndex = 0; statementIndex < snippet.statements.size(); statementIndex++) {
            DexStmtNode stmtNode = snippet.statements.get(statementIndex);
            if(stmtNode instanceof ConstStmtNode) {
                // definition of a constant -> set new active register
                ConstStmtNode constStmtNode = (ConstStmtNode) stmtNode;
                makeNewRegister(constStmtNode.a);
            }
            else if(stmtNode instanceof MethodStmtNode) {
                // ignore for now, instead, process the move-result statement instead
            }
            else if(stmtNode.op == Op.MOVE_RESULT || stmtNode.op == Op.MOVE_RESULT_OBJECT) {
                // we store the result from a method call, create dependencies
                Stmt1RNode moveStmtNode = (Stmt1RNode) stmtNode;

                int prevMethodStmtIndex = getCorrespondingMethodNode(statementIndex);
                if(prevMethodStmtIndex == -1) {
                    throw new RuntimeException("Could not find corresponding method for move-result(-object)!");
                }
                MethodStmtNode prevMethodStmtNode = (MethodStmtNode) snippet.statements.get(prevMethodStmtIndex);
                int[] args = prevMethodStmtNode.args;

                // make a new register
                RegisterDependencyNode newRegister = makeNewRegister(moveStmtNode.a);
                statementToRegister[statementIndex] = newRegister;
                statementToRegister[prevMethodStmtIndex] = newRegister;

                // make dependencies
                if(args == null || args.length == 0) { continue; }
                for(int arg : args) {
                    makeDependency(moveStmtNode.a, arg);
                }
            }
            else if(stmtNode.op == Op.SGET) {
                // we are getting a field which is stored in a new register
                FieldStmtNode fieldStmtNode = (FieldStmtNode) stmtNode;
                RegisterDependencyNode newRegister = makeNewRegister(fieldStmtNode.a);
                statementToRegister[statementIndex] = newRegister;
            }
            else if(stmtNode.op == Op.SPUT) {
                FieldStmtNode fieldStmtNode = (FieldStmtNode) stmtNode;
                statementToRegister[statementIndex] = activeRegister.get(fieldStmtNode.a);
                continue;
            }
            else if(stmtNode.op == Op.ADD_INT_2ADDR || stmtNode.op == Op.MUL_INT_2ADDR || stmtNode.op == Op.REM_INT_2ADDR) {
                Stmt2RNode castStmtNode = (Stmt2RNode) stmtNode;
                makeDependency(castStmtNode.a, castStmtNode.b);
                statementToRegister[statementIndex] = activeRegister.get(castStmtNode.a);
            }
            else if(stmtNode.op == Op.RETURN) {
                continue;
            }
            else if(stmtNode instanceof DexLabelStmtNode) {
                continue;
            }
            else if(stmtNode instanceof PackedSwitchStmtNode) {
                // TODO ignore switches for now!
                continue;
            }
            else if(stmtNode instanceof JumpStmtNode) {
                // TODO ignore jumps for now!
                continue;
            }
            else {
                throw new RuntimeException("Unknown statement type when building dependency graph! " + stmtNode.toString());
            }
        }
    }

}
