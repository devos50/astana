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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static com.googlecode.d2j.DexConstants.*;

public class StringDecryptor {

    private final StringSnippet snippet;
    private final StringDatabase database;
    public int resultExecutionCode = -1;
    public String stderr = "";
    public String stdout = "";

    public StringDecryptor(StringSnippet snippet, StringDatabase database) {
        this.snippet = snippet;
        this.database = database;
    }

    public boolean decrypt() throws IOException, SQLException {
        String methodName = snippet.method.methodNode.method.getName();

        // create a method that is called by the main method
        Method m = new Method(snippet.method.methodNode.method.getOwner(), methodName, new String[]{}, "V");
        int flags = ACC_PUBLIC | ACC_STATIC;
        if(methodName.equals("<clinit>")) {
            flags |= ACC_CONSTRUCTOR;
        }
        else if(methodName.equals("<init>")) {
            flags = ACC_PUBLIC | ACC_CONSTRUCTOR;
        }
        DexMethodNode mn = new DexMethodNode(flags, m);
        DexCodeNode cn = new DexCodeNode();
        cn.stmts = snippet.extractedStatements;
        mn.codeNode = cn;

        // account for buffer overflow
        DexStmtNode lastStmtNode = cn.stmts.get(cn.stmts.size() - 1);
        if(lastStmtNode.op == Op.MOVE_RESULT_OBJECT) {
            Stmt1RNode newMoveNode = new Stmt1RNode(Op.MOVE_RESULT_OBJECT, 1);
            cn.stmts.remove(cn.stmts.size() - 1);
            cn.stmts.add(newMoveNode);
            snippet.currentStringResultRegister = 1;
        }

        // add the print statement and return-void
        Field f = new Field("Ljava/lang/System;", "out", "Ljava/io/PrintStream;");
        int printReg = (snippet.currentStringResultRegister == 0) ? 1 : 0;
        FieldStmtNode fsn = new FieldStmtNode(Op.SGET_OBJECT, printReg, 0, f);
        cn.stmts.add(fsn);

        String[] paramTypes = new String[]{"Ljava/lang/String;"};
        Method printMethod = new Method("Ljava/io/PrintStream;", "println", paramTypes, "V");
        int[] args = {printReg, snippet.currentStringResultRegister};
        MethodStmtNode msn = new MethodStmtNode(Op.INVOKE_VIRTUAL, args, printMethod);
        cn.stmts.add(msn);

        Stmt0RNode returnVoidStmt = new Stmt0RNode(Op.RETURN_VOID);
        cn.stmts.add(returnVoidStmt);
        cn.visitRegister(30);

        // create the main method that calls the method we created
        paramTypes = new String[]{"[Ljava/lang/String;"};
        Method mainMethod = new Method(snippet.method.methodNode.method.getOwner(), "main", paramTypes, "V");
        DexMethodNode mainMethodNode = new DexMethodNode(ACC_PUBLIC | ACC_STATIC, mainMethod);
        DexCodeNode mainCodeNode = new DexCodeNode();
        mainMethodNode.codeNode = mainCodeNode;

        if(methodName.equals("<init>")) {
            // create a new instance and call the init
            TypeStmtNode newInstanceNode = new TypeStmtNode(Op.NEW_INSTANCE, 0, 0, m.getOwner());
            mainCodeNode.stmts.add(newInstanceNode);
            MethodStmtNode initCall = new MethodStmtNode(Op.INVOKE_DIRECT, new int[]{0}, m);
            mainCodeNode.stmts.add(initCall);
        }
        else if(!methodName.equals("<clinit>")) {
            // call the method from the main method
            MethodStmtNode mainMethodCall = new MethodStmtNode(Op.INVOKE_STATIC, new int[]{}, m);
            mainCodeNode.stmts.add(mainMethodCall);
        }
        returnVoidStmt = new Stmt0RNode(Op.RETURN_VOID);
        mainCodeNode.stmts.add(returnVoidStmt);
        mainCodeNode.visitRegister(30);

        // create the class
        DexClassNode classNode = new DexClassNode(ACC_PUBLIC, snippet.method.methodNode.method.getOwner(), "Ljava/lang/Object;", null);
        classNode.fields = snippet.rootNode.fields;
        classNode.methods = new ArrayList<>();
        classNode.methods.add(mn);
        classNode.methods.add(mainMethodNode);

        // convert to smali
        BaksmaliDumper dumper = new BaksmaliDumper(false, true);
        FileWriter fw = new FileWriter("temp/isolated.smali");
        BufferedWriter log = new BufferedWriter(fw);
        dumper.baksmaliClass(classNode, log);
        try {
            log.flush();
        } catch (IOException e) {
            e.printStackTrace();
            resultExecutionCode = 1;
            stderr = e.getMessage();
            return false;
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
        try {
            Dex2jar.from(dexReader).reUseReg(false).topoLogicalSort().skipDebug(true).optimizeSynchronized(false).printIR(false).noCode(false).skipExceptions(false).to(jarFile.toPath());
        }
        catch(RuntimeException e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            resultExecutionCode = 1;
            stderr = sw.toString();
            return false;
        }

        // run the jar
        String className = snippet.method.methodNode.method.getOwner();
        className = className.substring(1, className.length() - 1).replace("/", ".");
        String command = "java -noverify -cp data/AndroidStubs.jar:temp/isolated.jar:data/" + snippet.apkPath + ".jar " + className + " 2>/dev/null";
        Process p = Runtime.getRuntime().exec(command);
        String line = null;
        try {
            p.waitFor(5, TimeUnit.SECONDS);
            resultExecutionCode = p.exitValue();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            while ((line = reader.readLine()) != null) {
                stdout += line;
            }

            reader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            while ((line = reader.readLine()) != null) {
                stderr += line;
                System.out.println(line);
            }

            if(resultExecutionCode == 0 && stdout.length() > 0) {
                System.out.println("RESULT: " + stdout);
                return true;
            }
        } catch (InterruptedException | IllegalThreadStateException e) {
            System.out.println("Caught exception: ");
            stderr = e.getMessage();
            resultExecutionCode = 1;
            e.printStackTrace();
            return false;
        }
        return false;
    }
}
