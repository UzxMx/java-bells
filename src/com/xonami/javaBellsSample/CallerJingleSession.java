package com.xonami.javaBellsSample;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.List;
import java.util.logging.Logger;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.SendersEnum;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.Reason;

import org.ice4j.ice.Agent;
import org.ice4j.ice.CandidatePair;
import org.ice4j.ice.Component;
import org.ice4j.ice.IceMediaStream;
import org.ice4j.ice.IceProcessingState;
import org.ice4j.socket.IceSocketWrapper;
import org.jivesoftware.smack.XMPPConnection;

import com.xonami.javaBells.DefaultJingleSession;
import com.xonami.javaBells.IceAgent;
import com.xonami.javaBells.JingleStream;
import com.xonami.javaBells.JingleStreamManager;
import com.xonami.javaBells.JinglePacketHandler;

/**
 * handles jingle packets for the caller.
 * 
 * The actual initiation of the session is made outside this class, but XMPP messages after that
 * are made here.
 * 
 * @author bjorn
 *
 */
public class CallerJingleSession extends DefaultJingleSession implements PropertyChangeListener {
	
	private static final Logger logger = Logger.getLogger(CallerJingleSession.class.getName());
	
	private final IceAgent iceAgent;
	private final JingleStreamManager jingleStreamManager;
	private JingleStream jingleStream;
	
	public CallerJingleSession(IceAgent iceAgent, JingleStreamManager jingleStreamManager, JinglePacketHandler jinglePacketHandler, String peerJid, String sessionId, XMPPConnection connection) {
		super(jinglePacketHandler, sessionId, connection);
		this.iceAgent = iceAgent;
		this.jingleStreamManager = jingleStreamManager;
		this.peerJid = peerJid;
		
		iceAgent.addAgentStateChangeListener( this );
	}
	
	@Override
	protected void closeSession(Reason reason) {
		super.closeSession(reason);
		if( jingleStream != null )
			jingleStream.shutdown();
		iceAgent.freeAgent();
	}

	@Override
	public void handleSessionAccept(JingleIQ jiq) {
		//acknowledge
		if( !checkAndAck(jiq) )
			return;

		state = SessionState.NEGOTIATING_TRANSPORT;
		
		try {
			if( null == jingleStreamManager.parseIncomingAndBuildMedia( jiq, SendersEnum.both ) )
				throw new IOException( "No incoming streams detected." );
			iceAgent.addRemoteCandidates( jiq );
			iceAgent.startConnectivityEstablishment();
		} catch( IOException ioe ) {
			ioe.printStackTrace();
			closeSession(Reason.FAILED_APPLICATION);
		}
	}
	
	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		Agent agent = (Agent) evt.getSource();
		
		System.out.println( "-------------- Caller - Agent Property Change - -----------------" );
		System.out.println( "New State: " + evt.getNewValue() );
		for( String s : iceAgent.getStreamNames() ) {
			System.out.println("Stream          : " + s );
			System.out.println("Local Candidate : " + agent.getSelectedLocalCandidate(s));
			System.out.println("Remote Candidate: " + agent.getSelectedRemoteCandidate(s));
		}
		System.out.println( "-------------- Caller - Agent Property Change - -----------------" );
		
        if(agent.getState() == IceProcessingState.COMPLETED) {
            System.out.println("IceProcessingState.completed");
            List<IceMediaStream> streams = agent.getStreams();

            //////////
            for(IceMediaStream stream : streams)
            {
                String streamName = stream.getName();
                System.out.println( "Pairs selected for stream: " + streamName);
                List<Component> components = stream.getComponents();

                for(Component cmp : components)
                {
                    String cmpName = cmp.getName();
                    System.out.println(cmpName + ": " + cmp.getSelectedPair());
                }
            }

            System.out.println("Printing the completed check lists:");
            for(IceMediaStream stream : streams)
            {
                String streamName = stream.getName();
                System.out.println("Check list for  stream: " + streamName);
                //uncomment for a more verbose output
                System.out.println(stream.getCheckList());
            }
            ////////////
            
            System.out.println("Ready to talk");
            talk(agent);
            
//            try {
//            	for( String s : iceAgent.getStreamNames() ) {
//					jingleStream = jingleStreamManager.startStream(s, iceAgent);
//					jingleStream.quickShow(jingleStreamManager.getDefaultAudioDevice());
//				}
//            } catch( IOException ioe ) {
//            	System.out.println( "An io error occured when negotiating the call: " ) ;
//            	ioe.printStackTrace();
//            	System.exit(1);
//            }
        } else if( agent.getState() == IceProcessingState.FAILED ) {
        	closeSession(Reason.CONNECTIVITY_ERROR);
        }
	}
	
	private void talk(Agent agent) {
		IceMediaStream stream = agent.getStream("audio");
		CandidatePair rtpPair = stream.getComponent(Component.RTP).getSelectedPair();
		DatagramSocket socket = rtpPair.getDatagramSocket();
		byte[] buf = new byte[1024];
		DatagramPacket packet = new DatagramPacket(buf, buf.length);
		try {
			socket.receive(packet);
			logger.info("From: " + packet.getAddress() + " " + packet.getPort());
			logger.info("Message: " + new String(packet.getData()));
		} catch (IOException e) {
			logger.severe("receive failed");
		}
	}
}

