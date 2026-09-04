package filters.strategies;

import java.util.ArrayList;
import java.util.List;

public class CombinatoricsHelper {

    /**
     * A generic recursive method for generating permutations (arrangements) of a specific size.
     */
    public static <T> void generatePermutationsOfSize(
            List<Class<? extends T>> original,
            List<Class<? extends T>> current,
            List<List<Class<? extends T>>> result,
            int targetSize) {

        if (current.size() == targetSize) {
            result.add(new ArrayList<>(current));
            return;
        }

        for (int i = 0; i < original.size(); i++) {
            Class<? extends T> item = original.get(i);

            List<Class<? extends T>> newOriginal = new ArrayList<>(original);
            newOriginal.remove(i);

            current.add(item);
            generatePermutationsOfSize(newOriginal, current, result, targetSize);
            current.remove(current.size() - 1);
        }
    }
}