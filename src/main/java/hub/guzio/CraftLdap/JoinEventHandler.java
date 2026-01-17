package hub.guzio.CraftLdap;

import com.unboundid.ldap.sdk.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

public class JoinEventHandler implements ServerPlayConnectionEvents.Init {

    private final LDAPConnection connection;
    private final Properties config;

    JoinEventHandler(LDAPConnection connection, Properties config){
        this.connection = connection;
        this.config = config;
    }

    @Override
    public void onPlayInit(@NotNull ServerPlayNetworkHandler event, @NonNull MinecraftServer server) {
        DisconnectionInfo errmsg_ldap = new DisconnectionInfo(Text.literal(config.getProperty(Main.KEY_ERRUNKNOWN)));
        try {
            errmsg_ldap = new DisconnectionInfo(errmsg_ldap.reason(), Optional.empty(), Optional.of(new URI(config.getProperty(Main.KEY_WEBSITE))));
        } catch (URISyntaxException e) {
            Main.wrn.log("Couldn't parse bugreport URI, will not provide it at all. Error details:", e);
        }

        if (connection == null){
            Main.wrn.log(event.player.getName().getString() + " attempted to join, but CraftLDAP couldn't connect to LDAP and it's set to „fail-closed”. Their join attempt will get automatically rejected.");
            event.disconnect(errmsg_ldap);
            return;
        }

        try {
            var results = filterEntries(event.getPlayer().getUuid(), connection.search(config.getProperty(Main.KEY_GROUP), SearchScope.SUB, config.getProperty(Main.KEY_FILTER)).getSearchEntries());
            if (results.length < 1){
                Main.out.log(event.player.getName().getString() + " attempted to join, but was not authenticated with LDAP.");
                event.disconnect(new DisconnectionInfo(Text.literal(config.getProperty(Main.KEY_ERRAUTH))));
            }
            else {
                Main.out.log(event.player.getName().getString() + " joined as the following LDAP user(s): [ \""+String.join("\", \"", results)+"\" ].");
            }
        }
        catch (LDAPException e) {
            Main.err.log(event.player.getName().getString() + " attempted to join, but CraftLDAP couldn't determine whether they're authorized to do so (so their join attempt was rejected), due to the following error:", e);
            event.disconnect(errmsg_ldap);
        }
    }

    public String[] filterEntries(UUID player, List<SearchResultEntry> results) throws LDAPException{
        var outputRaw = new String[results.size()];
        int index = 0;

        try{
            for (var result : results) {
                if (result.getAttribute(config.getProperty(Main.KEY_MCUUID)).getValue().equals(player.toString())){
                    outputRaw[index] = result.getAttribute(config.getProperty(Main.KEY_LDAPNAME)).getValue();
                    index++;
                }
            }
        }
        catch (NullPointerException e){
            throw new LDAPException(ResultCode.NO_SUCH_ATTRIBUTE, e);
        }

        var output = new String[index];
        while (index > 0){
            output[index-1] = outputRaw[index-1];
            index--;
        }
        return output;
    }
}