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

package eu.cloudnetservice.cloudnet.node;

import com.google.common.base.Preconditions;
import eu.cloudnetservice.cloudnet.common.io.FileUtils;
import eu.cloudnetservice.cloudnet.common.log.LogManager;
import eu.cloudnetservice.cloudnet.common.log.Logger;
import eu.cloudnetservice.cloudnet.common.log.defaults.DefaultLogFormatter;
import eu.cloudnetservice.cloudnet.driver.CloudNetDriver;
import eu.cloudnetservice.cloudnet.driver.CloudNetVersion;
import eu.cloudnetservice.cloudnet.driver.DriverEnvironment;
import eu.cloudnetservice.cloudnet.driver.channel.ChannelMessage;
import eu.cloudnetservice.cloudnet.driver.database.Database;
import eu.cloudnetservice.cloudnet.driver.database.DatabaseProvider;
import eu.cloudnetservice.cloudnet.driver.module.DefaultModuleDependencyLoader;
import eu.cloudnetservice.cloudnet.driver.network.NetworkClient;
import eu.cloudnetservice.cloudnet.driver.network.NetworkServer;
import eu.cloudnetservice.cloudnet.driver.network.def.NetworkConstants;
import eu.cloudnetservice.cloudnet.driver.network.http.HttpServer;
import eu.cloudnetservice.cloudnet.driver.network.netty.client.NettyNetworkClient;
import eu.cloudnetservice.cloudnet.driver.network.netty.http.NettyHttpServer;
import eu.cloudnetservice.cloudnet.driver.network.netty.server.NettyNetworkServer;
import eu.cloudnetservice.cloudnet.driver.permission.PermissionManagement;
import eu.cloudnetservice.cloudnet.driver.service.ServiceTemplate;
import eu.cloudnetservice.cloudnet.driver.template.TemplateStorage;
import eu.cloudnetservice.cloudnet.node.cluster.ClusterNodeServerProvider;
import eu.cloudnetservice.cloudnet.node.cluster.DefaultClusterNodeServerProvider;
import eu.cloudnetservice.cloudnet.node.cluster.sync.DataSyncRegistry;
import eu.cloudnetservice.cloudnet.node.cluster.sync.DefaultDataSyncRegistry;
import eu.cloudnetservice.cloudnet.node.command.CommandProvider;
import eu.cloudnetservice.cloudnet.node.command.defaults.DefaultCommandProvider;
import eu.cloudnetservice.cloudnet.node.config.Configuration;
import eu.cloudnetservice.cloudnet.node.config.JsonConfiguration;
import eu.cloudnetservice.cloudnet.node.console.Console;
import eu.cloudnetservice.cloudnet.node.console.log.ColouredLogFormatter;
import eu.cloudnetservice.cloudnet.node.console.util.HeaderReader;
import eu.cloudnetservice.cloudnet.node.database.AbstractDatabaseProvider;
import eu.cloudnetservice.cloudnet.node.database.h2.H2DatabaseProvider;
import eu.cloudnetservice.cloudnet.node.database.xodus.XodusDatabaseProvider;
import eu.cloudnetservice.cloudnet.node.event.CloudNetNodePostInitializationEvent;
import eu.cloudnetservice.cloudnet.node.log.QueuedConsoleLogHandler;
import eu.cloudnetservice.cloudnet.node.module.ModulesHolder;
import eu.cloudnetservice.cloudnet.node.module.NodeModuleProviderHandler;
import eu.cloudnetservice.cloudnet.node.module.updater.ModuleUpdater;
import eu.cloudnetservice.cloudnet.node.module.updater.ModuleUpdaterContext;
import eu.cloudnetservice.cloudnet.node.module.updater.ModuleUpdaterRegistry;
import eu.cloudnetservice.cloudnet.node.module.utils.ModuleJsonReader;
import eu.cloudnetservice.cloudnet.node.network.DefaultNetworkClientChannelHandler;
import eu.cloudnetservice.cloudnet.node.network.DefaultNetworkServerChannelHandler;
import eu.cloudnetservice.cloudnet.node.network.chunk.FileDeployCallbackListener;
import eu.cloudnetservice.cloudnet.node.permission.DefaultDatabasePermissionManagement;
import eu.cloudnetservice.cloudnet.node.permission.DefaultPermissionManagementHandler;
import eu.cloudnetservice.cloudnet.node.permission.NodePermissionManagement;
import eu.cloudnetservice.cloudnet.node.provider.NodeGroupConfigurationProvider;
import eu.cloudnetservice.cloudnet.node.provider.NodeMessenger;
import eu.cloudnetservice.cloudnet.node.provider.NodeNodeInfoProvider;
import eu.cloudnetservice.cloudnet.node.provider.NodeServiceTaskProvider;
import eu.cloudnetservice.cloudnet.node.service.CloudServiceManager;
import eu.cloudnetservice.cloudnet.node.service.defaults.DefaultCloudServiceManager;
import eu.cloudnetservice.cloudnet.node.service.defaults.NodeCloudServiceFactory;
import eu.cloudnetservice.cloudnet.node.setup.DefaultInstallation;
import eu.cloudnetservice.cloudnet.node.template.LocalTemplateStorage;
import eu.cloudnetservice.cloudnet.node.template.install.ServiceVersionProvider;
import eu.cloudnetservice.ext.updater.UpdaterRegistry;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the implementation of the {@link CloudNetDriver} for nodes.
 */
