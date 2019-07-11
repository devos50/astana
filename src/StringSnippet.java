import com.googlecode.d2j.node.DexMethodNode;
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
    public DexMethodNode methodNode;
    public ConstStmtNode stringInitMethod;
    public List<DexStmtNode> statements;
    public ArrayList<String> stringStatements = new ArrayList<>();
    public HashMap<Pair<Integer, Integer>, Integer> frequencyMap = new HashMap<>();
    public int stringResultRegister = 0;

    public StringSnippet(File file, DexMethodNode methodNode, ConstStmtNode stringInitNode) {
        this.file = file;
        this.statements = new ArrayList<>();
        this.methodNode = methodNode;
        this.stringInitMethod = stringInitNode;
    }

    public String getString() {
        return this.stringInitMethod.value.toString();
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

    public void finalize() {
        for(int i = 0; i < statements.size(); i++) {
            DexStmtNode node = statements.get(i);
            if(node.op != null) {
                stringStatements.add(node.op.toString());
            }
            if(i != statements.size() - 1) {
                DexStmtNode nextNode = statements.get(i + 1);
                if(node.op != null && nextNode.op != null) {
                    Pair<Integer, Integer> pair = new Pair<>(normalizeOpCode(node.op.opcode), normalizeOpCode(nextNode.op.opcode));
                    if(!frequencyMap.containsKey(pair)) {
                        frequencyMap.put(pair, 0);
                    }
                    frequencyMap.put(pair, frequencyMap.get(pair) + 1);
                }

            }
        }
    }
}
