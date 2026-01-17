package hub.guzio.CraftLdap;

import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

public class Main implements ModInitializer {

    private static final String logAs = "CraftLDAP";

    public static final LoggingEventBuilder out = LoggerFactory.getLogger(logAs).atInfo();
    public static final LoggingEventBuilder err = LoggerFactory.getLogger(logAs).atError();
    public static final LoggingEventBuilder wrn = LoggerFactory.getLogger(logAs).atWarn();
    public static final LoggingEventBuilder dbg = LoggerFactory.getLogger(logAs).atDebug();

    public static final String KEY_HOST = "host";
    public static final String KEY_PORT = "port";
    public static final String KEY_USER = "bind-dn";
    public static final String KEY_PASS = "bind-pass";
    public static final String KEY_SAFE = "safe-mode";
    public static final String KEY_GROUP = "base-dn";
    public static final String KEY_FILTER = "filter";
    public static final String KEY_WEBSITE = "bugreport-url";
    public static final String KEY_LDAPNAME = "ldap-attr-name";
    public static final String KEY_MCUUID = "ldap-attr-mcuuid";
    public static final String KEY_ERRUNKNOWN = "message-error";
    public static final String KEY_ERRAUTH = "message-unauthorized";

    @Override
    public void onInitialize() {
        out.log("Starting CraftLDAP...");

        out.log("Getting the config...");
        Path props = FabricLoader.getInstance().getConfigDir().resolve("ldap.properties");
        int config_port = 3890;
        dbg.log("Path is:", props);
        var errmsg_generic = "\nCannot operate; crashing the server instead.";
        var config = new Properties();
        try {
            config.load(new FileInputStream(props.toFile()));
        } catch (IOException e) {
            if (e instanceof FileNotFoundException){
                wrn.log("A new config file needs to be created because the previous one failed to load. Error details:", e);
                config.setProperty(KEY_HOST, "ldap");
                config.setProperty(KEY_PORT, ""+config_port);
                config.setProperty(KEY_USER, "cn=[some user that can read the index],ou=people,dc=example,dc=com");
                config.setProperty(KEY_PASS, "[the password of that user]");
                config.setProperty(KEY_SAFE, "true");
                config.setProperty(KEY_GROUP, "ou=people,dc=example,dc=com");
                config.setProperty(KEY_FILTER, "(&(objectclass=person)(memberOf=cn=[some group],ou=groups,dc=example,dc=com))");
                config.setProperty(KEY_WEBSITE, "https://example.com/");
                config.setProperty(KEY_LDAPNAME, "uid");
                config.setProperty(KEY_MCUUID, "mcuuid");
                config.setProperty(KEY_ERRUNKNOWN, "Something went wrong with CraftLDAP auth. THIS IS NOT YOUR FAULT, please report this error to the server admin instead!!!");
                config.setProperty(KEY_ERRAUTH, "You're not authorized to play on this server.");

                try {
                    config.store(new FileWriter(props.toFile()), "Config file for CraftLDAP. Refer to Modrinth page or GitHub README for documentation.");
                } catch (IOException ex) {
                    err.log("Couldn't open the config file due to it not yet existing. Error details:", e, "\nSubsequently, attempted to create a new one. That, too however, failed. Error details:", ex, errmsg_generic);
                    throw new RuntimeException(ex);
                }
            } else {
                err.log("Couldn't open the config file, for a reason other than „it simply not being there”. Error details:", e, errmsg_generic);
                throw new RuntimeException(e);
            }
        }
        try{
            config_port = Integer.parseInt(config.getProperty("port"));
        } catch (NumberFormatException e) {
            wrn.log("Couldn't read port value \""+config.getProperty(KEY_PORT)+"\" due to the following error: ", e, "\nUsing the default value of "+config_port+" instead.");
        }
        boolean config_safe = Boolean.parseBoolean(config.getProperty(KEY_SAFE));
        out.log("Config obtained!");

        out.log("Connecting to LDAP...");
        LDAPConnection connection = null;
        try (var con = new LDAPConnection(config.getProperty(KEY_HOST), config_port, config.getProperty(KEY_USER), config.getProperty(KEY_PASS))) {
            connection = con;
            out.log("LDAP connection established!");
        } catch (LDAPException e) {
            err.log("Couldn't connect to LDAP. Error details:", e);
            e.printStackTrace();
            if (!config_safe){
                wrn.log("Safe-mode is OFF! That means that CraftLDAP will „fail-open”, ie. let anyone join (by skipping event handler registration) if an error occurs. Said error just occurred, and so your server is currently running without protection! This should only be used for testing or under special circumstances.");
                return;
            }
        }

        out.log("Registering the event handler...");
        ServerPlayConnectionEvents.INIT.register(new JoinEventHandler(connection, config));
        out.log("Event handler registered!");

        out.log("CraftLDAP started!");
    }
}