public class CloudNet extends CloudNetDriver {

  private static final Logger LOGGER = LogManager.logger(CloudNet.class);
  private static final boolean DEV_MODE = Boolean.getBoolean("cloudnet.dev");
  private static final Path LAUNCHER_DIR = Path.of(System.getProperty("cloudnet.launcherdir", "launcher"));

  private final Console console;
  private final CommandProvider commandProvider;

  private final HttpServer httpServer;
  private final NetworkClient networkClient;
  private final NetworkServer networkServer;

  private final ServiceVersionProvider serviceVersionProvider;
  private final DefaultClusterNodeServerProvider nodeServerProvider;

  private final ModulesHolder modulesHolder;
  private final UpdaterRegistry<ModuleUpdaterContext, ModulesHolder> moduleUpdaterRegistry;

  private final CloudNetTick mainThread = new CloudNetTick(this);
  private final AtomicBoolean running = new AtomicBoolean(true);
  private final DefaultInstallation installation = new DefaultInstallation();
  private final DataSyncRegistry dataSyncRegistry = new DefaultDataSyncRegistry();
  private final QueuedConsoleLogHandler logHandler = new QueuedConsoleLogHandler();

  private volatile Configuration configuration;
  private volatile AbstractDatabaseProvider databaseProvider;

  protected CloudNet(@NonNull String[] args, @NonNull Console console, @NonNull Logger rootLogger) {
    super(Arrays.asList(args));

    instance(this);

    // add the log handler here to capture all log lines of the startup
    this.logHandler.setFormatter(console.hasColorSupport()
      ? new ColouredLogFormatter()
      : DefaultLogFormatter.END_LINE_SEPARATOR);
    rootLogger.addHandler(this.logHandler);

    this.console = console;
    this.commandProvider = new DefaultCommandProvider(console);

    this.modulesHolder = ModuleJsonReader.read(LAUNCHER_DIR);
    this.moduleUpdaterRegistry = new ModuleUpdaterRegistry();
    this.moduleUpdaterRegistry.registerUpdater(new ModuleUpdater());

    this.serviceVersionProvider = new ServiceVersionProvider(this.eventManager);
    this.cloudNetVersion = CloudNetVersion.fromClassInformation(CloudNet.class.getPackage());

    this.configuration = JsonConfiguration.loadFromFile(this);

    this.nodeServerProvider = new DefaultClusterNodeServerProvider(this);

    this.nodeInfoProvider = new NodeNodeInfoProvider(this);
    this.generalCloudServiceProvider = new DefaultCloudServiceManager(this);

    this.messenger = new NodeMessenger(this);
    this.cloudServiceFactory = new NodeCloudServiceFactory(this);

    this.serviceTaskProvider = new NodeServiceTaskProvider(this);
    this.groupConfigurationProvider = new NodeGroupConfigurationProvider(this);

    // permission management init
    this.permissionManagement(new DefaultDatabasePermissionManagement(this));
    this.permissionManagement().permissionManagementHandler(
      new DefaultPermissionManagementHandler(this.eventManager));

    this.moduleProvider.moduleProviderHandler(new NodeModuleProviderHandler(this));
    this.moduleProvider.moduleDependencyLoader(new DefaultModuleDependencyLoader(LAUNCHER_DIR.resolve("libs")));

    this.networkClient = new NettyNetworkClient(
      DefaultNetworkClientChannelHandler::new,
      this.configuration.clientSSLConfig());
    this.networkServer = new NettyNetworkServer(
      DefaultNetworkServerChannelHandler::new,
      this.configuration.serverSSLConfig());
    this.httpServer = new NettyHttpServer(this.configuration.webSSLConfig());

    // register all rpc handlers associated with methods of this class
    this.rpcProviderFactory.newHandler(Database.class, null).registerToDefaultRegistry();
    this.rpcProviderFactory.newHandler(CloudNetDriver.class, this).registerToDefaultRegistry();
    this.rpcProviderFactory.newHandler(TemplateStorage.class, null).registerToDefaultRegistry();

    this.driverEnvironment = DriverEnvironment.CLOUDNET;
  }

