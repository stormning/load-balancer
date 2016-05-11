/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2015, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package org.mobicents.tools.sip.balancer.algorithms;

import static org.junit.Assert.*;

import java.util.Properties;

import javax.sip.ListeningPoint;
import javax.sip.message.Response;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mobicents.tools.sip.balancer.AppServer;
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.mobicents.tools.sip.balancer.UserBasedAlgorithm;
import org.mobicents.tools.sip.balancer.operation.Shootist;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class UserBasedAlgorithmTest {
	
		BalancerRunner balancer;
		int numNodes = 2;
		int numShootist = 20;
		AppServer[] servers = new AppServer[numNodes];
		Shootist [] shootists = new Shootist [numShootist];
		

		@Before
		public void setUp() throws Exception {
			for(int i = 0; i < shootists.length; i++)
			{
				shootists[i] = new Shootist("udp",5060,5033+i);
				shootists[i].callerSendsBye = true;
			}
			balancer = new BalancerRunner();
			Properties properties = new Properties();
			properties.setProperty("javax.sip.STACK_NAME", "SipBalancerForwarder");
			properties.setProperty("javax.sip.AUTOMATIC_DIALOG_SUPPORT", "off");
			// You need 16 for logging traces. 32 for debug + traces.
			// Your code will limp at 32 but it is best for debugging.
			properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
			properties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "logs/sipbalancerforwarderdebug.txt");
			properties.setProperty("gov.nist.javax.sip.SERVER_LOG", "logs/sipbalancerforwarder.xml");
			properties.setProperty("gov.nist.javax.sip.THREAD_POOL_SIZE", "2");
			properties.setProperty("gov.nist.javax.sip.REENTRANT_LISTENER", "true");
			properties.setProperty("gov.nist.javax.sip.CANCEL_CLIENT_TRANSACTION_CHECKED", "false");
			properties.setProperty("algorithmClass", UserBasedAlgorithm.class.getName());
			properties.setProperty("host", "127.0.0.1");
			properties.setProperty("internalPort", "5065");
			properties.setProperty("externalPort", "5060");
			properties.setProperty("sipHeaderAffinityKey", "To");
			balancer.start(properties);
			
			
			for(int q=0;q<servers.length;q++) 
			{
				servers[q] = new AppServer("node" + q,4060+q , "127.0.0.1", 2000, 5060, 5065, "0", ListeningPoint.UDP);
				servers[q].start();
			}
			Thread.sleep(5000);
		}

		@After
		public void tearDown() throws Exception {
			for(Shootist s :shootists)
				s.stop();
			
			for(AppServer as: servers) {
				as.stop();
			}
			balancer.stop();
		}
		
		@Test
		public void testInviteByeLandOnDifferentNodes() throws Exception {
			for(int i = 0; i < shootists.length; i++)
			{
				shootists[i].sendInitialInvite();
				Thread.sleep(6000);
				shootists[i].sendBye();
				Thread.sleep(2000);
			}
			
			assertNotEquals(servers[0].getTestSipListener().isInviteReceived(),servers[1].getTestSipListener().isInviteReceived());
			assertNotEquals(servers[0].getTestSipListener().isAckReceived(),servers[1].getTestSipListener().isAckReceived());
			assertNotEquals(servers[0].getTestSipListener().getByeReceived(),servers[1].getTestSipListener().getByeReceived());
			
			for(Shootist s :shootists)
			{
				boolean wasRinging = false;
				boolean wasOk = false;
				for(Response res : s.responses)
				{
					if(res.getStatusCode() != Response.RINGING)
						wasRinging = true;
					if(res.getStatusCode() != Response.OK)
						wasOk = true;
				}
				assertTrue(wasOk);
				assertTrue(wasRinging);
			}
		}
}
