package filters.strategies;

import java.util.List;
import filters.ImageFilter;

public interface FilterOrderStrategy {
    List<List<Class<? extends ImageFilter>>> generateOrders(List<Class<? extends ImageFilter>> baseFilters);
}