  public static @NonNull CloudNet instance() {
    return (CloudNet) CloudNetDriver.instance();
  }

  @Override
  public void start() throws Exception {
    HeaderReader.readAndPrintHeader(this.console);
    // load the service versions
    this.serviceVersionProvider.loadDefaultVersionTypes();

    // init the default services
    this.servicesRegistry.registerService(
      TemplateStorage.class,
      "local",
      new LocalTemplateStorage(Path.of(System.getProperty("cloudnet.storage.local", "local/templates"))));
    // init the default database providers
    this.servicesRegistry.registerService(
      AbstractDatabaseProvider.class,
      "h2",
      new H2DatabaseProvider(
        System.getProperty("cloudnet.database.h2.path", "local/database/h2")));
    this.servicesRegistry.registerService(
      AbstractDatabaseProvider.class,
      "xodus",
      new XodusDatabaseProvider(
        new File(System.getProperty("cloudnet.database.xodus.path", "local/database/xodus")),
        !this.configuration.clusterConfig().nodes().isEmpty()));

    // initialize the default database provider
    this.databaseProvider(this.servicesRegistry.service(
      AbstractDatabaseProvider.class,
      this.configuration.properties().getString("database_provider", "xodus")));

    // apply all module updates if we're not running in dev mode
    if (!DEV_MODE) {
      this.moduleUpdaterRegistry.runUpdater(this.modulesHolder);
    }
    // load the modules before proceeding for example to allow the database provider init
    this.moduleProvider.loadAll();

    // check if there is a database provider or initialize the default one
    if (this.databaseProvider == null || !this.databaseProvider.init()) {
      this.databaseProvider(this.servicesRegistry.service(AbstractDatabaseProvider.class, "xodus"));
      if (this.databaseProvider == null || !this.databaseProvider.init()) {
        // unable to start without a database
        throw new IllegalStateException("No database provider selected for startup - Unable to proceed");
      }
    }

    // init the permission management
    this.permissionManagement.init();

    // execute the installation setup and load the config things after it
    this.installation.executeFirstStartSetup(this.console);

    // init the local node server
    this.nodeServerProvider.clusterServers(this.configuration.clusterConfig());
    this.nodeServerProvider.selfNode().nodeInfo(this.configuration.identity());
    this.nodeServerProvider.selfNode().publishNodeInfoSnapshotUpdate();

    // network server init
    for (var listener : this.configuration.identity().listeners()) {
      this.networkServer.addListener(listener);
    }
    // http server init
    for (var httpListener : this.configuration.httpListeners()) {
      this.httpServer.addListener(httpListener);
    }
    // network client init
    Set<CompletableFuture<Void>> futures = new HashSet<>(); // all futures of connections
    for (var node : this.nodeServerProvider.nodeServers()) {
      var listeners = node.nodeInfo().listeners();
      // check if there are any listeners
      if (listeners.length > 0) {
        // get a random listener of the node
        var listener = listeners[ThreadLocalRandom.current().nextInt(0, listeners.length)];
        if (this.networkClient.connect(listener)) {
          // register a future that waits for the node to become available
          futures.add(CompletableFuture.runAsync(() -> {
            while (!node.available()) {
              try {
                //noinspection BusyWait
                Thread.sleep(10);
              } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
              }
            }
          }));
        }
      }
    }

    // now we can wait for all nodes to become available (if needed)
    if (!futures.isEmpty()) {
      try {
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(7, TimeUnit.SECONDS);
      } catch (TimeoutException ignored) {
        // auth failed to a node in the cluster - ignore
      }
    }

    // we are now connected to all nodes - request the full cluster data set if the head node is not the current one
    if (!this.nodeServerProvider.headnode().equals(this.nodeServerProvider.selfNode())) {
      ChannelMessage.builder()
        .message("request_initial_cluster_data")
        .channel(NetworkConstants.INTERNAL_MSG_CHANNEL)
        .targetNode(this.nodeServerProvider.headnode().nodeInfo().uniqueId())
        .build()
        .send();
    }

    // start modules
    this.moduleProvider.startAll();
    // enable console command handling
    this.commandProvider.registerDefaultCommands();
    this.commandProvider.registerConsoleHandler(this.console);

