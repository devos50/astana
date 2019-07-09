import com.googlecode.d2j.Field;
import com.googlecode.d2j.Method;
import com.googlecode.d2j.node.DexClassNode;
import com.googlecode.d2j.node.DexCodeNode;
import com.googlecode.d2j.node.DexMethodNode;
import com.googlecode.d2j.node.insn.*;
import com.googlecode.d2j.reader.Op;
import com.googlecode.d2j.smali.BaksmaliCmd;
import com.googlecode.d2j.smali.BaksmaliDumper;
import com.googlecode.d2j.smali.Smali;
import com.googlecode.d2j.smali.SmaliCmd;
import com.googlecode.dex2jar.tools.Dex2jarCmd;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import smile.clustering.HierarchicalClustering;
import smile.clustering.linkage.CompleteLinkage;
import smile.clustering.linkage.WardLinkage;

import javax.swing.*;
import java.io.*;
import java.util.*;

import static com.googlecode.d2j.DexConstants.ACC_PUBLIC;
import static com.googlecode.d2j.DexConstants.ACC_STATIC;

public class Main {
    private static String currentString = null;
    private static int stringResultRegister = 0;
    private static ArrayList<DexStmtNode> statements = new ArrayList<>();
    private static ArrayList<EncryptedStringDecryption> stringsCollection = new ArrayList<>();

