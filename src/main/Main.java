package main;

import com.googlecode.d2j.dex.Dex2jar;
import com.googlecode.d2j.reader.BaseDexFileReader;
import com.googlecode.d2j.reader.MultiDexFileReader;
import com.googlecode.d2j.smali.BaksmaliCmd;
import com.googlecode.dex2jar.tools.BaksmaliBaseDexExceptionHandler;
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.*;

public class Main {
    private static ArrayList<StringSnippet> snippets = new ArrayList<>();
    private static StringDatabase database;

    public static void processApk(File apkFile) throws SQLException, IOException {
        String apkName = apkFile.getName();
        if(database.isPreprocessed(apkName)) {
            System.out.println("APK " + apkName + " is already processed - ignoring");
            return;
        }

        database.addApplication(apkName);

        File jarFile = new File("data/" + apkName + ".jar");
        if(!jarFile.exists()) {
            // convert APK to .jar in order to run smali code

            BaseDexFileReader reader = MultiDexFileReader.open(Files.readAllBytes(apkFile.toPath()));
            BaksmaliBaseDexExceptionHandler handler = new BaksmaliBaseDexExceptionHandler();
            Dex2jar.from(reader).withExceptionHandler(handler).reUseReg(false).topoLogicalSort().skipDebug(true).optimizeSynchronized(false).printIR(false).noCode(false).skipExceptions(true).to(jarFile.toPath());
        }

        File smaliFilesPath = new File("data/" + apkName + "-smali");
        if(!smaliFilesPath.exists()) {
            // convert APK to smali files
            new BaksmaliCmd().doMain(apkFile.getPath(), "-o", "data/" + apkName + "-smali");
        }

        File analyzePath = new File("data/" + apkName + "-smali");
        int numStrings = 0;
        for(File smaliFile : FileUtils.listFiles(analyzePath, new String[] { "smali" }, true)) {
//            if(!smaliFile.getPath().equals("data/com.tescobank.mobile-10003003.apk-smali/r/jjr.smali")) {
//                continue;
//            }

            // skip some well-known libraries
            if(!smaliFile.getPath().startsWith("data/" + apkName + "-smali/android") &&
                    !smaliFile.getPath().startsWith("data/" + apkName + "-smali/kotlin") &&
                    !smaliFile.getPath().startsWith("data/" + apkName + "-smali/org/apache") &&
                    !smaliFile.getPath().startsWith("data/" + apkName + "-smali/com/google/android") &&
                    !smaliFile.getPath().startsWith("data/" + apkName + "-smali/com/google/zxing") &&
                    !smaliFile.getPath().startsWith("data/" + apkName + "-smali/com/facebook") &&
                    !smaliFile.getPath().startsWith("data/" + apkName + "-smali/com/google/i18n") &&
                    !smaliFile.getPath().startsWith("data/" + apkName + "-smali/com/google/firebase") &&
                    !smaliFile.getPath().startsWith("data/" + apkName + "-smali/butterknife") &&
                    !smaliFile.getPath().startsWith("data/" + apkName + "-smali/com/fasterxml/jackson") &&
                    !smaliFile.getPath().startsWith("data/" + apkName + "-smali/com/android/volley") &&
                    !smaliFile.getPath().startsWith("data/" + apkName + "-smali/com/squareup/okhttp")) {
                System.out.println("Processing file " + smaliFile.getPath());
                SmaliFileParser parser = new SmaliFileParser(apkName, smaliFile);
                parser.process();
                snippets.addAll(parser.snippets);
                numStrings += parser.numStrings;
            } else {
                System.out.println("Skipping file " + smaliFile.getPath());
            }
        }

        System.out.println("Snippets: " + snippets.size());

        // decrypt them
        for(StringSnippet snippet : snippets) {
            if(!database.hasSuccessfulDecryption(snippet)) {
                snippet.decrypt(database);
            }
        }

        database.setPreprocessed(apkName, numStrings);
        snippets = new ArrayList<>();
    }

    public static void main(String[] args) throws IOException, SQLException, ParseException {
        Options options = new Options();
        Option input = new Option("i", "input", true, "input file path");
        input.setRequired(true);
        options.addOption(input);

        CommandLineParser parser = new DefaultParser();
        CommandLine line = parser.parse(options, args);

        database = new StringDatabase();

        String inputPath = line.getOptionValue("i");
        File inputFilePath = new File(inputPath);
        if (inputFilePath.isDirectory()) {
            // get all the APKs in this directory and parse them indiviually
            for (File apkFile : FileUtils.listFiles(inputFilePath, new String[]{"apk"}, true)) {
                System.out.println("Processing APK: " + apkFile);
                processApk(apkFile);
            }
        } else {
            if (!inputFilePath.getPath().endsWith(".apk")) {
                System.out.println("Provided input file is not an APK file!");
                System.exit(1);
            }
            processApk(inputFilePath);
        }
    }
}
