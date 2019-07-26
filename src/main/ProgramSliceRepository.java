package main;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class ProgramSliceRepository {

    Set<ProgramSlice> slices = new HashSet<>();

    public void add(ProgramSlice slice) {
        slices.add(slice);
    }

    public void addAll(Collection<ProgramSlice> newSlices) {
        slices.addAll(newSlices);
    }

}