    private static void constructClass(File currentFile) throws IOException {
        if(statements.size() > 2) {
            EncryptedStringDecryption dec = new EncryptedStringDecryption(currentFile, currentString, statements);
            stringsCollection.add(dec);
        }

        currentString = null;
        statements = new ArrayList<>();

//        String[] paramTypes = {"[Ljava/lang/String;"};
//        Method m = new Method("LIsolated", "main", paramTypes, "V");
//        DexMethodNode mn = new DexMethodNode(ACC_PUBLIC | ACC_STATIC, m);
//        DexCodeNode cn = new DexCodeNode();
//        cn.stmts = statements;
//        mn.codeNode = cn;
//
//        // add the print statement and return-void
//        Field f = new Field("Ljava/lang/System;", "out", "Ljava/io/PrintStream;");
//        int printReg = (stringResultRegister == 0) ? 1 : 0;
//        FieldStmtNode fsn = new FieldStmtNode(Op.SGET_OBJECT, printReg, 0, f);
//        cn.stmts.add(fsn);
//
//        paramTypes = new String[]{"Ljava/lang/String;"};
//        Method printMethod = new Method("Ljava/io/PrintStream;", "println", paramTypes, "V");
//        int[] args = {printReg, stringResultRegister};
//        MethodStmtNode msn = new MethodStmtNode(Op.INVOKE_VIRTUAL, args, printMethod);
//        cn.stmts.add(msn);
//
//        Stmt0RNode returnVoidStmt = new Stmt0RNode(Op.RETURN_VOID);
//        cn.stmts.add(returnVoidStmt);
//        cn.visitRegister(20);
//
//        // create the class
//        DexClassNode classNode = new DexClassNode(ACC_PUBLIC, "LIsolated;", "Ljava/lang/Object;", null);
//        classNode.methods = new ArrayList<>();
//        classNode.methods.add(mn);
//
//        // convert to smali
//        BaksmaliDumper dumper = new BaksmaliDumper(false, true);
//        FileWriter fw = new FileWriter("temp/isolated.smali");
//        BufferedWriter log = new BufferedWriter(fw);
//        dumper.baksmaliClass(classNode, log);
//        try {
//            log.flush();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//        // convert to .dex
//        new SmaliCmd().doMain("temp/Isolated.smali", "-o", "temp/isolated.dex");
//
//        // convert to .jar
//        new Dex2jarCmd().doMain("temp/isolated.dex", "--force", "-o", "temp/isolated.jar");
//
//        // run the jar
//        Process p = Runtime.getRuntime().exec("java -cp /Users/martijndevos/Documents/barclays_original.jar:temp/isolated.jar:temp Isolated");
//        try {
//            int result = p.waitFor();
//            System.out.println("Process exit code: " + result);
//            System.out.println();
//            System.out.println("Result:");
//            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
//            String line = "";
//            while ((line = reader.readLine()) != null) {
//                System.out.println(line);
//            }
//
//            reader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
//            while ((line = reader.readLine()) != null) {
//                System.out.println(line);
//            }
//
//            if(result != 0) {
//                System.exit(1);
//            }
//
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }

//        currentString = null;
//        statements = new ArrayList<>();
    }

    private static void processSmaliFiles(File smaliFile) throws IOException {
        //System.out.println("Processing file: " + smaliFile.getPath());
        InputStream input = new FileInputStream(smaliFile);
        DexClassNode cn = null;
        try {
            cn = Smali.smaliFile2Node("test.smali", input);
        } catch (Exception e) {
            System.out.println("Could not parse file " + smaliFile.getPath());
            return;
        }

        if(cn.methods == null) {
            return;
        }

        for (Iterator<DexMethodNode> it = cn.methods.iterator(); it.hasNext(); ) {
            DexMethodNode m = it.next();
            //System.out.println("Method: " + m.method.getName());
            for(int stmtIndex = 0; stmtIndex < m.codeNode.stmts.size(); stmtIndex++ ) {
                DexStmtNode stmtNode = m.codeNode.stmts.get(stmtIndex);
                if(currentString != null && stmtNode.op != Op.CONST_STRING) { statements.add(stmtNode); }

                if(stmtNode.op == Op.CONST_STRING) {
                    ConstStmtNode cnn = (ConstStmtNode) stmtNode;
                    String stringValue = cnn.value.toString();

                    if(currentString != null) {
                        //throw new RuntimeException("Already parsing string!");

                        // for now, start a new sequence
                        Stmt1RNode moveStmtNode = new Stmt1RNode(Op.MOVE_RESULT_OBJECT, 1);
                        statements.add(moveStmtNode);
                        stringResultRegister = 1;
                        constructClass(smaliFile);

                        statements.add(stmtNode);
                        currentString = cnn.value.toString();
                        continue;
                    }

                    if(stringValue.length() > 0) {
                        currentString = stringValue;
                        statements.add(stmtNode);
                    }
                }

                if(stmtNode.op == Op.INVOKE_DIRECT && currentString != null) {
                    MethodStmtNode mnn = (MethodStmtNode) stmtNode;
                    stringResultRegister = mnn.args[0]; // first argument is always the string itself
                    if(mnn.method.getName().equals("<init>") && mnn.method.getOwner().equals("Ljava/lang/String;")) {
                        constructClass(smaliFile);
                    }
                }
                else if(stmtNode.op == Op.INVOKE_STATIC && currentString != null) {
                    MethodStmtNode mnn = (MethodStmtNode) stmtNode;
                    if(mnn.method.getReturnType().equals("Ljava/lang/String;")) {
                        // we want to move this result to a register
                        Stmt1RNode moveStmtNode = new Stmt1RNode(Op.MOVE_RESULT_OBJECT, 1);
                        statements.add(moveStmtNode);
                        stringResultRegister = 1;
                        constructClass(smaliFile);
                    }

                }
            }

            if(currentString != null) {
                // method ended while doing stuff with a string, finish properly
                Stmt1RNode moveStmtNode = new Stmt1RNode(Op.MOVE_RESULT_OBJECT, 1);
                statements.add(moveStmtNode);
                stringResultRegister = 1;
                constructClass(smaliFile);
            }
        }

        currentString = null;
        statements = new ArrayList<>();
    }

    public static double cosineSimilarity(double[] vectorA, double[] vectorB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public static void main(String[] args) throws IOException {
//        File jarFile = new File("data/barclays.jar");
//        if(!jarFile.exists()) {
//            // convert APK to .jar in order to run smali code
//            new Dex2jarCmd().doMain("data/barclays.apk", "-o", "data/barclays.jar");
//        }
//
//        File smaliFilesPath = new File("data/barclays-smali");
//        if(!smaliFilesPath.exists()) {
//            // convert APK to smali files
//            new BaksmaliCmd().doMain("data/barclays.apk", "-o", "data/barclays-smali");
//        }

        File analyzePath = new File("data/lloyds-smali");
        List<File> files = (List<File>) FileUtils.listFiles(analyzePath, new String[] { "smali" }, true);
        System.out.println("Number of smali files: " + files.size());

        for(File smaliFile : files) {
            processSmaliFiles(smaliFile);
        }

        //processSmaliFiles(new File("data/lloyds-smali/android/support/v4/media/session/MediaSessionCompat$MediaSessionImplApi18.smali"));

        System.out.println("Starting to compute distance matrix of " + stringsCollection.size() + " items!");

        Collections.shuffle(stringsCollection, new Random(1));

        // compute distance matrix
        int MAX_ITEMS = Math.min(stringsCollection.size(), 6000);

        double[][] distances = new double[MAX_ITEMS][MAX_ITEMS];
        for(int i = 0; i < MAX_ITEMS; i++) {
            for(int j = 0; j < i; j++) {
                double dist = 1 - cosineSimilarity(stringsCollection.get(i).nGramVector, stringsCollection.get(j).nGramVector);
                //double dist = LevenshteinDistance.levenshteinDistance(stringsCollection.get(i).stringStatements, stringsCollection.get(j).stringStatements);
                distances[i][j] = dist;
                distances[j][i] = dist;
            }

            if(i % 1000 == 0) { System.out.println(i); }
        }

        // write distance matrix
//        BufferedWriter br = new BufferedWriter(new FileWriter("distances.csv"));
//        StringBuilder sb = new StringBuilder();
//        for (double[] arr : distances) {
//            for(int i = 0; i < arr.length; i++) {
//                sb.append(arr[i]);
//                if(i != arr.length - 1) {
//                    sb.append(",");
//                }
//            }
//            sb.append("\n");
//        }
//        br.write(sb.toString());
//        br.close();

        String[] names = new String[MAX_ITEMS];
        for(int i = 0; i < MAX_ITEMS; i++) {
            names[i] = "" + i;
        }

        System.out.println("Clustering...");
        HierarchicalClustering hac = new HierarchicalClustering(new WardLinkage(distances));
        int[] membership = hac.partition(9);
        for(int cluster = 0; cluster < 1; cluster++) {
            ArrayList<Integer> belongsTo = new ArrayList<>();
            for(int i = 0; i < membership.length; i++) {
                if(membership[i] == cluster) { belongsTo.add(i); }
            }
            System.out.println("Members in cluster " + cluster + ": " + belongsTo.size());

            int encrypted = 0;
            int decrypted = 0;
            for(int i = 0; i < belongsTo.size(); i++) {
                EncryptedStringDecryption item = stringsCollection.get(belongsTo.get(i));
                if(item.file.getPath().contains("data/lloyds-smali/iiiiii") || item.file.getPath().contains("data/barclays-smali/p") || item.file.getPath().contains("data/barclays-smali/com/barclays")) { encrypted++; }
                else { decrypted++; }
                System.out.println("C " + cluster + ", file: " + item.file.getPath() + ", item: " + belongsTo.get(i) + ", str: " + item.encryptedString);
                System.out.println(item.stringStatements);
            }
            System.out.println("encrypted: " + encrypted + ", decrypted: " + decrypted);
        }

        double totalDistance = 0;
        for(int i = 0; i < MAX_ITEMS; i++) {
            for(int j = 0; j < MAX_ITEMS; j++) {
                totalDistance += distances[i][j];
            }
        }

//        for(int k = 2; k <= 50; k++) {
//            int[] membership = hac.partition(k);
//            //System.out.println("k: " + k);
//            //System.out.println(Arrays.toString(membership));
//            // for each cluster, get the total distance
//            double res = 0;
//
//            for(int cluster = 0; cluster < k; cluster++) {
//                double clusterDistance = 0;
//                ArrayList<Integer> belongsTo = new ArrayList<>();
//                for(int i = 0; i < membership.length; i++) {
//                    if(membership[i] == cluster) { belongsTo.add(i); }
//                }
//
//                for(int i = 0; i < belongsTo.size(); i++) {
//                    for(int j = 0; j < belongsTo.size(); j++) {
//                        if(i == j) { continue; }
//                        clusterDistance += distances[belongsTo.get(i)][belongsTo.get(j)];
//                    }
//                }
//                res += clusterDistance;
//                System.out.println("Members in cluster: " + belongsTo.size());
//            }
//            res /= totalDistance;
//            System.out.println("monzo," + k + "," + res);
//        }
    }
}
