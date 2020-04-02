/*
 * class MediaServer
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

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.ListIterator;
import java.util.TooManyListenersException;

import javax.sip.InvalidArgumentException;
import javax.sip.ObjectInUseException;
import javax.sip.PeerUnavailableException;
import javax.sip.TransportNotSupportedException;
import javax.sip.address.SipURI;
import javax.sip.header.Header;
import javax.sip.header.ToHeader;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.PatternLayout;
import org.gstreamer.Gst;

/**
 * @author niemand
 *
 */

public class MediaServer {
	
	private static final String PROPERTIES_FILE_NAME = "MediaServer.properties";

	//private AudioMixer audiomixer;
	//private VideoBridge videobridge;
	private Focus focus;

	private static volatile List<ConferenceRoom> conferenceRooms = new ArrayList<ConferenceRoom>();

	public Focus getFocus() {
		return focus;
	}	

	public void setFocus(Focus focus) {
		this.focus = focus;
	}

	public static List<ConferenceRoom> getConferenceRooms() {
		return conferenceRooms;
	}

	protected static void setConferenceRooms(List<ConferenceRoom> conferenceRooms) {
		MediaServer.conferenceRooms = conferenceRooms;
	}

	/**
	 * @param name = Name of the conference room to return
	 * @return returns the room if exists or creates one if not and returns the new room
	 */
	public static ConferenceRoom getRoomByName(String name) throws NullPointerException {
		ConferenceRoom room = null;

		ListIterator<ConferenceRoom> itc = MediaServer.getConferenceRooms().listIterator();
		
		while(itc.hasNext()){
			// if senders IP is in list of clients, authorized via SIP, forward RTP to localhost
			ConferenceRoom element = itc.next();
			if(element.getRoomName().equals(name)){
				System.out.println("room exists; returning room ...");
				room=element;
			}
		}
		// not in list
		if (room==null){
			System.out.println("adding room ...");
			room = MediaServer.addRoom(name);
		}
		
		return room;
	}

	private static ConferenceRoom addRoom(String name) {
		// TODO: DO NOT hardwire the max number
		conferenceRooms.add(new ConferenceRoom(4, name));
		// TODO this is bad
		return conferenceRooms.get(conferenceRooms.size()-1);
	}

	public static void main(String[] args) throws PeerUnavailableException, TransportNotSupportedException, ObjectInUseException, InvalidArgumentException, TooManyListenersException, IOException, ConfigurationException{

		MediaServer mediaserver = new MediaServer();
		mediaserver.init();
	}

	public void init() throws PeerUnavailableException, TransportNotSupportedException, ObjectInUseException, InvalidArgumentException, TooManyListenersException, IOException, ConfigurationException {

		//Initialize the apache logging system properly
		//org.apache.log4j.Logger.getRootLogger().addAppender(new ConsoleAppender(new PatternLayout("%d %-5p %c - %F:%L - %m%n")));
		org.apache.log4j.Logger.getRootLogger().addAppender(new ConsoleAppender(new PatternLayout("")));		
		
		PropertiesConfiguration config = new PropertiesConfiguration();
		// Try to load the config file ...
		try {
			config.load(PROPERTIES_FILE_NAME);
		}
		catch (ConfigurationException e) {
			// if config file was NOT found, create new one with following default values
			config.setProperty("IP_ADDR", "192.168.0.91");
			config.setProperty("HOSTNAME", "Gstreamer-MediaServer");
			config.setProperty("SIP_URI", "MediaServer@${IP_ADDR}");
			config.setProperty("SIP_PORT", "5060");			
			config.setProperty("AUDIO_RECV_PORT", "6040");
			config.setProperty("VIDEO_RECV_PORT", "7040");
			config.setProperty("MAX_PARTICIPANTS", "4");
			config.setProperty("WELCOMETEXT", "SIP Videoconference");
			config.setProperty("AUDIO_CODEC", "PCMU/8000");
			config.setProperty("VIDEO_CODEC", "H264/90000");
			config.setProperty("VIDEO_CODEC", "H263/90000");

			config.save(PROPERTIES_FILE_NAME);
		}

		InetAddress addr = getFirstNonLoopbackAddress(true, false);

		// extract IP Address
		byte[] ipAddr = addr.getAddress();

		// extract hostname
		String hostname = addr.getHostName();
		config.setProperty("IP_ADDR", ipAdressGetString(ipAddr));
		config.setProperty("HOSTNAME", hostname);
		config.save(PROPERTIES_FILE_NAME);

		focus = new Focus();
		focus.init();

		// Initialize Gstreamer
		Gst.init();
		
		System.out.println("MediaServer ready to receive calls!\n--------------------------------------------\n");
	}

	@SuppressWarnings("rawtypes")
	public static InetAddress getFirstNonLoopbackAddress(boolean preferIpv4, boolean preferIPv6) throws SocketException {
		Enumeration en = NetworkInterface.getNetworkInterfaces();
		while (en.hasMoreElements()) {
			NetworkInterface i = (NetworkInterface) en.nextElement();
			for (Enumeration en2 = i.getInetAddresses(); en2.hasMoreElements();) {
				InetAddress addr = (InetAddress) en2.nextElement();
				if (!addr.isLoopbackAddress()) {
					if (addr instanceof Inet4Address) {
						if (preferIPv6) {
							continue;
						}
						return addr;
					}
					if (addr instanceof Inet6Address) {
						if (preferIpv4) {
							continue;
						}
						return addr;
					}
				}
			}
		}
		return null;
	}

	public static String ipAdressGetString(byte[] ipAddr) {
		String strIpAdress="";

		for(int i=0; i<=ipAddr.length-1; i++){
			int tuple = ipAddr[i] & 0xFF;
			strIpAdress = strIpAdress + tuple;
			if (i < ipAddr.length-1){
				strIpAdress = strIpAdress + ".";
			}
		}
		return strIpAdress;
	}

//	public static String getRoomByName(Header header) {
//		
//		ToHeader toHeaderRequest = (ToHeader) header;
//		
//		SipURI confUri = (SipURI)toHeaderRequest.getAddress().getURI();
//
//		return confUri.getUser();
//	}



}
