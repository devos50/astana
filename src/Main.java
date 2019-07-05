import com.googlecode.d2j.Field;
import com.googlecode.d2j.Method;
import com.googlecode.d2j.node.DexClassNode;
import com.googlecode.d2j.node.DexCodeNode;
import com.googlecode.d2j.node.DexMethodNode;
import com.googlecode.d2j.node.insn.*;
import com.googlecode.d2j.reader.Op;
import com.googlecode.d2j.smali.BaksmaliDumper;
import com.googlecode.d2j.smali.Smali;
import com.googlecode.d2j.smali.SmaliCmd;
import com.googlecode.dex2jar.tools.Dex2jarCmd;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;

import static com.googlecode.d2j.DexConstants.ACC_PUBLIC;
import static com.googlecode.d2j.DexConstants.ACC_STATIC;

public class Main {
    private static String currentString = null;
    private static int stringResultRegister = 0;
    private static ArrayList<DexStmtNode> statements = new ArrayList<>();

    private static void constructClass() throws IOException {
        String[] paramTypes = {"[Ljava/lang/String;"};
        Method m = new Method("LIsolated", "main", paramTypes, "V");
        DexMethodNode mn = new DexMethodNode(ACC_PUBLIC | ACC_STATIC, m);
        DexCodeNode cn = new DexCodeNode();
        cn.stmts = statements;
        mn.codeNode = cn;

        // add the print statement and return-void
        Field f = new Field("Ljava/lang/System;", "out", "Ljava/io/PrintStream;");
        int printReg = (stringResultRegister == 0) ? 1 : 0;
        FieldStmtNode fsn = new FieldStmtNode(Op.SGET_OBJECT, printReg, 0, f);
        cn.stmts.add(fsn);

        paramTypes = new String[]{"Ljava/lang/String;"};
        Method printMethod = new Method("Ljava/io/PrintStream;", "println", paramTypes, "V");
        int[] args = {printReg, stringResultRegister};
        MethodStmtNode msn = new MethodStmtNode(Op.INVOKE_VIRTUAL, args, printMethod);
        cn.stmts.add(msn);

        Stmt0RNode returnVoidStmt = new Stmt0RNode(Op.RETURN_VOID);
        cn.stmts.add(returnVoidStmt);
        cn.visitRegister(20);

        // create the class
        DexClassNode classNode = new DexClassNode(ACC_PUBLIC, "LIsolated;", "Ljava/lang/Object;", null);
        classNode.methods = new ArrayList<>();
        classNode.methods.add(mn);

        // convert to smali
        BaksmaliDumper dumper = new BaksmaliDumper(false, true);
        FileWriter fw = new FileWriter("temp/isolated.smali");
        BufferedWriter log = new BufferedWriter(fw);
        dumper.baksmaliClass(classNode, log);
        try {
            log.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // convert to .dex
        new SmaliCmd().doMain("temp/Isolated.smali", "-o", "temp/isolated.dex");

        // convert to .jar
        new Dex2jarCmd().doMain("temp/isolated.dex", "--force", "-o", "temp/isolated.jar");

        // run the jar
        Process p = Runtime.getRuntime().exec("java -cp /Users/martijndevos/Downloads/lloyds_original.jar:temp/isolated.jar:temp Isolated");
        try {
            int result = p.waitFor();
            System.out.println("Process exit code: " + result);
            System.out.println();
            System.out.println("Result:");
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = "";
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            reader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        currentString = null;
        statements = new ArrayList<>();

        // TODO: exit for now
        //System.exit(0);
    }

    public static void main(String[] args) throws IOException {
        InputStream input = new FileInputStream("data/iwwiiw.smali");
        DexClassNode cn = Smali.smaliFile2Node("test.smali", input);
        for (Iterator<DexMethodNode> it = cn.methods.iterator(); it.hasNext(); ) {
            DexMethodNode m = it.next();
            System.out.println("Method: " + m.method.getName());
            for(int stmtIndex = 0; stmtIndex < m.codeNode.stmts.size(); stmtIndex++ ) {
                DexStmtNode stmtNode = m.codeNode.stmts.get(stmtIndex);
                if(currentString != null) { statements.add(stmtNode); }

                if(stmtNode.op == Op.CONST_STRING) {
                    if(currentString != null) {
                        throw new RuntimeException("Already parsing string!");
                    }

                    ConstStmtNode cnn = (ConstStmtNode) stmtNode;
                    String stringValue = cnn.value.toString();
                    if(stringValue.length() > 0) {
                        currentString = stringValue;
                        statements.add(stmtNode);
                    }
                }

                if(stmtNode.op == Op.INVOKE_DIRECT && currentString != null) {
                    MethodStmtNode mnn = (MethodStmtNode) stmtNode;
                    stringResultRegister = mnn.args[0]; // first argument is always the string itself
                    if(mnn.method.getName().equals("<init>") && mnn.method.getOwner().equals("Ljava/lang/String;")) {
                        constructClass();
                    }
                }
                else if(stmtNode.op == Op.INVOKE_STATIC && currentString != null) {
                    MethodStmtNode mnn = (MethodStmtNode) stmtNode;
                    if(mnn.method.getReturnType().equals("Ljava/lang/String;")) {
                        // we want to move this result to a register
                        Stmt1RNode moveStmtNode = new Stmt1RNode(Op.MOVE_RESULT_OBJECT, 1);
                        statements.add(moveStmtNode);
                        stringResultRegister = 1;

                        constructClass();
                    }

                }
            }
        }
    }
}
