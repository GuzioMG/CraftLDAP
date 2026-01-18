package hub.guzio.CraftLdap;

import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

public record Logger(Level lvl, String as) {

    public void log(String msg) {
        LoggerFactory.getLogger(as).atLevel(lvl).log(msg);
    }

    public void log(String msg, Object... args) {
        LoggerFactory.getLogger(as).atLevel(lvl).log(msg, args);
    }
}
