package main;

import com.googlecode.d2j.node.insn.DexStmtNode;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ProgramSlice {

    public final String apkPath;
    public final File file;
    public final Method method;
    public final int criterionStmtIndex;
    public int resultRegisterIndex;
    public List<DexStmtNode> extractedStatements = new ArrayList<>();
    public int exectionResultCode = -1;
    public String executionStdout = null;
    public String executionStderr = null;

    public ProgramSlice(String apkPath, File file, Method method, int criterionStmtIndex, int resultRegisterIndex) {
        this.apkPath = apkPath;
        this.file = file;
        this.method = method;
        this.criterionStmtIndex = criterionStmtIndex;
        this.resultRegisterIndex = resultRegisterIndex;
    }

    public boolean isSubslice(ProgramSlice other) {
        // check whether the other slice is a subslice of this one
        if(other.extractedStatements.size() < extractedStatements.size()) {
            return false;
        }

        Set<DexStmtNode> thisSet = new HashSet<>(extractedStatements);
        Set<DexStmtNode> otherSet = new HashSet<>(extractedStatements);
        return otherSet.containsAll(thisSet);
    }

}
