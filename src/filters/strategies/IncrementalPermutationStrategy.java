package filters.strategies;


import java.util.ArrayList;
import java.util.List;
import filters.ImageFilter;

public class IncrementalPermutationStrategy implements FilterOrderStrategy {
    @Override
    public List<List<Class<? extends ImageFilter>>> generateOrders(List<Class<? extends ImageFilter>> baseFilters) {
        List<List<Class<? extends ImageFilter>>> result = new ArrayList<>();
// 1. First run: empty set (no filter applied, only hough circle detection)
        result.add(new ArrayList<>());
        // Loop from 1 until # of available filters
        for (int size = 1; size <= baseFilters.size(); size++) {
            CombinatoricsHelper.generatePermutationsOfSize(baseFilters, new ArrayList<>(), result, size);
        }

        return result;
    }
}