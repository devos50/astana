package main;

import com.googlecode.d2j.node.insn.DexStmtNode;

import java.sql.*;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;


public class StringDatabase {

    public static Connection connection;
    public static final String driverUrl = "jdbc:sqlite:temp/strings.db";

    public StringDatabase() throws SQLException {
        connection = DriverManager.getConnection(driverUrl);
        if(!tableExists("strings")) {
            String sql = "CREATE TABLE strings(" +
                    "id INTEGER PRIMARY KEY," +
                    "apk varchar(255)," +
                    "file varchar(255)," +
                    "method varchar(255)," +
                    "string_init_index INTEGER," +
                    "string_decrypt_index INTEGER," +
                    "string TEXT," +
                    "statements TEXT," +
                    "is_decrypted INTEGER," +
                    "result TEXT," +
                    "execution_result_code INTEGER," +
                    "stderr TEXT" +
                    ");";

            Statement stmt = connection.createStatement();
            stmt.execute(sql);
        }

        if(!tableExists("applications")) {
            String sql = "CREATE TABLE applications(" +
                    "apk varchar(255) PRIMARY KEY," +
                    "preprocessed INTEGER," +
                    "num_strings INTEGER" +
                    ");";

            Statement stmt = connection.createStatement();
            stmt.execute(sql);
        }
    }

    private int getNextId() throws SQLException {
        String sql = "SELECT MAX(id) FROM strings;";
        Statement stmt  = connection.createStatement();
        ResultSet rs    = stmt.executeQuery(sql);
        if(!rs.next()) { return 1; }
        return rs.getInt(1) + 1;
    }

    public void addApplication(String apkPath) throws SQLException {
        if(!hasApplication(apkPath)) {
            String sql = "INSERT INTO applications VALUES(?, 0, 0)";
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setString(1, apkPath);
            preparedStatement.execute();
        }
    }

    public boolean hasApplication(String apkPath) throws SQLException {
        String sql = "SELECT apk FROM applications WHERE apk = ?";
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        preparedStatement.setString(1, apkPath);
        ResultSet rs = preparedStatement.executeQuery();
        return rs.next();
    }

    public boolean isPreprocessed(String apkPath) throws SQLException {
        String sql = "SELECT preprocessed FROM applications WHERE apk = ?";
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        preparedStatement.setString(1, apkPath);
        ResultSet rs = preparedStatement.executeQuery();
        if(!rs.next()) { return false; }
        return rs.getBoolean(1);
    }

    public void setPreprocessed(String apkPath, int numStrings) throws SQLException {
        String sql = "UPDATE applications SET preprocessed = 1, num_strings = ? WHERE apk = ?";
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        preparedStatement.setInt(1, numStrings);
        preparedStatement.setString(2, apkPath);
        preparedStatement.execute();
    }

    public boolean isDecrypted(StringSnippet snippet) throws SQLException {
        int existingSnippetId = hasSnippet(snippet);
        if(existingSnippetId == -1) {
            return false;
        }

        String sql = "SELECT is_decrypted FROM strings WHERE id = ?";
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        preparedStatement.setInt(1, existingSnippetId);
        ResultSet rs = preparedStatement.executeQuery();
        rs.next();
        return rs.getBoolean(1);
    }

    public void insertSnippet(StringSnippet snippet) throws SQLException {
        int existingSnippetId = hasSnippet(snippet);
        if(existingSnippetId != -1) {
            return;
        }

        // compute involved statements
        Set<String> involvedStatementsIndices = new HashSet<>();
        for(DexStmtNode node : snippet.extractedStatements) {
            involvedStatementsIndices.add("" + snippet.method.methodNode.codeNode.stmts.indexOf(node));
        }

        String sql = "INSERT INTO strings VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        preparedStatement.setInt(1, getNextId());
        preparedStatement.setString(2, snippet.apkPath);
        preparedStatement.setString(3, snippet.file.getPath());
        preparedStatement.setString(4, snippet.method.methodNode.method.getName());
        preparedStatement.setInt(5, snippet.stringInitIndex);
        preparedStatement.setInt(6, snippet.stringDecryptedIndex);
        preparedStatement.setString(7, snippet.getString());
        preparedStatement.setString(8, String.join(",", involvedStatementsIndices));
        preparedStatement.setBoolean(9, snippet.isDecrypted);
        preparedStatement.setString(10, snippet.decryptedString);
        preparedStatement.setInt(11, snippet.executionResultCode);
        preparedStatement.setString(12, snippet.resultStderr);
        preparedStatement.execute();
    }

