/*
 * class Focus
 * 
 * Version 0.1
 *
 * Date 12.3.2012
 * 
 * Copyright notice
 * 
 * This code is Open Source 
 * 
 * Author: Michael Niemand
 * 
 * 
 * 
 */

package de.fhffm.research.mediaserver;

import gov.nist.javax.sdp.fields.AttributeField;
import gov.nist.javax.sdp.parser.SDPParser;

import java.net.InetAddress;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import java.util.TooManyListenersException;
import javax.sip.*;
import javax.sip.address.*;
import javax.sip.header.*;
import javax.sip.message.*;

import javax.sdp.*;

// Apache Common Configuration
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.log4j.Logger;

// Regular Expressions
import java.util.regex.Pattern;
import java.util.regex.Matcher;


// TODO: remove this in the end
@SuppressWarnings("unused")

public class Focus implements SipListener 	 {

	private static final String PROPERTIES_FILE_NAME = "MediaServer.properties";

	private int maxParticipants;
	private int counter = 0;

	// TODO:Config File!!!!
	private String SIP_URI;
	private static AddressFactory addressFactory;
	private static MessageFactory messageFactory;	
	private static HeaderFactory headerFactory;
	private static SipStack sipStack;
	private static String myAddress;
	private static int myPort;

	protected ServerTransaction inviteTid;
	private Response okResponse;
	private Request inviteRequest;
	private Response busyResponse;

	private Dialog dialog;

	public static final boolean callerSendsBye = true;

	private SipFactory sipFactory;
	private SipProvider sipProvider;

	private SdpFactory sdpFactory;

	private MessageProcessor messageProcessor;
	private String username;

	private static Logger logger = Logger.getLogger(Focus.class) ;

	public interface MessageProcessor {
		public void processMessage(String sender, String message);
		public void processError(String errorMessage);
		public void processInfo(String infoMessage);
	}	

	//	public void init(AudioMixer mixer, VideoBridge bridge){
	public void init(){

		// Load data from the config file
		PropertiesConfiguration config = new PropertiesConfiguration();
		try {
			config.load(PROPERTIES_FILE_NAME);
		} catch (ConfigurationException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		//SIP_URI=config.getString("SIP_URI");
		myAddress = config.getString("IP_ADDR");
		myPort = config.getInt("SIP_PORT");
		maxParticipants = config.getInt("MAX_PARTICIPANTS");

		//	audioMixer = mixer;
		//	videoBridge = bridge;

		SipFactory sipFactory = null;
		sipStack = null;
		sipFactory = SipFactory.getInstance();
		sipFactory.setPathName("gov.nist");
		Properties properties = new Properties();
		properties.setProperty("javax.sip.STACK_NAME", "Focus");
		// You need 16 for logging traces. 32 for debug + traces.
		// Your code will limp at 32 but it is best for debugging.
		properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "ERROR");
		properties.setProperty("gov.nist.javax.sip.DEBUG_LOG",	"mediaserverfocusdebug.txt");
		properties.setProperty("gov.nist.javax.sip.SERVER_LOG",	"mediaserverfocuslog.txt");

		try {
			// Create SipStack object
			sipStack = sipFactory.createSipStack(properties);
			//System.out.println("sipStack = " + sipStack);
		} catch (PeerUnavailableException e) {
			// could not find
			// gov.nist.jain.protocol.ip.sip.SipStackImpl
			// in the classpath
			e.printStackTrace();
			System.err.println(e.getMessage());
			if (e.getCause() != null)
				e.getCause().printStackTrace();
			System.exit(0);
		}

		try {
			headerFactory = sipFactory.createHeaderFactory();
			addressFactory = sipFactory.createAddressFactory();
			messageFactory = sipFactory.createMessageFactory();
			ListeningPoint lp = sipStack.createListeningPoint(myAddress, myPort, "udp");

			Focus listener = this;

			SipProvider sipProvider = sipStack.createSipProvider(lp);
			//System.out.println("udp provider " + sipProvider);
			sipProvider.addSipListener(listener);

		} catch (Exception ex) {
			System.out.println(ex.getMessage());
			ex.printStackTrace();
			usage();
		}

		sdpFactory = SdpFactory.getInstance();

	}

