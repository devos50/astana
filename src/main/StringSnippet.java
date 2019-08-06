package main;

import com.googlecode.d2j.node.DexClassNode;
import com.googlecode.d2j.node.insn.ConstStmtNode;
import com.googlecode.d2j.node.insn.DexStmtNode;
import com.googlecode.d2j.reader.Op;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

public class StringSnippet {
    public final String apkPath;
    public final File file;
    public final DexClassNode rootNode;
    public final Method method;
    public ConstStmtNode stringInitNode;
    public int stringInitIndex;

    public List<Pair<Integer, Integer>> stringDecryptPairs;
    public int currentStringDecryptedIndex;
    public int currentStringResultRegister;

    public List<DexStmtNode> extractedStatements = new ArrayList<>();
    public boolean isValidSlice = false;
    public boolean isDecrypted = false;
    public boolean decryptionSuccessful = false;
    public String decryptedString = null;
    public int resultExecutionCode = -1;
    public String resultStderr = null;

    public StringSnippet(String apkPath, File file, DexClassNode rootNode, Method method, int stringInitIndex) {
        this.apkPath = apkPath;
        this.file = file;
        this.rootNode = rootNode;
        this.method = method;
        this.stringInitIndex = stringInitIndex;
        this.stringInitNode = (ConstStmtNode) method.methodNode.codeNode.stmts.get(stringInitIndex);
    }

    public String getString() {
        return this.stringInitNode.value.toString();
    }

    public List<DexStmtNode> getPrunedStatementsList() {
        List<DexStmtNode> statements = new ArrayList<>();
        for(int stmtIndex = 0; stmtIndex < extractedStatements.size(); stmtIndex++) {
            if(extractedStatements.get(stmtIndex).op != null) {
                statements.add(extractedStatements.get(stmtIndex));
            }
        }
        return statements;
    }

