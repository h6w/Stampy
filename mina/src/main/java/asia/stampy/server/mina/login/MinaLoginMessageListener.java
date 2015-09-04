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
package asia.stampy.server.mina.login;

import java.lang.invoke.MethodHandles;
import java.net.InetSocketAddress;

import javax.annotation.Resource;

import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asia.stampy.common.StampyLibrary;
import java.net.URI;
import asia.stampy.common.gateway.MessageListenerHaltException;
import asia.stampy.common.gateway.StampyMessageListener;
import asia.stampy.common.mina.MinaServiceAdapter;
import asia.stampy.server.listener.login.AbstractLoginMessageListener;
import asia.stampy.server.listener.login.StampyLoginHandler;
import asia.stampy.server.listener.login.TerminateSessionException;
import asia.stampy.server.mina.ServerMinaMessageGateway;

/**
 * This class enforces login functionality via the implementation of a
 * {@link StampyLoginHandler}. Should the login handler throw a
 * {@link TerminateSessionException} this class will send an error to the
 * client, close the session and throw a {@link MessageListenerHaltException} to
 * prevent downstream processing of the message by the remaining
 * {@link StampyMessageListener}s.
 */
@Resource
@StampyLibrary(libraryName = "stampy-MINA-client-server-RI")
public class MinaLoginMessageListener extends AbstractLoginMessageListener<ServerMinaMessageGateway> {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  protected void ensureCleanup() {
    getGateway().addServiceListener(new MinaServiceAdapter() {

      @Override
      public void sessionDestroyed(IoSession session) throws Exception {
        URI uri = new URI("stomp","",((InetSocketAddress) session.getRemoteAddress()).getHostName(),((InetSocketAddress) session.getRemoteAddress()).getPort(),"","","");
        if (loggedInConnections.contains(uri)) {
          log.debug("{} session terminated before DISCONNECT message received, cleaning up", uri);
          loggedInConnections.remove(uri);
        }
      }
    });
  }

}
