package filters.strategies;

import java.util.ArrayList;
import java.util.List;
import filters.ImageFilter;

public class FullPermutationStrategy implements FilterOrderStrategy {
    @Override
    public List<List<Class<? extends ImageFilter>>> generateOrders(List<Class<? extends ImageFilter>> baseFilters) {
        List<List<Class<? extends ImageFilter>>> result = new ArrayList<>();

        //Ask the Helper to generate combinations using the total size of the list.
        int targetSize = baseFilters.size();
        CombinatoricsHelper.generatePermutationsOfSize(baseFilters, new ArrayList<>(), result, targetSize);

        return result;
    }
}
