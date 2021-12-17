/*
 * Copyright 2019-2021 CloudNetService team & contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.dytanic.cloudnet.ext.cloudperms.bukkit;

import de.dytanic.cloudnet.driver.CloudNetDriver;
import de.dytanic.cloudnet.ext.cloudperms.PermissionsUpdateListener;
import de.dytanic.cloudnet.ext.cloudperms.bukkit.listener.BukkitCloudPermissionsPlayerListener;
import de.dytanic.cloudnet.ext.cloudperms.bukkit.vault.VaultSupport;
import de.dytanic.cloudnet.wrapper.Wrapper;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

public final class BukkitCloudPermissionsPlugin extends JavaPlugin {

  @Override
  public void onEnable() {
    this.checkForVault();
    Bukkit.getOnlinePlayers().forEach(this::injectCloudPermissible);

    this.getServer().getPluginManager().registerEvents(new BukkitCloudPermissionsPlayerListener(
      this,
      CloudNetDriver.instance().permissionManagement()
    ), this);

    // register the update listener if the server can update the command tree to the player
    if (BukkitPermissionHelper.canUpdateCommandTree()) {
      CloudNetDriver.instance().eventManager().registerListener(new PermissionsUpdateListener<>(
        runnable -> Bukkit.getScheduler().runTask(this, runnable),
        BukkitPermissionHelper::resendCommandTree,
        Player::getUniqueId,
        Bukkit::getPlayer,
        Bukkit::getOnlinePlayers));
    }
  }

  @Override
  public void onDisable() {
    CloudNetDriver.instance().eventManager().unregisterListeners(this.getClass().getClassLoader());
    Wrapper.instance().unregisterPacketListenersByClassLoader(this.getClass().getClassLoader());
  }

  @Internal
  public void injectCloudPermissible(@NotNull Player player) {
    try {
      BukkitPermissionHelper.injectPlayer(player);
    } catch (Throwable exception) {
      this.getLogger().log(Level.SEVERE, "Exception while injecting cloud permissible", exception);
    }
  }

  private void checkForVault() {
    if (super.getServer().getPluginManager().isPluginEnabled("Vault")) {
      VaultSupport.hook(this, CloudNetDriver.instance().permissionManagement());
    }
  }
}