    public void updateSnippet(StringSnippet snippet) throws SQLException {
        int existingSnippetId = hasSnippet(snippet);
        if(existingSnippetId == -1) {
            insertSnippet(snippet);
        }
        else {
            // update existing entry
            String sql = "UPDATE strings SET is_decrypted = ?, result = ?, execution_result_code = ?, stderr = ? WHERE id = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setBoolean(1, snippet.isDecrypted);
            preparedStatement.setString(2, snippet.decryptedString);
            preparedStatement.setInt(3, snippet.executionResultCode);
            preparedStatement.setString(4, snippet.resultStderr);
            preparedStatement.setInt(5, existingSnippetId);
            preparedStatement.execute();
        }
    }

    public int hasSnippet(StringSnippet snippet) throws SQLException {
        String sql = "SELECT id FROM strings WHERE apk = ? AND file = ? AND method = ? and string_init_index = ? AND string_decrypt_index = ?";
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        preparedStatement.setString(1, snippet.apkPath);
        preparedStatement.setString(2, snippet.file.getPath());
        preparedStatement.setString(3, snippet.method.methodNode.method.getName());
        preparedStatement.setInt(4, snippet.stringInitIndex);
        preparedStatement.setInt(5, snippet.stringDecryptedIndex);
        ResultSet rs = preparedStatement.executeQuery();
        if(!rs.next()) { return -1; }
        return rs.getInt(1);
    }

    public boolean tableExists(String tableName){
        try{
            DatabaseMetaData md = connection.getMetaData();
            ResultSet rs = md.getTables(null, null, tableName, null);
            rs.next();
            return rs.getRow() > 0;
        }catch(SQLException ex){
            Logger.getLogger(StringDatabase.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }

    public void printCharacterDistribution() throws SQLException {
        // encrypted
        int[] encryptedDistribution = new int[128];
        String sql = "SELECT string FROM strings";
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        ResultSet rs = preparedStatement.executeQuery();
        int total = 0;
        while(rs.next()) {
            String encryptedString = rs.getString(1);
            for(int i = 0; i < encryptedString.length(); i++) {
                if(encryptedString.charAt(i) < 128) {
                    total += 1;
                    encryptedDistribution[encryptedString.charAt(i)]++;
                }
            }
        }

        for(int i = 0; i < encryptedDistribution.length; i++) {
            System.out.println("obfuscated," + i + "," + encryptedDistribution[i] / (float)total);
        }

        // decrypted
        int[] decryptedDistribution = new int[128];
        sql = "SELECT result FROM strings WHERE is_decrypted = 1 AND execution_result_code = 0";
        preparedStatement = connection.prepareStatement(sql);
        rs = preparedStatement.executeQuery();
        total = 0;
        while(rs.next()) {
            String decryptedString = rs.getString(1);
            if(decryptedString != null) {
                for(int i = 0; i < decryptedString.length(); i++) {
                    if(decryptedString.charAt(i) < 128) {
                        total += 1;
                        decryptedDistribution[decryptedString.charAt(i)]++;
                    }
                }
            }
        }

        for(int i = 0; i < decryptedDistribution.length; i++) {
            System.out.println("deobfuscated," + i + "," + decryptedDistribution[i] / (float)total);
        }
    }
}
