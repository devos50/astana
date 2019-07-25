package main;

import com.googlecode.d2j.smali.BaksmaliCmd;
import com.googlecode.dex2jar.tools.Dex2jarCmd;
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import smile.clustering.KMeans;

import java.io.*;
import java.sql.SQLException;
import java.util.*;

public class Main {
    private static ArrayList<StringSnippet> snippets = new ArrayList<>();
    private static StringDatabase database;

    public static void processApk(File apkFile) throws SQLException, IOException {
        String apkName = apkFile.getName();
//        if(database.isPreprocessed(apkName)) {
//            System.out.println("APK " + apkName + " is already processed - ignoring");
//            return;
//        }

        database.addApplication(apkName);

        File jarFile = new File("data/" + apkName + ".jar");
        if(!jarFile.exists()) {
            // convert APK to .jar in order to run smali code
            new Dex2jarCmd().doMain(apkFile.getPath(), "-o", "data/" + apkName + ".jar", "-n");
        }

        File smaliFilesPath = new File("data/" + apkName + "-smali");
        if(!smaliFilesPath.exists()) {
            // convert APK to smali files
            new BaksmaliCmd().doMain(apkFile.getPath(), "-o", "data/" + apkName + "-smali");
        }

        File analyzePath = new File("data/" + apkName + "-smali");
        int numStrings = 0;
        for(File smaliFile : FileUtils.listFiles(analyzePath, new String[] { "smali" }, true)) {
            if(!smaliFile.getPath().equals("data/barclays.apk-smali/u/aq.smali")) {
                continue;
            }

            if(!smaliFile.getPath().startsWith("data/" + apkName + "-smali/android")) {
                System.out.println("Processing file " + smaliFile.getPath());
                SmaliFileParser parser = new SmaliFileParser(apkName, smaliFile);
                parser.parseFile();
                parser.processStrings();
                snippets.addAll(parser.snippets);
                numStrings += parser.numStrings;
            } else {
                System.out.println("Skipping file " + smaliFile.getPath());
            }
        }

        // insert snippets in the database
        for(StringSnippet snippet : snippets) {
            database.insertSnippet(snippet);
        }

        database.setPreprocessed(apkName, numStrings);
        System.out.println("Snippets: " + snippets.size());
    }

