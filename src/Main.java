import com.googlecode.d2j.node.DexClassNode;
import com.googlecode.d2j.node.DexMethodNode;
import com.googlecode.d2j.node.insn.ConstStmtNode;
import com.googlecode.d2j.node.insn.DexStmtNode;
import com.googlecode.d2j.node.insn.MethodStmtNode;
import com.googlecode.d2j.reader.Op;
import com.googlecode.d2j.smali.Smali;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;

public class Main {


    public static void main(String[] args) throws IOException {
        InputStream input = new FileInputStream("data/ApplicationController.smali");
        DexClassNode cn = Smali.smaliFile2Node("test.smali", input);
        for (Iterator<DexMethodNode> it = cn.methods.iterator(); it.hasNext(); ) {
            DexMethodNode m = it.next();
            System.out.println("Method: " + m.method.getName());
            String curString = null;
            ArrayList<DexStmtNode> statements = new ArrayList<DexStmtNode>();
            for(Iterator<DexStmtNode> it2 = m.codeNode.stmts.iterator(); it2.hasNext(); ) {
                DexStmtNode stmtNode = it2.next();
                if(stmtNode.op == Op.CONST_STRING) {
                    if(curString != null) {
                        throw new RuntimeException("Already parsing string!");
                    }

                    ConstStmtNode cnn = (ConstStmtNode) stmtNode;
                    String stringValue = cnn.value.toString();
                    if(stringValue.length() > 0) {
                        System.out.println("START");
                        curString = stringValue;
                        statements.add(stmtNode);
                    }
                }

                if(stmtNode.op == Op.INVOKE_DIRECT && curString != null) {
                    MethodStmtNode mnn = (MethodStmtNode) stmtNode;
                    if(mnn.method.getName().equals("<init>") && mnn.method.getOwner().equals("Ljava/lang/String;")) {
                        curString = null;
                        statements = new ArrayList<DexStmtNode>();
                        System.out.println("STOP");
                    }
                }
                else if(stmtNode.op == Op.INVOKE_STATIC && curString != null) {
                    MethodStmtNode mnn = (MethodStmtNode) stmtNode;
                    if(mnn.method.getReturnType().equals("Ljava/lang/String;")) {
                        curString = null;
                        statements = new ArrayList<DexStmtNode>();
                        System.out.println("STOP");
                    }

                }
            }
        }
    }
}