    public void computeSlice() {
        extractedStatements = new ArrayList<>();
        isValidSlice = false;

        // find all possible paths from the string declaration to the point where the string is decrypted
        List<MethodExecutionPath> stringPaths = method.getExecutionPaths(stringInitIndex, currentStringDecryptedIndex);
        List<Set<Integer>> statementsSet = new ArrayList<>();

        if(stringPaths.size() == 0) {
            return;
        }

        // build register dependency graphs and compute involved statements
        boolean[] involvedStatements = new boolean[method.methodNode.codeNode.stmts.size()];
        Set<Integer> undefinedRegisters = new HashSet<>();
        for(MethodExecutionPath path : stringPaths) {
            path.buildRegisterDependencyGraph();
            RegisterDependencyNode rootNode = path.registerDependencyGraph.activeRegister.get(currentStringResultRegister);
            statementsSet.add(path.registerDependencyGraph.getInvolvedStatementsForNode(rootNode));
            Set<RegisterDependencyNode> dependencies = path.registerDependencyGraph.getDependencies(rootNode);
            for(Integer undefinedRegister : path.registerDependencyGraph.undefinedRegisters) {
                if(dependencies.contains(new RegisterDependencyNode(undefinedRegister, 0))) {
                    undefinedRegisters.add(undefinedRegister);
                }
            }

            // check whether the original string declaration is an involved register. If not, this string is probably not encrypted
            ConstStmtNode stringInitNode = (ConstStmtNode) method.methodNode.codeNode.stmts.get(stringInitIndex);
            RegisterDependencyNode destNode = new RegisterDependencyNode(stringInitNode.a, 1);
            RegisterDependencyNode sourceNode = path.registerDependencyGraph.activeRegister.get(currentStringResultRegister);
            if(!path.registerDependencyGraph.hasDependency(sourceNode, destNode)) {
                return;
            }
        }

        // test whether the statement sets are the same, if so, jumps do not matter and we can exclude them
        boolean areEqual = true;
        for(int i = 0; i < statementsSet.size() - 1; i++) {
            if(!statementsSet.get(i).equals(statementsSet.get(i + 1))) {
                areEqual = false;
                break;
            }
        }

        // compute involved statements
        for(MethodExecutionPath path : stringPaths) {
            boolean[] pathInvolvedStatements = path.computeInvolvedStatements(currentStringResultRegister, !areEqual);
            for(int i = 0; i < pathInvolvedStatements.length; i++) {
                involvedStatements[i] = involvedStatements[i] || pathInvolvedStatements[i];
            }
        }

        // we now check if there are undefined registers - if there are, create another dependency graph
        if(undefinedRegisters.size() > 0) {
            List<MethodExecutionPath> backwardPaths = method.getExecutionPaths(method.firstStmtIndex, stringInitIndex);
            statementsSet = new ArrayList<>();
            for(MethodExecutionPath backwardPath : backwardPaths) {
                Set<Integer> statementsForPath = new HashSet<>();
                backwardPath.buildRegisterDependencyGraph();

                for(Integer undefinedRegister : undefinedRegisters) {
                    RegisterDependencyNode rootNode = backwardPath.registerDependencyGraph.activeRegister.get(undefinedRegister);
                    Set<RegisterDependencyNode> dependencies = backwardPath.registerDependencyGraph.getDependencies(rootNode);
                    statementsForPath.addAll(backwardPath.registerDependencyGraph.getInvolvedStatementsForNode(rootNode));

                    // if this undefined register depends on another undefined register, the string is probably not encrypted
                    for(Integer undefinedRegister2 : backwardPath.registerDependencyGraph.undefinedRegisters) {
                        if(dependencies.contains(new RegisterDependencyNode(undefinedRegister2, 0))) {
                            return;
                        }
                    }
                }
                statementsSet.add(statementsForPath);
            }

            // test whether the statement sets are the same, if so, jumps do not matter and we can exclude them
            areEqual = true;
            for(int i = 0; i < statementsSet.size() - 1; i++) {
                if(!statementsSet.get(i).equals(statementsSet.get(i + 1))) {
                    areEqual = false;
                    break;
                }
            }

            for(MethodExecutionPath backwardPath : backwardPaths) {
                for(Integer undefinedRegister : undefinedRegisters) {
                    boolean[] pathInvolvedStatements = backwardPath.computeInvolvedStatements(undefinedRegister, !areEqual);
                    for(int i = 0; i < pathInvolvedStatements.length; i++) {
                        involvedStatements[i] = involvedStatements[i] || pathInvolvedStatements[i];
                    }
                }
            }
        }

        for(int i = 0; i < involvedStatements.length; i++) {
            DexStmtNode stmtNode = method.methodNode.codeNode.stmts.get(i);
            if(involvedStatements[i]) {
                extractedStatements.add(stmtNode);
            }
        }

        // we check whether the snippet is not "too basic", i.e., it is not just the creation of a new string based on our encrypted string
        List<DexStmtNode> prunedStatements = getPrunedStatementsList();
        if(!(prunedStatements.size() == 3 && prunedStatements.get(1).op == Op.NEW_INSTANCE && prunedStatements.get(2).op == Op.INVOKE_DIRECT)) {
            isValidSlice = true;
        }
    }

    public void decrypt(StringDatabase database) throws IOException, SQLException {
        // attempt to decrypt the string by iterating over all string decrypt pairs and attempting to decrypt them
        for(int i = 0; i < stringDecryptPairs.size(); i++) {
            Pair<Integer, Integer> currentPair = stringDecryptPairs.get(i);
            currentStringDecryptedIndex = currentPair.getKey();
            currentStringResultRegister = currentPair.getValue();
            isDecrypted = false;

            if(database.isExecuted(this)) {
                continue;
            }

            computeSlice();
            if(!isValidSlice) {
                System.out.println("Invalid slice for snippet with decrypt index " + currentStringDecryptedIndex);
                continue;
            }

            // the slice is valid -> attempt to decrypt
            System.out.println("Attempt to decrypt string: " + getString() + " (l: " + getString().length() + ", " + file.getPath() + ")" + " (" + currentStringDecryptedIndex + ", reg " + currentStringResultRegister + ")");
            StringDecryptor decryptor = new StringDecryptor(this, database);
            decryptionSuccessful = decryptor.decrypt();

            resultExecutionCode = decryptor.resultExecutionCode;
            resultStderr = decryptor.stderr;
            decryptedString = decryptor.stdout;
            isDecrypted = true;

            database.updateSnippet(this);
            if(decryptionSuccessful) {
                break;
            }
        }
    }
}
