package main;

import com.googlecode.d2j.Field;
import com.googlecode.d2j.Method;
import com.googlecode.d2j.dex.Dex2jar;
import com.googlecode.d2j.dex.writer.DexFileWriter;
import com.googlecode.d2j.node.DexClassNode;
import com.googlecode.d2j.node.DexCodeNode;
import com.googlecode.d2j.node.DexMethodNode;
import com.googlecode.d2j.node.insn.*;
import com.googlecode.d2j.reader.BaseDexFileReader;
import com.googlecode.d2j.reader.MultiDexFileReader;
import com.googlecode.d2j.reader.Op;
import com.googlecode.d2j.smali.BaksmaliDumper;
import com.googlecode.d2j.smali.Smali;
import com.googlecode.d2j.visitors.DexFileVisitor;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.util.ArrayList;

import static com.googlecode.d2j.DexConstants.ACC_PUBLIC;
import static com.googlecode.d2j.DexConstants.ACC_STATIC;

public class StringDecryptor {

    public static void decrypt(StringSnippet snippet) throws IOException {
        System.out.println("Original string: " + snippet.getString() + " (l: " + snippet.getString().length() + ", " + snippet.file.getPath() + ")");
        String[] paramTypes = {"[Ljava/lang/String;"};
        Method m = new Method("LIsolated", "main", paramTypes, "V");
        DexMethodNode mn = new DexMethodNode(ACC_PUBLIC | ACC_STATIC, m);
        DexCodeNode cn = new DexCodeNode();
        cn.stmts = snippet.extractedStatements;
        mn.codeNode = cn;

        // account for buffer overflow
        DexStmtNode lastStmtNode = cn.stmts.get(cn.stmts.size() - 1);
        if(lastStmtNode.op == Op.MOVE_RESULT_OBJECT) {
            Stmt1RNode newMoveNode = new Stmt1RNode(Op.MOVE_RESULT_OBJECT, 1);
            cn.stmts.remove(cn.stmts.size() - 1);
            cn.stmts.add(newMoveNode);
            snippet.stringResultRegister = 1;
        }

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
        cn.visitRegister(30);

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
        DexFileWriter dexFileWriter = new DexFileWriter();
        DexFileVisitor dexFileVisitor = new DexFileVisitor(dexFileWriter) {
            public void visitEnd() {
            }
        };
        File smaliFile = new File("temp/isolated.smali");
        Smali.smali(smaliFile.toPath(), dexFileVisitor);
        dexFileWriter.visitEnd();
        byte[] data = dexFileWriter.toByteArray();
        File dexFile = new File("temp/isolated.dex");
        Files.write(dexFile.toPath(), data, new OpenOption[0]);

        // convert to .jar
        File jarFile = new File("temp/isolated.jar");
        BaseDexFileReader dexReader = MultiDexFileReader.open(Files.readAllBytes(dexFile.toPath()));
        Dex2jar.from(dexReader).reUseReg(false).topoLogicalSort().skipDebug(true).optimizeSynchronized(false).printIR(false).noCode(false).skipExceptions(false).to(jarFile.toPath());

        // run the jar
        Process p = Runtime.getRuntime().exec("java -cp /Users/martijndevos/Documents/lloyds_original.jar:temp/isolated.jar:temp Isolated 2>/dev/null");
        String line = null;
        String finalString = "";
        try {
            int result = p.waitFor();
//            System.out.println("Process exit code: " + result);
//            System.out.println();
//            System.out.println("Result:");
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            while ((line = reader.readLine()) != null) {
//                System.out.println(line);
                finalString += line;
            }

//            line = null;
//            reader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
//            while ((line = reader.readLine()) != null) {
//                System.out.println(line);
//            }

            if(result == 0 && finalString.length() > 0) {
                snippet.decryptedString = finalString;
                snippet.decryptionSuccessful = true;
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
