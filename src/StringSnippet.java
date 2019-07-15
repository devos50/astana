import com.googlecode.d2j.node.insn.ConstStmtNode;
import com.googlecode.d2j.node.insn.DexStmtNode;
import com.googlecode.d2j.reader.Op;
import javafx.util.Pair;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class StringSnippet {
    public final File file;
    public Method method;
    public ConstStmtNode stringInitNode;
    public int stringInitIndex;
    public int stringDecryptedIndex;
    public List<DexStmtNode> extractedStatements = new ArrayList<>();
    public ArrayList<String> stringStatements = new ArrayList<>();
    public HashMap<Pair<Integer, Integer>, Integer> frequencyMap = new HashMap<>();
    public int stringResultRegister = 0;

    public StringSnippet(File file, Method method, int stringInitIndex) {
        this.file = file;
        this.method = method;
        this.stringInitIndex = stringInitIndex;
        this.stringInitNode = (ConstStmtNode) method.methodNode.codeNode.stmts.get(stringInitIndex);
    }

    public String getString() {
        return this.stringInitNode.value.toString();
    }

    public double[] toVector() {
        double[] vector = new double[255 * 255];
        for(Pair<Integer, Integer> key : frequencyMap.keySet()) {
            int code = key.getKey() * 255 + key.getValue();
            vector[code] = frequencyMap.get(key);
        }
        return vector;
    }

    private int normalizeOpCode(int opcode) {
        if(opcode == Op.CONST_4.opcode) { opcode = Op.CONST_16.opcode; }
        if(opcode == Op.CONST_STRING_JUMBO.opcode) { opcode = Op.CONST_STRING.opcode; }
        return opcode;
    }
}
