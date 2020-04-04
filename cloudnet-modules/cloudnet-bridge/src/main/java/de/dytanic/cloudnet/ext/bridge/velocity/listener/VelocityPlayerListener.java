package de.dytanic.cloudnet.ext.bridge.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import de.dytanic.cloudnet.driver.service.ServiceInfoSnapshot;
import de.dytanic.cloudnet.ext.bridge.BridgeHelper;
import de.dytanic.cloudnet.ext.bridge.player.NetworkServiceInfo;
import de.dytanic.cloudnet.ext.bridge.proxy.BridgeProxyHelper;
import de.dytanic.cloudnet.ext.bridge.velocity.VelocityCloudNetBridgePlugin;
import de.dytanic.cloudnet.ext.bridge.velocity.VelocityCloudNetHelper;
import de.dytanic.cloudnet.wrapper.Wrapper;
import net.kyori.text.TextComponent;

import java.util.concurrent.TimeUnit;

public final class VelocityPlayerListener {

    private VelocityCloudNetBridgePlugin plugin;

    public VelocityPlayerListener(VelocityCloudNetBridgePlugin plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void handle(LoginEvent event) {
        BridgeHelper.sendChannelMessageProxyLoginRequest(VelocityCloudNetHelper.createNetworkConnectionInfo(event.getPlayer()));
    }

    @Subscribe
    public void handle(PostLoginEvent event) {
        BridgeHelper.sendChannelMessageProxyLoginSuccess(VelocityCloudNetHelper.createNetworkConnectionInfo(event.getPlayer()));

        VelocityCloudNetHelper.getProxyServer().getScheduler().buildTask(this.plugin, VelocityCloudNetHelper::updateServiceInfo)
                .delay(50, TimeUnit.MILLISECONDS).schedule();
    }

    @Subscribe
    public void handle(ServerPreConnectEvent event) {
        if (!event.getPlayer().getCurrentServer().isPresent()) {
            VelocityCloudNetHelper.getNextFallback(event.getPlayer())
                    .ifPresent(registeredServer -> event.setResult(ServerPreConnectEvent.ServerResult.allowed(registeredServer)));
        }

        ServiceInfoSnapshot serviceInfoSnapshot = BridgeProxyHelper.getCachedServiceInfoSnapshot(event.getResult().getServer().get().getServerInfo().getName());

        if (serviceInfoSnapshot != null) {
            BridgeHelper.sendChannelMessageProxyServerConnectRequest(VelocityCloudNetHelper.createNetworkConnectionInfo(event.getPlayer()),
                    new NetworkServiceInfo(
                            serviceInfoSnapshot.getServiceId().getEnvironment(),
                            Wrapper.getInstance().getServiceId(),
                            Wrapper.getInstance().getCurrentServiceInfoSnapshot().getConfiguration().getGroups()
                    )
            );

            try {
                Thread.sleep(150);
            } catch (InterruptedException exception) {
                exception.printStackTrace();
            }
        }
    }

    @Subscribe
    public void handle(ServerConnectedEvent event) {
        ServiceInfoSnapshot serviceInfoSnapshot = BridgeProxyHelper.getCachedServiceInfoSnapshot(event.getServer().getServerInfo().getName());

        if (serviceInfoSnapshot != null) {
            BridgeHelper.sendChannelMessageProxyServerSwitch(VelocityCloudNetHelper.createNetworkConnectionInfo(event.getPlayer()),
                    new NetworkServiceInfo(
                            serviceInfoSnapshot.getServiceId().getEnvironment(),
                            Wrapper.getInstance().getServiceId(),
                            Wrapper.getInstance().getCurrentServiceInfoSnapshot().getConfiguration().getGroups()
                    )
            );
        }
    }

    @Subscribe
    public void handle(KickedFromServerEvent event) {
        VelocityCloudNetHelper.getNextFallback(event.getPlayer())
                .ifPresent(registeredServer -> event.setResult(KickedFromServerEvent.RedirectPlayer.create(registeredServer)));
        event.getOriginalReason().ifPresent(component -> event.getPlayer().sendMessage(component));
    }

    @Subscribe
    public void handle(DisconnectEvent event) {
        BridgeHelper.sendChannelMessageProxyDisconnect(VelocityCloudNetHelper.createNetworkConnectionInfo(event.getPlayer()));

        VelocityCloudNetHelper.getProxyServer().getScheduler().buildTask(this.plugin, VelocityCloudNetHelper::updateServiceInfo)
                .delay(50, TimeUnit.MILLISECONDS).schedule();
    }
}