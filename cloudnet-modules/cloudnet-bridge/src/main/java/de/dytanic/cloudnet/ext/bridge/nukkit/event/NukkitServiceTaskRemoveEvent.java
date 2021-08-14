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

package de.dytanic.cloudnet.ext.bridge.nukkit.event;

import de.dytanic.cloudnet.driver.service.ServiceTask;
import org.bukkit.event.HandlerList;

public class NukkitServiceTaskRemoveEvent extends NukkitCloudNetEvent {

  private static final HandlerList handlers = new HandlerList();

  private final ServiceTask task;

  public NukkitServiceTaskRemoveEvent(ServiceTask task) {
    this.task = task;
  }

  public static HandlerList getHandlers() {
    return NukkitServiceTaskRemoveEvent.handlers;
  }

  public ServiceTask getTask() {
    return task;
  }

}