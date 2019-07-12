import com.googlecode.d2j.Field;
import com.googlecode.d2j.Method;
import com.googlecode.d2j.node.DexClassNode;
import com.googlecode.d2j.node.DexCodeNode;
import com.googlecode.d2j.node.DexMethodNode;
import com.googlecode.d2j.node.insn.FieldStmtNode;
import com.googlecode.d2j.node.insn.MethodStmtNode;
import com.googlecode.d2j.node.insn.Stmt0RNode;
import com.googlecode.d2j.reader.Op;
import com.googlecode.d2j.smali.BaksmaliDumper;
import com.googlecode.d2j.smali.SmaliCmd;
import com.googlecode.dex2jar.tools.Dex2jarCmd;

import java.io.*;
import java.util.ArrayList;

import static com.googlecode.d2j.DexConstants.ACC_PUBLIC;
import static com.googlecode.d2j.DexConstants.ACC_STATIC;

public class StringDecryptor {

    public static String decrypt(StringSnippet snippet) throws IOException {
        String[] paramTypes = {"[Ljava/lang/String;"};
        Method m = new Method("LIsolated", "main", paramTypes, "V");
        DexMethodNode mn = new DexMethodNode(ACC_PUBLIC | ACC_STATIC, m);
        DexCodeNode cn = new DexCodeNode();
        cn.stmts = snippet.statements;
        mn.codeNode = cn;

        // add the print statement and return-void
        Field f = new Field("Ljava/lang/System;", "out", "Ljava/io/PrintStream;");
        int printReg = (snippet.stringResultRegister == 0) ? 1 : 0;
        FieldStmtNode fsn = new FieldStmtNode(Op.SGET_OBJECT, printReg, 0, f);
        cn.stmts.add(fsn);

        paramTypes = new String[]{"Ljava/lang/String;"};
        Method printMethod = new Method("Ljava/io/PrintStream;", "println", paramTypes, "V");
        int[] args = {printReg, snippet.stringResultRegister};
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
        Process p = Runtime.getRuntime().exec("java -cp /Users/martijndevos/Documents/lloyds_original.jar:temp/isolated.jar:temp Isolated");
        String line = null;
        String finalString = "";
        try {
            int result = p.waitFor();
            System.out.println("Original string: " + snippet.getString());
            System.out.println("Process exit code: " + result);
            System.out.println();
            System.out.println("Result:");
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                finalString += line;
            }

            if(finalString.length() == 0) { System.exit(1); }

            line = null;
            reader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            if(result != 0) {
                System.exit(1);
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return finalString;
    }
}
