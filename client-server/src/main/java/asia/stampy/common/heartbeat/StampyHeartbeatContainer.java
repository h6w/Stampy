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
package asia.stampy.common.heartbeat;

import asia.stampy.common.gateway.AbstractStampyMessageGateway;
import java.net.URI;

public interface StampyHeartbeatContainer {

  /** The Constant HB1. */
  public static final byte[] HB1 = new byte[]{'\n'};
  /** The Constant HB2. */
  public static final byte[] HB2 = new byte[]{'\r', '\n'};

  /**
   * Starts a heartbeat for the specified host & port.
   * @param uri
   * @param gateway
   * @param timeMillis
   */
  public abstract void start(URI uri, AbstractStampyMessageGateway gateway, int timeMillis);

  /**
   * Stops heartbeats to the specified {@link HostPort}.
   * 
   * @param uri
   *          the host port
   */
  public abstract void stop(URI uri);

  /**
   * Removes the {@link PaceMaker} specified by {@link HostPort}.
   * 
   * @param uri
   *          the host port
   */
  public abstract void remove(URI uri);

  /**
   * Resets the {@link PaceMaker} for the specified {@link HostPort}, preventing
   * a heartbeat from being sent.
   * 
   * @param uri
   *          the host port
   */
  public abstract void reset(URI uri);

}