import com.googlecode.d2j.node.insn.ConstStmtNode;
import com.googlecode.d2j.node.insn.DexLabelStmtNode;
import com.googlecode.d2j.node.insn.DexStmtNode;
import com.googlecode.d2j.node.insn.FieldStmtNode;

import java.io.File;
import java.util.ArrayList;

public class EncryptedStringDecryption {
    public File file;
    public String encryptedString;
    public ArrayList<DexStmtNode> statements;
    public ArrayList<String> stringStatements = new ArrayList<>();
    public double[] nGramVector = new double[255];

    public EncryptedStringDecryption(File file, String encryptedString, ArrayList<DexStmtNode> statements) {
        this.file = file;
        this.encryptedString = encryptedString;
        this.statements = statements;

        for(DexStmtNode node : statements) {
            if(node instanceof DexLabelStmtNode) { continue; }
            stringStatements.add(node.op.toString());
            nGramVector[node.op.opcode]++;
        }
    }
}
