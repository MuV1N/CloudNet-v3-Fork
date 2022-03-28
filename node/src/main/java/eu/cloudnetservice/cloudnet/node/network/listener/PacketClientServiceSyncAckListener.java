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

package eu.cloudnetservice.cloudnet.node.network.listener;

import eu.cloudnetservice.cloudnet.driver.network.NetworkChannel;
import eu.cloudnetservice.cloudnet.driver.network.cluster.NetworkClusterNodeInfoSnapshot;
import eu.cloudnetservice.cloudnet.driver.network.def.NetworkConstants;
import eu.cloudnetservice.cloudnet.driver.network.protocol.Packet;
import eu.cloudnetservice.cloudnet.driver.network.protocol.PacketListener;
import eu.cloudnetservice.cloudnet.node.Node;
import eu.cloudnetservice.cloudnet.node.cluster.NodeServerState;
import eu.cloudnetservice.cloudnet.node.cluster.util.QueuedNetworkChannel;
import lombok.NonNull;

public final class PacketClientServiceSyncAckListener implements PacketListener {

  @Override
  public void handle(@NonNull NetworkChannel channel, @NonNull Packet packet) throws Exception {
    // read the cluster node snapshot
    var snapshot = packet.content().readObject(NetworkClusterNodeInfoSnapshot.class);
    var syncData = packet.content().readDataBuf();
    // select the node server and validate that it is in the right state for the packet
    var server = Node.instance().nodeServerProvider().node(snapshot.node().uniqueId());
    if (server != null && server.state() == NodeServerState.SYNCING) {
      // remove this listener
      channel.packetRegistry().removeListeners(NetworkConstants.INTERNAL_SERVICE_SYNC_ACK_CHANNEL);
      // sync the data between the nodes
      Node.instance().dataSyncRegistry().handle(syncData, syncData.readBoolean());
      if (server.channel() instanceof QueuedNetworkChannel queuedChannel) {
        queuedChannel.drainPacketQueue(channel);
      }

      // close the old channel
      // little hack to prevent some disconnect handling firring in the channel if the state was not set before
      server.state(NodeServerState.DISCONNECTED);
      server.channel().close();
      // mark the node as ready
      server.channel(channel);
      server.updateNodeInfoSnapshot(snapshot);
      server.state(NodeServerState.READY);
      // re-select the head node
      Node.instance().nodeServerProvider().selectHeadNode();
    }
  }
}
