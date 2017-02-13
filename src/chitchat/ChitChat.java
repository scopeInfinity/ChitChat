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

    public ChitChat(String myname) {
        this.myname = myname;
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
    
    public void addClient(String IP) {
        System.out.println("Attempt to Connect to "+IP);
            Socket socket = connectTo(IP);
            if(socket==null) {
                JOptionPane.showMessageDialog(null, "Failed to Connect");
            } else {
                new Thread("Client") {
                    public void run() {
                        addP2P(socket,myname);
                    }
                }.start();
            }
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
    
    @Override
    public void addP2P(Socket socket, String myname) {
        P2PChat chat = new P2PChat(socket,myname);
        chat.start();
    }
    
}
