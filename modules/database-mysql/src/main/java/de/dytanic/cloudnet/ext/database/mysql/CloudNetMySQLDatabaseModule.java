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

package de.dytanic.cloudnet.ext.database.mysql;

import com.google.gson.reflect.TypeToken;
import de.dytanic.cloudnet.common.document.gson.JsonDocument;
import de.dytanic.cloudnet.database.AbstractDatabaseProvider;
import de.dytanic.cloudnet.driver.module.ModuleLifeCycle;
import de.dytanic.cloudnet.driver.module.ModuleTask;
import de.dytanic.cloudnet.driver.module.driver.DriverModule;
import de.dytanic.cloudnet.driver.network.HostAndPort;
import de.dytanic.cloudnet.ext.database.mysql.util.MySQLConnectionEndpoint;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

public final class CloudNetMySQLDatabaseModule extends DriverModule {

  public static final Type TYPE = TypeToken.getParameterized(List.class, MySQLConnectionEndpoint.class).getType();

  private static CloudNetMySQLDatabaseModule instance;

  public static CloudNetMySQLDatabaseModule getInstance() {
    return CloudNetMySQLDatabaseModule.instance;
  }

  @ModuleTask(order = 127, event = ModuleLifeCycle.LOADED)
  public void init() {
    instance = this;
  }

  @ModuleTask(order = 126, event = ModuleLifeCycle.LOADED)
  public void initConfig() {

    JsonDocument configuration = this.getConfig();

    configuration.getString("database", "mysql");
    configuration.get("addresses", TYPE, Collections.singletonList(
      new MySQLConnectionEndpoint(false, "CloudNet", new HostAndPort("127.0.0.1", 3306))
    ));

    configuration.getString("username", "root");
    configuration.getString("password", "root");

    int connectionMaxPoolSize = 20;

    if (configuration.contains("connectionPoolSize")) {
      connectionMaxPoolSize = this.getConfig().getInt("connectionPoolSize");
      configuration.remove("connectionPoolSize");
    }

    configuration.getInt("connectionMaxPoolSize", connectionMaxPoolSize);
    configuration.getInt("connectionMinPoolSize", 10);
    configuration.getInt("connectionTimeout", 5000);
    configuration.getInt("validationTimeout", 5000);

    this.saveConfig();
  }

  @ModuleTask(order = 125, event = ModuleLifeCycle.LOADED)
  public void registerDatabaseProvider() {
    this.getRegistry().registerService(AbstractDatabaseProvider.class, this.getConfig().getString("database"),
      new MySQLDatabaseProvider(this.getConfig(), null));
  }

  @ModuleTask(order = 127, event = ModuleLifeCycle.STOPPED)
  public void unregisterDatabaseProvider() {
    this.getRegistry().unregisterService(AbstractDatabaseProvider.class, this.getConfig().getString("database"));
  }
}
