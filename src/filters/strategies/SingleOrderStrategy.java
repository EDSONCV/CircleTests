package filters.strategies;

import java.util.ArrayList;
import java.util.List;
import filters.ImageFilter;

public class SingleOrderStrategy implements FilterOrderStrategy {
    @Override
    public List<List<Class<? extends ImageFilter>>> generateOrders(List<Class<? extends ImageFilter>> baseFilters) {
        List<List<Class<? extends ImageFilter>>> result = new ArrayList<>();
        // Adiciona apenas a lista original, sem permutar
        result.add(new ArrayList<>(baseFilters));
        return result;
    }
}