	public void processRequest(RequestEvent requestEvent) {
		Request request = requestEvent.getRequest();
		ServerTransaction serverTransactionId = requestEvent.getServerTransaction();
		System.out.println("TID: " + serverTransactionId);
		System.out.println("METHOD: "+request.getMethod() + "\r\n");
		if (request.getMethod().equals(Request.INVITE)) {
			processInvite(requestEvent, serverTransactionId);
		} else if (request.getMethod().equals(Request.ACK)) {
			processAck(requestEvent, serverTransactionId);
		} else if (request.getMethod().equals(Request.BYE)) {
			processBye(requestEvent, serverTransactionId);
		} else if (request.getMethod().equals(Request.CANCEL)) {
			processCancel(requestEvent, serverTransactionId);

		} else {

			try {
				SipProvider prov = (SipProvider) requestEvent.getSource();

				if (serverTransactionId == null) {
					logger.info("Null TID.");
					serverTransactionId = prov.getNewServerTransaction(request);
				}
				Response response = messageFactory.createResponse(405, request );
				Header header = headerFactory.createAllowHeader("INVITE,BYE,ACK,CANCEL");

				response.addHeader(header);

				serverTransactionId.sendResponse(response);

				//				// send one back
				//				Request refer = requestEvent.getDialog().createRequest("REFER");
				//				requestEvent.getDialog().sendRequest( prov.getNewClientTransaction(refer) );

			} catch (SipException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvalidArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	protected static final String usageString = "MediaServer Focus \n>>>> Is your class path set to the root?\n>>>> Is another MediaServer already running?";

	private static void usage() {
		System.out.println(usageString);
		System.exit(0);
	}

	public void processResponse(ResponseEvent responseEvent) {
	}
	/**
	 * Process the <i>ACK</i> request. Send the <i>BYE</i> and complete the call flow.
	 */
	public void processAck(RequestEvent requestEvent, ServerTransaction serverTransaction) {

		//TODO start to send Data to the client just here!!


		try {
			System.out.println("Focus: got an ACK! ");
			System.out.println("Dialog State = " + dialog.getState());
			SipProvider provider = (SipProvider) requestEvent.getSource();
			if (!callerSendsBye) {
				Request byeRequest = dialog.createRequest(Request.BYE);
				ClientTransaction ct = provider
						.getNewClientTransaction(byeRequest);
				dialog.sendRequest(ct);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}

	}
	/**
	 * Process the <i>INVITE</i> request.
	 * 
	 */
	public void processInvite(RequestEvent requestEvent, ServerTransaction serverTransaction) {

		SessionDescription sessionDescription;

		SipProvider sipProvider = (SipProvider) requestEvent.getSource();
		Request request = requestEvent.getRequest();

		try {
			System.out.println("Focus: got an Invite; sending Trying");

			Response response = messageFactory.createResponse(Response.TRYING, request);
			ServerTransaction st = requestEvent.getServerTransaction();

			if (st == null) {
				st = sipProvider.getNewServerTransaction(request);
			}
			dialog = st.getDialog();
			st.sendResponse(response);

			//get a room ...
			String roomName = null;
			
			roomName = getRoomNamefromToHeader(request.getHeader(ToHeader.NAME));
			
			//is that room available?;
			ConferenceRoom room = MediaServer.getRoomByName(roomName);

			//Ask particular room for free slots, if available send OK, if not send BUSY
			if (room.slotsAvailable()){
				// 
				//Save the SDP content in a String
				String sdpRequestContent = new String(request.getRawContent());  

				//Use the static method of SdpFactory to parse the content
				SessionDescription sdpRequestSessionDescription =  sdpFactory.createSessionDescription(sdpRequestContent);

				// ports 
				int audioRemotePort = getMediaDescriptionPort(sdpRequestSessionDescription, "audio"); 
				int videoRemotePort = getMediaDescriptionPort(sdpRequestSessionDescription, "video"); 

				// ip
				String remoteIp = sdpRequestSessionDescription.getConnection().getAddress(); 

				System.out.println("Focus: adding participant to room \"" 
									+ roomName 
									+ "\" / audiomixer @ remote socket " 
									+ remoteIp 
									+ ":" 
									+ audioRemotePort);

				System.out.println("Focus: adding participant to room \"" 
									+ roomName 
									+ "\" / videobridge @ remote socket " 
									+ remoteIp 
									+ ":"
									+ videoRemotePort);		
				
				
				// Wait for GStreamer to be ready
				while(!(room.isVideoReady())){
					Thread.sleep(5);
				};
				
				SIP_URI = roomName + " <sip:" + roomName + "@" + myAddress + ">";
				
				room.addParticipant(remoteIp, 
						audioRemotePort, 
						videoRemotePort, 
						request.getHeader(CallIdHeader.NAME).toString(), 
						getParticipantNameByFromHeader(request.getHeader(FromHeader.NAME)));

				String sdpResponseContent = createSdpResponse();

				byte[] sdpResponseContentBytes = sdpResponseContent.getBytes(); 

				// C-TYPE
				ContentTypeHeader contentTypeHeader = null; 
				contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");

				this.okResponse = messageFactory.createResponse(Response.OK, request);

				Address address = addressFactory.createAddress(SIP_URI);

				// ALLOW
				AllowHeader allowHeader = headerFactory.createAllowHeader("INVITE,BYE,ACK,CANCEL");
				okResponse.addHeader(allowHeader);

				// CONTACT
				ContactHeader contactHeader = headerFactory.createContactHeader(address);
				System.out.println("contactHeader: " + contactHeader);
				okResponse.addHeader(contactHeader);

				// TO
				ToHeader toHeader = (ToHeader) okResponse.getHeader(ToHeader.NAME);
				
				// TODO set Tag with a constant 
				toHeader.setTag("Conference-Server"); // Application is supposed to set.

				okResponse.setContent(sdpResponseContentBytes, contentTypeHeader);		

				this.inviteTid = st;
				this.inviteRequest = request;
				this.sendInviteOK();

			} else {

				this.okResponse = messageFactory.createResponse(Response.BUSY_HERE, request);
				ToHeader toHeader = (ToHeader) okResponse.getHeader(ToHeader.NAME);
				toHeader.setTag("Conference-Server"); // Application is supposed to set.

				this.inviteTid = st;

				this.inviteRequest = request;	

				this.sendInviteOK();
			}
		} 
		catch (Exception ex) {
			ex.printStackTrace();
			System.exit(0);
		}
	}

	private void sendInviteOK() {

		try {
			if (inviteTid.getState() != TransactionState.COMPLETED) {
				System.out.println("Focus: Dialog state before Response: " + inviteTid.getDialog().getState());
				inviteTid.sendResponse(okResponse);
				System.out.println("Focus: Dialog state after Response: " + inviteTid.getDialog().getState());
			}
		} catch (SipException ex) {
			ex.printStackTrace();
		} catch (InvalidArgumentException ex) {
			ex.printStackTrace();
		}
	}
	/**
	 * Process the <i>BYE</i> request.
	 */
	public void processBye(RequestEvent requestEvent, ServerTransaction serverTransactionId) {
		SipProvider sipProvider = (SipProvider) requestEvent.getSource();
		Request request = requestEvent.getRequest();
		Dialog dialog = requestEvent.getDialog();
		System.out.println("local party = " + dialog.getLocalParty());
		try {
			System.out.println("Focus: got a bye; sending OK...");
			Response response = messageFactory.createResponse(200, request);

			//get a room ...
			String roomName = null;

			roomName = getRoomNamefromToHeader(request.getHeader(ToHeader.NAME));
			
			System.out.println(roomName);

			//is that room available?
			ConferenceRoom room = MediaServer.getRoomByName(roomName);
			
			// Remove the participants from Audiomixer and Videobridge 
			room.getAudioMixer().removeParticipant(request.getHeader(CallIdHeader.NAME).toString());
			room.getVideoBridge().removeParticipant(request.getHeader(CallIdHeader.NAME).toString());
		
			
			serverTransactionId.sendResponse(response);
			System.out.println("Dialog State is " + serverTransactionId.getDialog().getState());

		} catch (Exception ex) {
			ex.printStackTrace();
			System.exit(0);
		}
	}

	public void processCancel(RequestEvent requestEvent, ServerTransaction serverTransactionId) {
		SipProvider sipProvider = (SipProvider) requestEvent.getSource();
		Request request = requestEvent.getRequest();
		try {
			System.out.println("Focus:  got a cancel.");
			if (serverTransactionId == null) {
				System.out.println("Focus:  null tid.");
				return;
			}
			Response response = messageFactory.createResponse(200, request);
			serverTransactionId.sendResponse(response);
			if (dialog.getState() != DialogState.CONFIRMED) {
				response = messageFactory.createResponse(Response.REQUEST_TERMINATED, inviteRequest);
				inviteTid.sendResponse(response);
			}

		} catch (Exception ex) {
			ex.printStackTrace();
			System.exit(0);

		}
	}

	public void processTimeout(javax.sip.TimeoutEvent timeoutEvent) {
		Transaction transaction;
		if (timeoutEvent.isServerTransaction()) {
			transaction = timeoutEvent.getServerTransaction();
		} else {
			transaction = timeoutEvent.getClientTransaction();
		}
		System.out.println("state = " + transaction.getState());
		System.out.println("dialog = " + transaction.getDialog());
		System.out.println("dialogState = "	+ transaction.getDialog().getState());
		System.out.println("Transaction Time out");

	}

	private String getParticipantNameByFromHeader(Header header) {
		String userName = null;
		String displayName = null;

		
		FromHeader fromHeaderRequest = (FromHeader)header;
		SipURI fromUri = (SipURI)fromHeaderRequest.getAddress().getURI();
		
		displayName = fromHeaderRequest.getAddress().getDisplayName();
		userName = fromUri.getUser();		
		
		String returnName = userName;
		
		if (!(displayName==null)){
			returnName = displayName;
		}
		
		return returnName;	
	}
	
	private String getRoomNamefromToHeader(Header header) {

		ToHeader toHeaderRequest = (ToHeader)header;
		SipURI confUri = (SipURI)toHeaderRequest.getAddress().getURI();
		return confUri.getUser();	
	}

	@Override
	public void processDialogTerminated(DialogTerminatedEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void processIOException(IOExceptionEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void processTransactionTerminated(TransactionTerminatedEvent arg0) {
		// TODO Auto-generated method stub

	}
	//TODO: Warnings!?
	@SuppressWarnings("unchecked")
	private int getMediaDescriptionPort(SessionDescription sd, String mediaType){
		int i=0;
		try {
			Vector<MediaDescription> mds = sd.getMediaDescriptions(false);
			for (MediaDescription m : mds) {
				if (m.getMedia().getMediaType().toLowerCase().equalsIgnoreCase(mediaType)){
					System.out.println("MediaType: " + m.getMedia().getMediaType() + " @ port " + m.getMedia().getMediaPort());
					i = m.getMedia().getMediaPort();
				}
			}
		} catch (SdpException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return i ; 
	}

	private String createSdpResponse() {

		// Load data from the config file
		PropertiesConfiguration config = new PropertiesConfiguration();
		try {
			config.load(PROPERTIES_FILE_NAME);
		} catch (ConfigurationException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		String ip = config.getString("IP_ADDR");
		String text = config.getString("WELCOMETEXT");
		int a = config.getInt("AUDIO_RECV_PORT"); 
		int v = config.getInt("VIDEO_RECV_PORT"); 		

		// SDP Response
		// TODO: replace 2345 with "real" number 
		// TODO: get codecs from config file
		String sdpData = "v=0\r\n"
				+ "o=- 123456 0 IN IP4 " + ip +  "\r\n" 
				+ "s=" + text + "\r\n"
				+ "c=IN IP4 " + ip + "\r\n"
				+ "t=0 0\r\n" 
				+ "m=audio " + a + " RTP/AVP 0\r\n"
				+ "a=rtpmap:0 PCMU/8000\r\n"
				+ "m=video " + v + " RTP/AVP 123\r\n"
				+ "a=rtpmap:123 H264/90000\r\n";
//				+ "a=fmtp:123 profile-level-id=42801e; packetization-mode=0; max-mbps=48600\r\n";

		// return full SDP response
		//System.out.println(sdpData);		

		return sdpData;

	}

}
