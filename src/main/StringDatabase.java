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
                    "string TEXT," +
                    "statements TEXT," +
                    "is_decrypted INTEGER," +
                    "result TEXT" +
                    ");";

            Statement stmt = connection.createStatement();
            stmt.execute(sql);
        }

        if(!tableExists("applications")) {
            String sql = "CREATE TABLE applications(" +
                    "apk varchar(255) PRIMARY KEY," +
                    "preprocessed INTEGER" +
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
            String sql = "INSERT INTO applications VALUES(?, 0)";
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

    public void setPreprocessed(String apkPath) throws SQLException {
        String sql = "UPDATE applications SET preprocessed = 1 WHERE apk = ?";
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        preparedStatement.setString(1, apkPath);
        preparedStatement.execute();
    }

    public void insertSnippet(StringSnippet snippet) throws SQLException {
        // compute involved statements
        Set<String> involvedStatementsIndices = new HashSet<>();
        for(DexStmtNode node : snippet.extractedStatements) {
            involvedStatementsIndices.add("" + snippet.method.methodNode.codeNode.stmts.indexOf(node));
        }

        String sql = "INSERT INTO strings VALUES(?, ?, ?, ?, ?, ?, 0, ?)";
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        preparedStatement.setInt(1, getNextId());
        preparedStatement.setString(2, snippet.apkPath);
        preparedStatement.setString(3, snippet.file.getPath());
        preparedStatement.setString(4, snippet.method.methodNode.method.getName());
        preparedStatement.setString(5, snippet.getString());
        preparedStatement.setString(6, String.join(",", involvedStatementsIndices));
        preparedStatement.setString(7, null);
        preparedStatement.execute();
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
}
