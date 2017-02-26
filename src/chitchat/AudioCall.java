package chitchat;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

/**
 *
 * @author scopeinfinity
 */
public class AudioCall {
    private static final int PORT = 7576;
    private static int CHUNK_SIZE = 1024;
    private ChitChat chat;
    private boolean onCall;
    private boolean WAIT_ACK;
    
    public AudioCall(ChitChat chat) {
        onCall = false;
        this.chat = chat;
        System.out.println("Audio Call Status : "+startServer());
    }
    
    public boolean canStart() {
        return !onCall;
    }
    
    private void addSystemMessage(String str) {
        if(chat!=null)
            chat.addSystemMessage(str);
        System.out.println("System : "+str);
        
    }
    
    public boolean startServer() {
        try {
            ServerSocket ssocket = new ServerSocket(PORT);
            new Thread("Audio"){
                @Override
                public void run() {
                    
                    while(true) {
                        try {
                            Socket socket = ssocket.accept();
                            if(onCall) {
                                socket.close();
                                System.out.println("Audio Rejected from "+socket+" Already in Use");
                                continue;
                            }
                            onCall = true;
                            runCall(socket);
                        } catch (IOException ex) {
                            Logger.getLogger(AudioCall.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }
                
            }.start();
            return true;
        } catch (IOException ex) {
            Logger.getLogger(AudioCall.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }
    
    synchronized public boolean connectAsClient(String IP) {
        try {
            System.out.println("Connecting for Audio to "+IP);
            Socket socket = new Socket(IP,PORT);
            if(onCall)
            {
                socket.close();
                return false;
            }
            onCall = true;
            runCall(socket);
            return true;
        } catch (IOException ex) {
            Logger.getLogger(AudioCall.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }
    
    public void runCall(Socket socket) {
        addSystemMessage("Starting Audio Call");
        BufferedOutputStream bos[] = new BufferedOutputStream[1];
        DataInputStream dis[] = new DataInputStream[1];
        try {
            bos[0] = new BufferedOutputStream(socket.getOutputStream());
            dis[0] = new DataInputStream(socket.getInputStream());
            new Thread("Transmit Audio") {
                @Override
                public void run() {
                    readFromStream(dis[0],bos[0]);
                }
                
            }.start();
            transmitToStream(bos[0]);
                
            
        } catch (Exception ex) {
            Logger.getLogger(AudioCall.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            
            try {
                if(bos[0]!=null)
                    bos[0].close();
                if(dis[0]!=null)
                    dis[0].close();
                onCall=false;
            } catch (IOException ex) {
                Logger.getLogger(AudioCall.class.getName()).log(Level.SEVERE, null, ex);
            }
            
        addSystemMessage("Audio Call Ended");
        }
        
    }
    
    private void transmitToStream(BufferedOutputStream bos) {
        TargetDataLine microphone = null;
        WAIT_ACK = false;
        try {
            AudioFormat format = new AudioFormat(8000.0f, 16, 1, true,true);
            microphone = AudioSystem.getTargetDataLine(format);
            
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(format);
            microphone.start();
            
            byte[] buffer = new byte[CHUNK_SIZE];
            while(onCall) {
                if(WAIT_ACK)continue;
                int len = microphone.read(buffer, 0,CHUNK_SIZE);
                if(len==-1)
                    break;
                byte[] nbuffer = new byte[len];
                for (int k = 0; k < len; k++) {
                    nbuffer[k]=buffer[k];
                }
                VideoCall.sendData(bos, nbuffer, VideoCall.TYPE_AUDIO);
//                WAIT_ACK=true;
//                bos.write(buffer,0, len);
//                bos.flush();
                if(len>0)
                    System.out.println("Audio Chunk Send : "+len);
            }
            
        } catch (LineUnavailableException ex) {
            Logger.getLogger(AudioCall.class.getName()).log(Level.SEVERE, null, ex);
            System.out.println("Couldn't Open Microphone");
        } catch (IOException ex) {
            Logger.getLogger(AudioCall.class.getName()).log(Level.SEVERE, null, ex);
            System.out.println("Audio Disconnected");
        } finally {
          if(microphone!=null)
              microphone.close();
          if(bos!=null)
              try {
                  bos.close();
          } catch (IOException ex) {
              Logger.getLogger(AudioCall.class.getName()).log(Level.SEVERE, null, ex);
          }
        }
        onCall = false;
            
    }
    
    private void readFromStream(DataInputStream dis,BufferedOutputStream bos) {
        SourceDataLine  speaker = null;
        try {
            AudioFormat format = new AudioFormat(8000.0f, 16, 1, true, true);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            speaker = (SourceDataLine) AudioSystem.getLine(info);
            speaker.open(format);
            speaker.start();
            byte[] buffer=new byte[CHUNK_SIZE];
            int len;
            while(onCall) {
                int type[] = new int[1];
                byte[] data=VideoCall.readData(dis,type);
                if(type[0]==VideoCall.TYPE_AUDIO) {
                   System.out.println("Audio Chunk Received : "+data.length+" "+data.hashCode());
                   speaker.write(data,0,data.length);
//                   VideoCall.sendData(bos, null, VideoCall.TYPE_ACK);
                } else if(type[0]==VideoCall.TYPE_ACK) {
                   System.out.println("Audio ACK Received");
                   WAIT_ACK = false;
                }
            }
        } catch (LineUnavailableException ex) {
            Logger.getLogger(AudioCall.class.getName()).log(Level.SEVERE, null, ex);
            System.out.println("No Speaker Found!");
            
        } catch (IOException ex) {
            Logger.getLogger(AudioCall.class.getName()).log(Level.SEVERE, null, ex);
            System.out.println("Audio Disconnected");
        } finally {
            onCall=false;
            if(speaker!=null) {
//                speaker.drain();
                speaker.close();
            }
            if(dis!=null)
                try {
                    dis.close();
            } catch (IOException ex) {
                Logger.getLogger(AudioCall.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    String stop() {
        if(!onCall)
            return "No Audio Call Running!";
        onCall=false;
        return "Audio Stopped";
    }
    
}
