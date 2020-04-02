package de.fhffm.research.mediaserver;


// also: 
public class Participant {

	public String callId = null;
	public int port = 0;
	public String ip = null;
	public boolean canJoin = false;
	
	public String socket(){
		return ip + ":" + port;
	}
	/**
	 * @return the callId
	 */
	public String getCallId() {
		return callId;
	}
	/**
	 * @param callId the callId to set
	 */
	public void setCallId(String callId) {
		this.callId = callId;
	}
	/**
	 * @return the port
	 */
	public int getPort() {
		return port;
	}
	/**
	 * @param port the port to set
	 */
	public void setPort(int port) {
		this.port = port;
	}
	/**
	 * @return the ip
	 */
	public String getIp() {
		return ip;
	}
	/**
	 * @param ip the ip to set
	 */
	public void setIp(String ip) {
		this.ip = ip;
	}
	/**
	 * @return the canJoin
	 */
	public boolean isCanJoin() {
		return canJoin;
	}
	/**
	 * @param canJoin the canJoin to set
	 */
	public void setCanJoin(boolean canJoin) {
		this.canJoin = canJoin;
	}
}
