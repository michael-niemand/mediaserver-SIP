package de.fhffm.research.mediaserver;

public class RTPHelper {
 
	/**
     * für den Dynamischer Payloadtyp
     * SIP/SDP Bsp.: a=rtpmap:101 telephone-event/8000
     */
    public static final int DTMF_DYNAMIC_PAYLOADTYPE = 101;

    /**
	 *	Tastensymbol in DTMFEvent Zahlenwert wandeln:
	 *
     * @param char	DTMF-Zeichen
     * @return int  gibt Integer des DTMF-Zeichens zurück
     */
    public static int getDTMFEvent(char tone){
        switch (tone){
            case '0': return 0; 
            case '1': return 1; 
            case '2': return 2; 
            case '3': return 3; 
            case '4': return 4; 
            case '5': return 5; 
            case '6': return 6; 
            case '7': return 7; 
            case '8': return 8; 
            case '9': return 9; 
            case '*': return 10;
            case '#': return 11;
            default: return -1; 
        }
     } 
    

    /**
     * Ein komplettes RTP-Packet mit DTMF als Payload erstellen:
     * DTMF als Payload: siehe http://www.ietf.org/rfc/rfc2833.txt?number=2833
     * bzw.	http://www.ietf.org/rfc/rfc4733.txt?number=4733
     * 
     *  0                   1                   2                   3
     *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * | V |P|X|  C C  |M|    P T      |       Sequence Number         |                       
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |					       Timestamp						   |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |							S S R C						       |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |			optional  C S R C (0 - 64 Byte)					   |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |     event     |E|R| volume    |          duration             |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * 
     * @param marker				M
     * @param payLoadType			PT
     * @param sequenceNumber		Sequence Number
     * @param timestamp				Timestamp
     * @param ssrc					SSRC
     * @param dtmfEvent				event
     * @param dtmfEnd				E	
     * @param dtmfVolume			volume
     * @param dtmfDuration			duration
     * @return byte[]
     * @throws Exception
     */
    public static byte[] createRTPPaketWithDTMFPayLoad(boolean marker, byte payLoadType, int sequenceNumber, long timestamp, long ssrc, int dtmfEvent, boolean dtmfEnd, byte dtmfVolume, int dtmfDuration) throws Exception
     {
         //12 Bytes für RTP Header + 4 Byte für DTMF Payload:
         byte[] buf = new byte[12+4];

         //1. Byte mit 2 Bit für Version (=2), 1 Bit Padding (=0), 1 Bit extension (=0), 4 Bit CSRC count (CC) (=0):
         // 1000000b = 80hex = 128dez
         buf[0]=setUnsignedByteBits(128);

         //2. Byte mit 1 Bit für Marker und 7 Bits für Payloadtype
         buf[1] = (byte) (setUnsignedByteBits(marker==true ? 128 : 0) | setUnsignedByteBits(payLoadType));

         //3. + 4. Byte mit 16 Bits für Sequenznummer:
         buf[2] = (byte)((sequenceNumber >> 8) & 0xff); //Hi-Byte
         buf[3] = (byte)((sequenceNumber >> 0) & 0xff); //Lo-Byte

         //5. + 6. + 7. + 8. Byte mit 32 Bits für Timestamp
         buf[4] = (byte)((timestamp >> 24) & 0xff); //Hi-Word Hi-Byte
         buf[5] = (byte)((timestamp >> 16) & 0xff); //Hi-Word Lo-Byte
         buf[6] = (byte)((timestamp >>  8) & 0xff); //Lo-Word Hi-Byte
         buf[7] = (byte)((timestamp >>  0) & 0xff); //Lo-Word Lo-Byte

         //9. + 10. + 11. + 12. Byte mit 32 Bits für SSRC
         buf[8]  = (byte)((ssrc >> 24) & 0xff); //Hi-Word Hi-Byte
         buf[9]  = (byte)((ssrc >> 16) & 0xff); //Hi-Word Lo-Byte
         buf[10] = (byte)((ssrc >>  8) & 0xff); //Lo-Word Hi-Byte
         buf[11] = (byte)((ssrc >>  0) & 0xff); //Lo-Word Lo-Byte

         //4 Bytes des DTMF Payloads
         //1. Byte ist der Event also die Tasten '0'(=0)...'9'(=9) und '*'(=10) und '#'(=11) 
         buf[12] = setUnsignedByteBits(dtmfEvent);
         //2. Byte ist das End-Bit, das Reserved-Bit (immer 0) und 6 Bits für Volume:
         buf[13] = (byte) (setUnsignedByteBits(dtmfEnd==true ? 128 : 0) | setUnsignedByteBits(dtmfVolume)); 
         //3. + 4. Byte sind die 16 Bits von Duration:
         buf[14] = (byte)((dtmfDuration >> 8) & 0xff); //Hi-Byte
         buf[15] = (byte)((dtmfDuration >> 0) & 0xff); //Lo-Byte


         //Buffer zurückgeben:
         return buf;
     }
     
