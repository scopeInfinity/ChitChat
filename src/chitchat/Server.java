package chitchat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author scopeinfinity
 */
public class Server {
    public static int PORT = 8989;
    private ServerSocket ssocket;
    private Controller controller;
    private String myname;
    
    public Server(Controller controller,String myname) {
        this.controller = controller;
        this.myname = myname;
    }
    
    public boolean start() {
        try {
            ssocket = new ServerSocket(PORT);
            System.out.println("Server Started at PORT "+PORT);
            while (true) {                
                Socket socket = ssocket.accept();
                System.out.println("Client Connected from "+socket.getRemoteSocketAddress());
                new Thread(){
                    public void run() {
                        controller.addP2P(socket,myname);
                    }
                }.start();
            }
        } catch (IOException ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }
    
    
    
}
