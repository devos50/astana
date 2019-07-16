import com.googlecode.d2j.node.insn.ConstStmtNode;
import com.googlecode.d2j.node.insn.DexStmtNode;
import com.googlecode.d2j.reader.Op;
import javafx.util.Pair;

import java.io.File;
import java.util.*;

public class StringSnippet {
    public final File file;
    public Method method;
    public ConstStmtNode stringInitNode;
    public int stringInitIndex;
    public int stringDecryptedIndex;
    public List<DexStmtNode> extractedStatements = new ArrayList<>();
    public HashMap<Pair<Integer, Integer>, Integer> frequencyMap = new HashMap<>();
    public int stringResultRegister = 0;
    public boolean stringIsEncrypted = true;
    public boolean finalized = true;

    public StringSnippet(File file, Method method, int stringInitIndex) {
        this.file = file;
        this.method = method;
        this.stringInitIndex = stringInitIndex;
        this.stringInitNode = (ConstStmtNode) method.methodNode.codeNode.stmts.get(stringInitIndex);
    }

    public String getString() {
        return this.stringInitNode.value.toString();
    }

    public List<String> getPrintableStatements() {
        List<String> stringStatements = new ArrayList<>();
        for(int stmtIndex = 0; stmtIndex < extractedStatements.size(); stmtIndex++) {
            if(extractedStatements.get(stmtIndex).op != null) {
                stringStatements.add(extractedStatements.get(stmtIndex).op.toString());
            }
        }
        return stringStatements;
    }

    public void finalize() {
        // finalize the snippet by computing the frequency map
        for(int i = 0; i < extractedStatements.size(); i++) {
            DexStmtNode node = extractedStatements.get(i);
            if(i != extractedStatements.size() - 1) {
                DexStmtNode nextNode = extractedStatements.get(i + 1);
                if(node.op == null || nextNode.op == null) { continue; }
                Pair<Integer, Integer> pair = new Pair<>(normalizeOpCode(node.op.opcode), normalizeOpCode(nextNode.op.opcode));
                if(!frequencyMap.containsKey(pair)) {
                    frequencyMap.put(pair, 0);
                }
                frequencyMap.put(pair, frequencyMap.get(pair) + 1);
            }
        }

        finalized = true;
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

    public double cosineSimilarity(StringSnippet other) {
        if(!this.finalized || !other.finalized) {
            throw new RuntimeException("This snippet (or other snippet) not finalized!");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        // we first need the dot product -> will only result if there is overlap
        Set<Pair<Integer, Integer>> keysA = this.frequencyMap.keySet();
        Set<Pair<Integer, Integer>> keysB = other.frequencyMap.keySet();
        Set<Pair<Integer, Integer>> intersection = new HashSet<>(keysA);
        intersection.retainAll(keysB);

        for(Pair<Integer, Integer> pair : intersection) {
            dotProduct += this.frequencyMap.get(pair) * other.frequencyMap.get(pair);
        }

        // compute normalization values
        Iterator it = this.frequencyMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            normA += Math.pow((Integer)pair.getValue(), 2);
        }

        it = other.frequencyMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            normB += Math.pow((Integer)pair.getValue(), 2);
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }


}
