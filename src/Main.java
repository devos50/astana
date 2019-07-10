import com.googlecode.d2j.node.insn.*;
import javafx.util.Pair;
import org.apache.commons.io.FileUtils;
import smile.clustering.HierarchicalClustering;
import smile.clustering.linkage.WardLinkage;

import java.io.*;
import java.util.*;

public class Main {
    private static ArrayList<StringSnippet> snippets = new ArrayList<>();

    private static void constructClass(File currentFile) throws IOException {
//        if(statements.size() > 2 && currentStringIsConsumed) {
//            StringSnippet dec = new StringSnippet(currentFile, currentString, statements);
//            stringsCollection.add(dec);
//        }

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
//        currentStringIsConsumed = false;
//        statements = new ArrayList<>();
    }

    public static double cosineSimilarity(HashMap<Pair<Integer, Integer>, Integer> mapA, HashMap<Pair<Integer, Integer>, Integer> mapB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        // we first need the dot product -> will only result if there is overlap
        Set<Pair<Integer, Integer>> keysA = mapA.keySet();
        Set<Pair<Integer, Integer>> keysB = mapB.keySet();
        Set<Pair<Integer, Integer>> intersection = new HashSet<>(keysA);
        intersection.retainAll(keysB);

        for(Pair<Integer, Integer> pair : intersection) {
            dotProduct += mapA.get(pair) * mapB.get(pair);
        }

        // compute normalization values
        Iterator it = mapA.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            normA += Math.pow((Integer)pair.getValue(), 2);
        }

        it = mapB.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            normB += Math.pow((Integer)pair.getValue(), 2);
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
            //System.out.println("Processing file " + smaliFile.getPath());
            SmaliFileParser parser = new SmaliFileParser(smaliFile);
            parser.process();
            snippets.addAll(parser.snippets);
        }

        System.out.println("Starting to compute distance matrix of " + snippets.size() + " items!");

        Collections.shuffle(snippets, new Random(1));

        // compute distance matrix
        int MAX_ITEMS = Math.min(snippets.size(), 6000);

        double[][] distances = new double[MAX_ITEMS][MAX_ITEMS];
        for(int i = 0; i < MAX_ITEMS; i++) {
            for(int j = 0; j < i; j++) {
                double dist = 1 - cosineSimilarity(snippets.get(i).frequencyMap, snippets.get(j).frequencyMap);
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
        int[] membership = hac.partition(10);
        for(int cluster = 0; cluster < 10; cluster++) {
            ArrayList<Integer> belongsTo = new ArrayList<>();
            for(int i = 0; i < membership.length; i++) {
                if(membership[i] == cluster) { belongsTo.add(i); }
            }
            System.out.println("Members in cluster " + cluster + ": " + belongsTo.size());

            int encrypted = 0;
            int decrypted = 0;
            for(int i = 0; i < belongsTo.size(); i++) {
                StringSnippet item = snippets.get(belongsTo.get(i));
                if(item.file.getPath().contains("data/lloyds-smali/iiiiii") || item.file.getPath().contains("data/barclays-smali/p") || item.file.getPath().contains("data/barclays-smali/com/barclays")) { encrypted++; }
                else { decrypted++; }
                System.out.println("C " + cluster + ", file: " + item.file.getPath() + ", str: " + item.getString());
                System.out.println("Item " + belongsTo.get(i) + ": " + item.stringStatements);
            }
            //System.out.println("encrypted: " + encrypted + ", decrypted: " + decrypted);

            // compute sum of squares within cluster

            // compute centroid
            double[] clusterCentroid = new double[255 * 255];
            for(int i = 0; i < belongsTo.size(); i++) {
                int index = belongsTo.get(i);
                HashMap<Pair<Integer, Integer>, Integer> frequencyMap = snippets.get(index).frequencyMap;

                for(Pair<Integer, Integer> key : frequencyMap.keySet()) {
                    int code = key.getKey() * 255 + key.getValue();
                    clusterCentroid[code] += frequencyMap.get(key);
                }
            }

            // normalize
            double withinDist = 0;
            for(int i = 0; i < 255 * 255; i++) {
                clusterCentroid[i] /= belongsTo.size();
            }

            for(int i = 0; i < belongsTo.size(); i++) {
                int index = belongsTo.get(i);
                double[] vector = snippets.get(index).toVector();

                // compute distance from centroid
                double dist = 0;
                for(int j = 0; j < 255 * 255; j++) {
                    dist += Math.pow(clusterCentroid[j] - vector[j], 2);
                }
                withinDist += dist;
            }

            //System.out.println(Math.sqrt(withinDist));
        }

//        double totalDistance = 0;
//        for(int i = 0; i < MAX_ITEMS; i++) {
//            for(int j = 0; j < MAX_ITEMS; j++) {
//                totalDistance += distances[i][j];
//            }
//        }
//        for(int k = 2; k <= 20; k++) {
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
