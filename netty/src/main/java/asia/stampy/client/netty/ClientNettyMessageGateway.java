/*
 * Copyright (C) 2013 Burton Alexander
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 * 
 */
package asia.stampy.client.netty;

import java.lang.invoke.MethodHandles;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asia.stampy.common.StampyLibrary;
import java.net.URI;
import asia.stampy.common.netty.AbstractStampyNettyMessageGateway;

/**
 * The Class ClientNettyMessageGateway.
 */
@StampyLibrary(libraryName = "stampy-NETTY-client-server-RI")
public class ClientNettyMessageGateway extends AbstractStampyNettyMessageGateway {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  protected NioClientSocketChannelFactory factory;
  private URI uri;

  private Channel client;
  
  public ClientNettyMessageGateway() {
    this.factory = new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool());
  }

  private ClientBootstrap init() {
    ClientBootstrap bootstrap = new ClientBootstrap(factory);
    initializeChannel(bootstrap);

    return bootstrap;
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * asia.stampy.common.gateway.AbstractStampyMessageGateway#closeConnection
   * (asia.stampy.common.gateway.HostPort)
   */
  @Override
  public void closeConnection(URI uri) {
    getHandler().close(uri);
  }

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.gateway.AbstractStampyMessageGateway#connect()
   */
  @Override
  public void connect() throws Exception {
    if (client == null) {
      ClientBootstrap bootstrap = init();
      ChannelFuture cf = bootstrap.connect(new InetSocketAddress(this.uri.getHost(), this.uri.getPort()));
      cf.await();
      if (cf.isSuccess()) {
        client = cf.getChannel();
        log.info("Connected to {}", this.uri);
      } else {
        log.error("Could not connect to {}", this.uri);
      }
    } else if (client.isConnected()) {
      log.warn("Already connected");
    } else {
      log.error("Connector in unrecognized state: isBound {}, isConnected {}, ", client.isBound(), client.isConnected());
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.gateway.AbstractStampyMessageGateway#shutdown()
   */
  @Override
  public void shutdown() throws Exception {
    if (client == null || !client.isConnected()) return;

    ChannelFuture cf = client.close();
    cf.awaitUninterruptibly();
    client = null;
    log.info("Client has been shut down");
  }

  /**
   * Gets the host.
   * 
   * @return the host
   */
  public URI getURI() {
    return uri;
  }

  /**
   * Sets the host.
   * 
   * @param host
   *          the new host
   */
  public void setURI(URI uri) {
    this.uri = uri;
  }

}