     /**
      * Setzt alle Bits eines Bytes ohne dass das Vorzeichen-Bit eine besondere Rolle spielt:
      * 
      * @param i
      * @return
      * @throws Exception
      */
     private static byte setUnsignedByteBits(int i) throws Exception{
         if (i>255 || i < 0)
             throw new Exception("Unsigned Byte Wertebereich 0..255 überschritten: "+i);
         return (byte)(i & 0xff);
     }
     
     /**
      * Extrahiert aus dem RTP-Packetkopf den Timestamp:
      * 
      * @param data
      * @return
      */
     public static long getRTPTimestamp(byte[] data){
         long lng, lng1, lng2, lng3, lng4;

         //****** 5. + 6. + 7. + 8. Byte:
         //Timestamp: 32 bits
         lng1 = data[4];
         lng1 = lng1 & 0xff;
         lng2 = data[5];
         lng2 = lng2 & 0xff;
         lng3 = data[6];
         lng3 = lng3 & 0xff;
         lng4 = data[7];
         lng4 = lng4 & 0xff;
         lng = (lng1 << 24) | (lng2 << 16) | (lng3 << 8) | lng4;
         lng =  lng & 0xffffffff;  
         return lng;
     }
     
     /**
      * Extrahiert aus dem RTP-Packetkopf die SequenceNumber:
      * 
      * @param data
      * @return
      */
     public static int getRTPSequenceNumber(byte[] data){
         long lng, lng1, lng2;
         int i;
         //****** 3. + 4. Byte:
         //sequence number: 16 bits
         lng1 = data[2];
         lng1 = lng1 & 0xff;
         lng2 = data[3];
         lng2 = lng2 & 0xff;
         lng = (lng1 << 8) | (lng2);
         i =  (int)(lng & 0xffff);
         return i;
     }
     
     /**
      * Extrahiert aus dem RTP-Packetkopf die Synchronisations Source:
      * 
      * @param data
      * @return
      */
     public static long getRTPSSRC(byte[] data){
         long lng, lng1, lng2, lng3, lng4;
         //****** 9. + 10. + 11. + 12. Byte:
         //SSRC: 32 bits
         lng1 = data[8];
         lng1 = lng1 & 0xff;
         lng2 = data[9];
         lng2 = lng2 & 0xff;
         lng3 = data[10];
         lng3 = lng3 & 0xff;
         lng4 = data[11];
         lng4 = lng4 & 0xff;
         lng = (lng1 << 24) | (lng2 << 16) | (lng3 << 8) | lng4;
         lng =  lng & 0xffffffff;
         return lng;
     }
     
     public static byte getPayloadType(byte[] data){
    	 byte pt;
         //****** 2. Byte:
         //payloadtype: 8 bits
    	 
    	 pt = data[1];
    	 
    	 return pt;
     }
     
     public static void setRTPTimestamp(long timestamp, byte[] data){
     	
     	data[4] = (byte) (timestamp >> 24);
     	data[5]= (byte) (timestamp >> 16);
     	data[6]= (byte) (timestamp >> 8);
     	data[7]= (byte) (timestamp);
     }

}
