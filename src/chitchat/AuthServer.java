package chitchat;

import static chitchat.Login.AUTH_SERVER_PORT;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author scopeinfinity
 */
public class AuthServer extends Thread{
    private Socket socket;
    
    private AuthServer(Socket socket) {
        this.socket=socket;
    }
    
    public static void main(String args[]) throws IOException {
        ServerSocket ssocket = new ServerSocket(AUTH_SERVER_PORT);
        while(true) {
            Socket socket = ssocket.accept();
            new AuthServer(socket).start();
        }
    }

    @Override
    public void run() {
        String ip =  ((InetSocketAddress)socket.getRemoteSocketAddress()).getAddress().toString().substring(1);
        
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            String type= br.readLine().trim();
            if(type.equals("Add Friend")) {
                addFriend(br,bw);
            } else if(type.equals("Login")){
                checkLogin(br,bw,true,ip);
            } else if(type.equals("Register")){
                doRegister(br,bw,ip);
            } else if(type.equals("Friends")){
                getFriends(br,bw,ip);
            }
            bw.flush();
            socket.close();
        } catch (IOException ex) {
            
        } finally {
            try {
                br.close();
            } catch (IOException ex) {
                
            }
        }
        
    }
    
    private void addFriend(BufferedReader br, BufferedWriter bw)  throws IOException {
        String name = br.readLine().trim();
        String pwd = br.readLine();
        String friend = br.readLine().trim();
        bw.write(AuthData.getInstance().addFriend(name,pwd,friend));
        bw.flush();
    }

    private boolean checkLogin(BufferedReader br, BufferedWriter bw, boolean b, String ip) throws IOException {
        String name=br.readLine().trim();
        System.out.println("Checking : "+name);
        String pwd = br.readLine();
        if(AuthData.getInstance().isUser(name)) {
            
            if(AuthData.getInstance().checkPassword(name, pwd)) {
                AuthData.getInstance().updateIP(name,ip);
                AuthData.getInstance().dataUpdated();
                System.out.println("Login for "+name);
                if(!b)
                    return true;
                else
                    bw.write("DONE");
                bw.flush();
                return true;
            }
            else bw.write("Invalid Password");
        }
        else bw.write("Invalid User");
        bw.flush();
        return false;
    }

    private void doRegister(BufferedReader br, BufferedWriter bw, String ip)  throws IOException {
        String name=br.readLine().trim();
        String pwd = br.readLine();
        bw.write(AuthData.getInstance().register(name, pwd, ip));
        bw.flush();
    }

    private void getFriends(BufferedReader br, BufferedWriter bw, String ip)  throws IOException {
        String name = br.readLine().trim();
        String pwd = br.readLine();
        bw.write(AuthData.getInstance().getFriends(name,pwd, ip));
        bw.flush();
    }
    
}
