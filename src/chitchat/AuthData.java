package chitchat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author scopeinfinity
 */
public class AuthData implements Serializable {
    public static final long serialVersionUID = 9523152053620532L;
    private static AuthData instance;
    private static final String FILENAME = "data";
    
    private HashMap<String,String> users;
    private HashMap<String,String> ipmap;
    private HashMap<String,ArrayList<String> > friends;

    public AuthData() {
        users = new HashMap<String,String>();
        ipmap = new HashMap<String,String>();
        friends = new HashMap<String,ArrayList<String> >();
    }
    
    public boolean isUser(String username) {
       return users.get(username)!=null;
    }
    
    public boolean checkPassword(String username,String pass) {
       return pass.equals(users.get(username));
    }
    
    public String getFriends(String name,String pwd,String ip) {
        if(!checkPassword(name,pwd)) {
           return "Invalid Password";
       }
       updateIP(name,ip);
       dataUpdated();
       StringBuffer sb = new StringBuffer();
       ArrayList<String> list;
        list = friends.get(name);
        if(list==null)
           list = new ArrayList<>();
        for (String string : list) {
            sb.append(ipmap.get(string)+"\n"+string+"\n");
        }
        return sb.toString();
    }
    
    public String addFriend(String uname,String pwd,String friend) {
       if(!checkPassword(uname,pwd)) {
           return "Invalid Password";
       }
       if(!isUser(friend))
           return "Invalid Friend Name";
       if(friends.get(uname).contains(friend.trim())) 
           return "Already a Friend";
       friends.get(uname).add(friend);
       dataUpdated();
       return "DONE";
    } 

    
    public String register(String name, String pwd, String ip) {
        if(isUser(name))
           return "Username Already in Use";
        users.put(name,pwd);
        friends.put(name, new ArrayList<>());
        updateIP(name,ip);
        dataUpdated();
        return "DONE";
    }
    
    public void updateIP(String user,String ip) {
        ipmap.put(user, ip);
    }
    
    
    public static AuthData getInstance() {
        if(instance==null){
            ObjectInputStream ois = null;
            try {
                File file = new File(FILENAME);
                ois = new ObjectInputStream(new FileInputStream(file));
                instance = (AuthData) ois.readObject();
            } catch (FileNotFoundException ex) {
                instance = new AuthData();
            } catch (IOException ex) {
                Logger.getLogger(AuthData.class.getName()).log(Level.SEVERE, null, ex);
            } catch (ClassNotFoundException ex) {
                Logger.getLogger(AuthData.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                try {
                    if(ois!=null)
                        ois.close();
                } catch (IOException ex) {
                    Logger.getLogger(AuthData.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        return instance;
    }
    
    synchronized public void dataUpdated() {
        try {
            ObjectOutputStream oos;
            oos = new ObjectOutputStream(new FileOutputStream(new File(FILENAME)));
            oos.writeObject((Object)this);
            oos.close();
        } catch (IOException ex) {
            Logger.getLogger(AuthData.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    
}
