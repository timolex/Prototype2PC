import java.util.HashMap;
import java.util.Map;

public class Printer {

    private static final Map<String, String> COLORS = createMap();
    private static final String RESET_COLOR = "\033[0m";

    private static Map<String, String> createMap() {
        Map<String, String> c = new HashMap<>();
        c.put("red", "\033[0;31m");
        c.put("green", "\033[0;32m");
        c.put("orange", "\033[0;33m");
        c.put("blue", "\033[0;34m");
        return c;
    }

    public static void print(String msg) {
        System.out.println(msg);
    }

    public static void print(String msg, String color) {
        String colorEscape = COLORS.get(color);
        if (colorEscape == null)
            print(msg);
        else
            System.out.println(COLORS.get(color) + msg + RESET_COLOR);
    }

}
