package chitchat;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author scopeinfinity
 */
public class P2PChat implements ChatController {
    
    private Socket socket;
    private UI ui;
    private BufferedReader br;
    private BufferedOutputStream bos;
    private String myname,othername;
    
    
    public P2PChat(Socket socket,String myname) {
        this.socket = socket;
        this.myname = myname;
    }

    @Override
    public void sendMessage(String msg) {
        ui.addMessage(msg,myname);
        try {
            bos.write((msg+"\n").getBytes());
            bos.flush();
        } catch (IOException ex) {
            ui.addMessage("Failed to send above message","System");
            Logger.getLogger(P2PChat.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public void setUI(UI ui){
        this.ui = ui;
        System.out.println("UI is init ["+(ui!=null)+"]");
    }
    
    public boolean start() {
        System.out.println("Starting Chat for "+socket);
        
        try {
            br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            bos = new BufferedOutputStream(socket.getOutputStream());
            bos.write((myname+"\n").getBytes());
            bos.flush();
            othername = br.readLine();
            UI.launch(this,myname,othername);
            while(ui==null) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                }
            }
            new Reader().start();
            return true;
        } catch (IOException ex) {
            Logger.getLogger(P2PChat.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
          
    }
    
    class Reader extends Thread{
        
        @Override
        public void run() {
            System.out.println("Reader Started");
                try {
                    
                    ui.addMessage("Chat Top", "System");
                    String line;
                    while((line=br.readLine())!=null) {
                        ui.addMessage(line, othername);
                    }
                    
                } catch (IOException ex) {
                    ui.addMessage("Chat Ended!", "System");
                }
           
        }
        
        
    }
    
}
