package filters.strategies;

import java.util.ArrayList;
import java.util.List;
import filters.ImageFilter;

public class FullPermutationStrategy implements FilterOrderStrategy {
    @Override
    public List<List<Class<? extends ImageFilter>>> generateOrders(List<Class<? extends ImageFilter>> baseFilters) {
        List<List<Class<? extends ImageFilter>>> result = new ArrayList<>();

        // Pede ao Helper para gerar combinações usando o tamanho total da lista
        int targetSize = baseFilters.size();
        CombinatoricsHelper.generatePermutationsOfSize(baseFilters, new ArrayList<>(), result, targetSize);

        return result;
    }
}
