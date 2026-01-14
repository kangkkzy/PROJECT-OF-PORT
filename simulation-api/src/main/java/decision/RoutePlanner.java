package decision;
import map.Location;
import java.util.List;

public interface RoutePlanner {
    List<Location> searchRoute(Location origin, Location destination);
}