    // register listeners & post node startup finish
    this.eventManager.registerListener(new FileDeployCallbackListener());
    this.eventManager.callEvent(new CloudNetNodePostInitializationEvent(this));

    Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "Shutdown Thread"));

    // run the main loop
    this.mainThread.start();
  }

  @Override
  public void stop() {
    // check if we are in the shutdown thread - execute in the shutdown thread if not
    if (!Thread.currentThread().getName().equals("Shutdown Thread")) {
      System.exit(0);
      return;
    }
    // check if the node is still running
    if (this.running.getAndSet(false)) {
      try {
        // stop task execution
        this.scheduler.shutdownNow();
        this.serviceVersionProvider.interruptInstallSteps();

        // close all providers
        this.nodeServerProvider.close();
        this.permissionManagement.close();
        this.databaseProvider.close();
        this.moduleProvider.unloadAll();

        // close all services
        this.cloudServiceProvider().deleteAllCloudServices();

        // close all networking listeners
        this.httpServer.close();
        this.networkClient.close();
        this.networkServer.close();

        // remove temp directory
        FileUtils.delete(FileUtils.TEMP_DIR);

        // close console
        this.console.close();
      } catch (Exception exception) {
        LOGGER.severe("Exception during node shutdown", exception);
      }
    }
  }

  @Override
  public @NonNull String componentName() {
    return this.configuration.identity().uniqueId();
  }

  @Override
  public @NonNull String nodeUniqueId() {
    return this.configuration.identity().uniqueId();
  }

  @Override
  public @NonNull TemplateStorage localTemplateStorage() {
    var localStorage = this.templateStorage(ServiceTemplate.LOCAL_STORAGE);
    if (localStorage == null) {
      // this should never happen
      throw new UnsupportedOperationException("Local template storage is not present");
    }

    return localStorage;
  }

  @Override
  public @Nullable TemplateStorage templateStorage(@NonNull String storage) {
    return this.servicesRegistry.service(TemplateStorage.class, storage);
  }

  @Override
  public @NonNull Collection<TemplateStorage> availableTemplateStorages() {
    return this.servicesRegistry.services(TemplateStorage.class);
  }

  @Override
  public @NonNull AbstractDatabaseProvider databaseProvider() {
    return this.databaseProvider;
  }

  public void databaseProvider(@Nullable AbstractDatabaseProvider databaseProvider) {
    if (databaseProvider != null) {
      try {
        // check if we have an old database provider and close that one if the new database provider is ready and connected
        if (this.databaseProvider != null && databaseProvider.init()) {
          this.databaseProvider.close();
        }
        this.databaseProvider = databaseProvider;
        this.rpcProviderFactory.newHandler(DatabaseProvider.class, databaseProvider).registerToDefaultRegistry();
      } catch (Exception exception) {
        LOGGER.severe("Unable to update current database provider", exception);
      }
    }
  }

  @Override
  public @NonNull NetworkClient networkClient() {
    return this.networkClient;
  }

  @Override
  public @NonNull NodeMessenger messenger() {
    return (NodeMessenger) super.messenger();
  }

  @Override
  public @NonNull
  CloudServiceManager cloudServiceProvider() {
    return (CloudServiceManager) super.cloudServiceProvider();
  }

  @Override
  public @NonNull
  NodePermissionManagement permissionManagement() {
    return (NodePermissionManagement) super.permissionManagement();
  }

  @Override
  public void permissionManagement(@NonNull PermissionManagement management) {
    // nodes can only use node permission managements
    Preconditions.checkArgument(management instanceof NodePermissionManagement);
    super.permissionManagement(management);
    // re-register the handler for the permission management - the call to super.setPermissionManagement will not exit
    // if the permission management is invalid
    this.rpcProviderFactory.newHandler(PermissionManagement.class, management).registerToDefaultRegistry();
  }

  public @NonNull Configuration config() {
    return this.configuration;
  }

  public void config(@NonNull Configuration configuration) {
    this.configuration = configuration;
  }

  public @NonNull
  ClusterNodeServerProvider nodeServerProvider() {
    return this.nodeServerProvider;
  }

  public @NonNull CloudNetTick mainThread() {
    return this.mainThread;
  }

  public @NonNull CommandProvider commandProvider() {
    return this.commandProvider;
  }

  public @NonNull Console console() {
    return this.console;
  }

  public @NonNull ServiceVersionProvider serviceVersionProvider() {
    return this.serviceVersionProvider;
  }

  public @NonNull NetworkServer networkServer() {
    return this.networkServer;
  }

  public @NonNull HttpServer httpServer() {
    return this.httpServer;
  }

  public @NonNull QueuedConsoleLogHandler logHandler() {
    return this.logHandler;
  }

  public @NonNull DefaultInstallation installation() {
    return this.installation;
  }

  public @NonNull DataSyncRegistry dataSyncRegistry() {
    return this.dataSyncRegistry;
  }

  public @NonNull ModulesHolder modulesHolder() {
    return modulesHolder;
  }

  public boolean dev() {
    return DEV_MODE;
  }

  public boolean running() {
    return this.running.get();
  }
}