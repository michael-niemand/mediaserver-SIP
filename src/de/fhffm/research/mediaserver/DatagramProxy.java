/**
 * 
 */
package de.fhffm.research.mediaserver;

import java.io.*;
import java.net.*;
//import java.util.ListIterator;

/**
 * 
 * @author niemand
 *
 */
public class DatagramProxy extends Thread {

	private final static int MAX_SIZE = 1460;

	private int recvPort = -1;
	private int sendPort = -1;

	private InetAddress destIp = null;

	private boolean stop = false;

	private byte[] recvData = null;
	private byte[] sendData = null;

	private DatagramSocket proxy = null;

	public DatagramProxy(int sendPort, DatagramSocket proxy) throws UnknownHostException, SocketException{

		this.proxy = proxy;
		this.sendPort = sendPort;
		this.destIp = InetAddress.getLocalHost();
		this.proxy.setReuseAddress(true);

		init();
	}


	private void init(){
		recvPort = proxy.getLocalPort();
		setPacketSize(MAX_SIZE);
	}

	public void setPacketSize(int size){
		if(size<1){
			size=1;

		} else if(size>MAX_SIZE){
			size= MAX_SIZE;
		}
		recvData = new byte[size];
		sendData = new byte[size];

	}

	public void run(){

		try {
			while(!stop){
				DatagramPacket recvPacket = new DatagramPacket(recvData, recvData.length);
				proxy.receive(recvPacket);
				if(recvPacket.getAddress().getHostName().equals(destIp.getHostAddress())){
					if(recvPacket.getPort() != sendPort){
						update(recvPacket.getPort());
					}

				}else{
					// ... forward to localhost if in list

//					boolean inList = false;
//					InetAddress remoteIp = recvPacket.getAddress();
//					// ... only forward if on list	
//					ListIterator<SIPAuthorizedClient> itc = MediaServer.getAuthorizedClients().listIterator();
//					while(itc.hasNext()){
//						// if senders IP is in list of clients, authorized via SIP, forward RTP to localhost
//						if(itc.next().getClientAddress().equals(remoteIp)){
//							inList=true;
//						}
//					}
//					if (inList){
					
						sendData = recvPacket.getData();
						DatagramPacket sendPacket = new DatagramPacket(sendData, recvPacket.getLength(), destIp, sendPort);
						proxy.send(sendPacket);
						
//					}
				}
			}
		}catch (SocketException e) {
			e.printStackTrace();
		}catch (IOException e) {
			e.printStackTrace();
		}
	}

	public synchronized void kill(){
		stop=true;
	}
	private synchronized void update (int sendPort){
		this.sendPort = sendPort;
	}

	public int getRecvPort(){
		return recvPort;
	}


}









// incoming packet ...



// Is packets origin on the authorized list?

// forward packet



// Not?

// discard packet