    public static void main(String[] args) throws IOException, SQLException, ParseException {
        Options options = new Options();
        Option input = new Option("i", "input", true, "input file path");
        input.setRequired(true);
        options.addOption(input);

        CommandLineParser parser = new DefaultParser();
        CommandLine line = parser.parse( options, args );

        database = new StringDatabase();

        String inputPath = line.getOptionValue("i");
        File inputFilePath = new File(inputPath);
        if(inputFilePath.isDirectory()) {
            // get all the APKs in this directory and parse them indiviually
            for(File apkFile : FileUtils.listFiles(inputFilePath, new String[] { "apk" }, true)) {
                System.out.println("Processing APK: " + apkFile);
                processApk(apkFile);
            }
        }
        else {
            if(!inputFilePath.getPath().endsWith(".apk")) {
                System.out.println("Provided input file is not an APK file!");
                System.exit(1);
            }
            processApk(inputFilePath);
        }

//        SmaliFileParser parser = new SmaliFileParser(new File("data/lloyds-smali/com/google/android/gms/dynamite/DynamiteModule.smali"));
//        parser.parseFile();
//        parser.process();
//        snippets.addAll(parser.snippets);
//        System.out.println("Snippets: " + snippets.size());

//        for(StringSnippet snippet : snippets) {
//            SliceExecutor.decrypt(snippet);
//            if(snippet.decryptionSuccessful) {
//                System.out.println("--> decrypted --> " + snippet.decryptedString);
//            }
//        }

//        System.out.println("Starting to compute distance matrix of " + snippets.size() + " items!");
//
//        Collections.shuffle(snippets, new Random(1));
//
        // compute distance matrix
//        int MAX_ITEMS = Math.min(snippets.size(), 7000);
//
//        double[][] distances = new double[MAX_ITEMS][MAX_ITEMS];
//        for(int i = 0; i < MAX_ITEMS; i++) {
//            for(int j = 0; j < i; j++) {
//                double dist = 1 - snippets.get(i).cosineSimilarity(snippets.get(j));
//                distances[i][j] = dist;
//                distances[j][i] = dist;
//            }
//
//            if(i % 1000 == 0) { System.out.println(i); }
//        }

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

//        System.out.println("Clustering...");
//        KMeans kmeans = new KMeans(distances, 10, 30000);
//        int[] membership = kmeans.getClusterLabel();
//        for(int cluster = 0; cluster < 10; cluster++) {
//            ArrayList<Integer> belongsTo = new ArrayList<>();
//            for(int i = 0; i < membership.length; i++) {
//                if(membership[i] == cluster) { belongsTo.add(i); }
//            }
//            System.out.println("Members in cluster " + cluster + ": " + belongsTo.size());
//
//            int encrypted = 0;
//            int decrypted = 0;
//            for(int i = 0; i < belongsTo.size(); i++) {
//                StringSnippet item = snippets.get(belongsTo.get(i));
//                if(item.file.getPath().contains("data/lloyds-smali/iiiiii") || item.file.getPath().contains("data/barclays-smali/p") || item.file.getPath().contains("data/barclays-smali/com/barclays")) { encrypted++; }
//                else { decrypted++; }
//
//                System.out.println("Item " + belongsTo.get(i) + ": " + item.getPrintableStatements());
//            }
//
//            for(int i = 0; i < belongsTo.size(); i++) {
//                StringSnippet item = snippets.get(belongsTo.get(i));
//                System.out.println("Item " + belongsTo.get(i) + ": C " + cluster + ", file: " + item.file.getPath() + ", str: " + item.getString());
//            }
//
//            // compute mean distance
//            ArrayList<Double> inClusterDistances = new ArrayList<>();
//            for(int i = 0; i < belongsTo.size(); i++) {
//                for(int j = 0; j < belongsTo.size(); j++) {
//                    inClusterDistances.add(distances[belongsTo.get(i)][belongsTo.get(j)]);
//                }
//            }
//
//            Collections.sort(inClusterDistances);
//            if(inClusterDistances.size() < 10) {
//                System.out.println(inClusterDistances);
//            }
//
//
//            if(inClusterDistances.size() % 2 == 0) {
//                double median = (inClusterDistances.get(inClusterDistances.size() / 2) + inClusterDistances.get(inClusterDistances.size() / 2 - 1)) / 2.0;
//                System.out.println(median);
//            }
//            else {
//                System.out.println(inClusterDistances.get(inClusterDistances.size() / 2));
//            }

//            System.out.println("encrypted: " + encrypted + ", decrypted: " + decrypted);

//            // compute sum of squares within cluster
//
//            // compute centroid
//            double[] clusterCentroid = new double[255 * 255];
//            for(int i = 0; i < belongsTo.size(); i++) {
//                int index = belongsTo.get(i);
//                HashMap<Pair<Integer, Integer>, Integer> frequencyMap = snippets.get(index).frequencyMap;
//
//                for(Pair<Integer, Integer> key : frequencyMap.keySet()) {
//                    int code = key.getKey() * 255 + key.getValue();
//                    clusterCentroid[code] += frequencyMap.get(key);
//                }
//            }
//
//            // normalize
//            double withinDist = 0;
//            for(int i = 0; i < 255 * 255; i++) {
//                clusterCentroid[i] /= belongsTo.size();
//            }
//
//            for(int i = 0; i < belongsTo.size(); i++) {
//                int index = belongsTo.get(i);
//                double[] vector = snippets.get(index).toVector();
//
//                // compute distance from centroid
//                double dist = 0;
//                for(int j = 0; j < 255 * 255; j++) {
//                    dist += Math.pow(clusterCentroid[j] - vector[j], 2);
//                }
//                withinDist += dist;
//            }
//
//            System.out.println(Math.sqrt(withinDist));
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
//    }
}
