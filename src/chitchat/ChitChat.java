package chitchat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;

public class ChitChat implements Controller {
    private String myname;
    private VideoCall videoCall;
    private AudioCall audioCall;
    private P2PChat chat;
    private static ChitChat instance;

    public static ChitChat getInstance() {
        return instance;
    }
    
    public ChitChat(String myname) {
        instance = this;
        this.myname = myname;
        videoCall = new VideoCall(myname,this);
        audioCall = new AudioCall(this);
    }
    
      
    public static ChitChat start(String myname) {
        ChitChat chat = new ChitChat(myname);
        Server server = new Server(chat,myname);
        new Thread("Server") {
            public void run() {
                server.start();
            }
        }.start();
        return chat;
       
    }
    public static boolean isReachable(String IP) {
        try{
            return InetAddress.getByName(IP).isReachable(2000);
        }catch(Exception e) {
            return false;
        }
    }
    
    public void addClient(String IP) {
        System.out.println("Attempt to Connect to "+IP);
        //Ping not working always, thus not taking as base case
         new Thread("Client") {
            public void run() {
                   Socket socket = connectTo(IP);
                    if(socket==null) {

                        if(!isReachable(IP)) {
                            JOptionPane.showMessageDialog(null, "User Offline");
                            return;
                        }

                        JOptionPane.showMessageDialog(null, "User Failed to Connect");
                    } else {
                                addP2P(socket,myname);
                           }
                    }
        }.start();
            
    }
    
    private static Socket connectTo(String IP) {
        InetAddress address;
        try {
            address = InetAddress.getByName(IP);
        } catch (UnknownHostException ex) {
            System.err.println("Invalid IP");
            return null;
        }
        try {
            Socket socket = new Socket(address, Server.PORT);
            return socket;
        } catch (IOException ex) {
            System.err.println("Couldn't Connected");
            return null;
        }
        
    }
    
    public void callAsVideo(String IP) {
//        if(!isReachable(IP)) {
//            JOptionPane.showMessageDialog(null, "User Offline");
//            return;
//        }
        if(videoCall.canStart()) {
            new Thread("Call Client"){
                @Override
                public void run() {
                    videoCall.connectToCall(IP);
                }

            }.start();
        }
        else {
            addSystemMessage("Max 1 Video Call at a time!");
        }
    }
    
    public void callAsAudio(String IP) {

        if(audioCall.canStart()) {
            new Thread("Audio Call Client"){
                @Override
                public void run() {
                    audioCall.connectAsClient(IP);
                }

            }.start();
        }
        else {
            addSystemMessage("Max 1 Audio Call at a time!");
        }
    }
    
    public void addSystemMessage(String str) {
        if(chat!=null)
            chat.addSystemMessage(str);
    }
    
    @Override
    public void addP2P(Socket socket, String myname) {
        chat = new P2PChat(socket,myname);
        chat.start();
    }

    void disconnectVideo() {
        if(videoCall==null)
            return;;
        addSystemMessage(videoCall.stop());
        
    }

    void disconnectAudio() {
        if(audioCall==null)
            return;;
        addSystemMessage(audioCall.stop());
        
    }
    
}
