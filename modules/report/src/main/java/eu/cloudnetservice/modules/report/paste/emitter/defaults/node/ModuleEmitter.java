/*
 * Copyright 2019-2022 CloudNetService team & contributors
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

package eu.cloudnetservice.modules.report.paste.emitter.defaults.node;

import eu.cloudnetservice.cloudnet.common.document.gson.JsonDocument;
import eu.cloudnetservice.cloudnet.driver.module.driver.DriverModule;
import eu.cloudnetservice.cloudnet.driver.network.cluster.NetworkClusterNodeInfoSnapshot;
import eu.cloudnetservice.cloudnet.node.CloudNet;
import eu.cloudnetservice.modules.report.paste.emitter.ReportDataEmitter;
import org.jetbrains.annotations.NotNull;

public class ModuleEmitter implements ReportDataEmitter<NetworkClusterNodeInfoSnapshot> {

  @Override
  public void emitData(@NotNull StringBuilder builder, @NotNull NetworkClusterNodeInfoSnapshot context) {
    builder
      .append(" - Loaded Modules ")
      .append(context.modules().size())
      .append(" - \n\n");

    for (var module : CloudNet.instance().moduleProvider().modules()) {
      builder
        .append(" - Module ")
        .append(module.module().name())
        .append(" loaded.\n\n")
        .append(JsonDocument.newDocument(module.moduleConfiguration()).toPrettyJson())
        .append("\n");
      if (!module.moduleConfiguration().storesSensitiveData()) {
        builder.append("Configuration: \n")
          .append(((DriverModule) module.module()).readConfig().toPrettyJson())
          .append("\n\n");
      }
    }
  }
}