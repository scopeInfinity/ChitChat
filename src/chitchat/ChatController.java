package chitchat;

import java.net.Socket;

/**
 *
 * @author scopeinfinity
 */
public interface ChatController {
    public void sendMessage(String msg);
    public void setUI(UI ui);
}