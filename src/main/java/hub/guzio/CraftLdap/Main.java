package hub.guzio.CraftLdap;

import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.event.Level;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

public class Main implements ModInitializer {

    private static final String logAs = "CraftLDAP";

    public static final Logger out = new Logger(Level.INFO, logAs);
    public static final Logger err = new Logger(Level.ERROR, logAs);
    public static final Logger wrn = new Logger(Level.WARN, logAs);
    public static final Logger dbg = new Logger(Level.DEBUG, logAs);

    public static final String KEY_HOST = "host";
    public static final String KEY_PORT = "port";
    public static final String KEY_USER = "bind-dn";
    public static final String KEY_PASS = "bind-pass";
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
        var errmsg_generic = "\nCannot operate in this broken-config state; crashing the server instead.";
        var config = new Properties();
        try {
            config.load(new FileInputStream(props.toFile()));
        } catch (IOException e) {
            if (e instanceof FileNotFoundException){
                wrn.log("A new config file needs to be created because the previous one seems to not exist. Error details:\n{}", e);
                config.setProperty(KEY_HOST, "ldap");
                config.setProperty(KEY_PORT, ""+config_port); //Cheesy „parsing” trick
                config.setProperty(KEY_USER, "cn=[some user that can read the index],ou=people,dc=example,dc=com");
                config.setProperty(KEY_PASS, "[the password of that user]");
                config.setProperty(KEY_GROUP, "ou=people,dc=example,dc=com");
                config.setProperty(KEY_FILTER, "(&(objectclass=person)(memberOf=cn=[some group],ou=groups,dc=example,dc=com))");
                config.setProperty(KEY_WEBSITE, "https://example.com/");
                config.setProperty(KEY_LDAPNAME, "uid");
                config.setProperty(KEY_MCUUID, "mcuuid");
                config.setProperty(KEY_ERRUNKNOWN, "Something went wrong with CraftLDAP auth. THIS IS NOT YOUR FAULT, please report this error to the server admin instead!!!");
                config.setProperty(KEY_ERRAUTH, "You're not authorized to play on this server.");

                try {
                    config.store(new FileWriter(props.toFile()), "Config file for CraftLDAP. Refer to the Modrinth-page or GitHub README for documentation.");
                } catch (IOException ex) {
                    err.log("Couldn't open the config file due to it not yet existing. Error details:\n{}\nSubsequently, attempted to create a new one. That, too however, failed. Error details:\n{}"+errmsg_generic, e, ex);
                    throw new RuntimeException(ex);
                }
            } else {
                err.log("Couldn't open the config file, for a reason other than „it simply not being there”. Error details:\n{}"+errmsg_generic, e);
                throw new RuntimeException(e);
            }
        }
        try{
            config_port = Integer.parseInt(config.getProperty("port"));
        } catch (NumberFormatException e) {
            wrn.log("Couldn't read port value \"{}\" due to the following error:\n{}\nUsing the default value of {} instead.", config.getProperty(KEY_PORT), e, config_port);
        }
        out.log("Config obtained!");

        out.log("Connecting to LDAP...");
        LDAPConnection connection = null;
        try (var con = new LDAPConnection(config.getProperty(KEY_HOST), config_port, config.getProperty(KEY_USER), config.getProperty(KEY_PASS))) {
            connection = con;
            out.log("LDAP connection established!");
        } catch (LDAPException e) {
            wrn.log("Couldn't connect to LDAP. Error details:\n{}\nDue to this, the very first player login will cause a connection re-attempt due to it now being in a „dropped-out” state.", e);
        }

        out.log("Registering the event handler...");
        ServerPlayConnectionEvents.INIT.register(new JoinEventHandler(connection, config));
        out.log("Event handler registered!");

        out.log("CraftLDAP started!");
    }
}