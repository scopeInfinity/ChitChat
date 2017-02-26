package chitchat;


import com.xuggle.mediatool.IMediaReader;
import com.xuggle.mediatool.IMediaViewer;
import com.xuggle.mediatool.ToolFactory;
import com.xuggle.mediatool.demos.DisplayWebcamVideo;

import java.awt.Dimension;

import com.xuggle.mediatool.IMediaWriter;
import com.xuggle.mediatool.ToolFactory;
import com.xuggle.xuggler.ICodec;
import com.xuggle.xuggler.IContainer;
import com.xuggle.xuggler.IContainerFormat;
import com.xuggle.xuggler.IMediaData;
import com.xuggle.xuggler.IMetaData;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IPixelFormat;
import com.xuggle.xuggler.IStream;
import com.xuggle.xuggler.IStreamCoder;
import com.xuggle.xuggler.IVideoPicture;
import com.xuggle.xuggler.IVideoResampler;
import com.xuggle.xuggler.Utils;
import com.xuggle.xuggler.demos.VideoImage;
import com.xuggle.xuggler.video.ConverterFactory;
import com.xuggle.xuggler.video.IConverter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.JOptionPane;

public class VideoCall {
    private boolean isBusy = false;
    private ChitChat chat;
    
    private int SWIDTH = 640;
    private int SHEIGHT = 480;

    private int SFRAMERATE = 20;
    private int PACKETSLEEPTIMER = 1000/SFRAMERATE;

    private VideoImage mScreen;

    private String driverName;
    private String deviceName;

    private static final String WINDOWS_driverName = "vfwcap";
    private static final String WINDOWS_deviceName = "0";
    private static final String LINUX_driverName = "v4l2";
    private static final String LINUX_deviceName = "/dev/video0";

    private int VIDEO_PORT = 7575;
    
    private String myname;
  
    private void addSystemMessage(String str) {
        if(chat!=null)
            chat.addSystemMessage(str);
    }
    
    public VideoCall(String myname,ChitChat chat) {
        this.chat = chat;
        this.myname = myname;
        String OS = System.getProperty("os.name").toLowerCase();
        if(OS.contains("windows")) {
            driverName = WINDOWS_driverName;
            deviceName = WINDOWS_deviceName;
            System.out.println("OS : Windows");
        } else if(OS.contains("nux")) {
            driverName = LINUX_driverName;
            deviceName = LINUX_deviceName;
            System.out.println("OS : Linux");
        } else {
            System.out.println("Unsupported OS : "+OS);
        }
        System.out.println("Call Server Status : "+makeCallServer());
    }
    
    public boolean canStart() {
        return !isBusy;
    }

    public boolean makeCallServer() {
        try {
            System.out.println("Creating Video Socket");
            ServerSocket server = new ServerSocket(VIDEO_PORT);
            System.out.println("Video Socket created");
            new Thread("Call Server") {
                @Override
                public void run() {
                    while (true) {
                        try {
                            System.out.println("Waiting for Video Call");
                            Socket socket = server.accept();
                            if(isBusy) {
                                System.out.println("Call Disconnected, due to already in Call");
                                socket.close();
                                continue;
                            }
                            isBusy = true;
                            System.out.println("Video Call client : "+socket);
                            startCall(socket);
                        } catch (IOException ex) {
                            Logger.getLogger(VideoCall.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        
                    }
                }
                
            }.start();
            
            return true;
        } catch (IOException ex) {
            Logger.getLogger(VideoCall.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }

    synchronized public void connectToCall(String IP) {
        try {
            Socket socket = new Socket(IP, VIDEO_PORT);
            System.out.println("Going to Connect");
            if(isBusy){
                socket.close();
                JOptionPane.showMessageDialog(null, "Max 1 Call");
                return;
            }
            isBusy = true;
            startCall(socket);
        } catch (Exception ex) {
            Logger.getLogger(VideoCall.class.getName()).log(Level.SEVERE, null, ex);
            JOptionPane.showMessageDialog(null, "Call Disconnected");
        }

    }
    
    private boolean startCall(Socket socket) {
        try {
            addSystemMessage("Starting Video Call");
            BufferedOutputStream bos = new BufferedOutputStream(socket.getOutputStream());
            DataInputStream is = new DataInputStream(socket.getInputStream());
            System.out.println("Video Stream Opened");
            
            new Thread("Play Stream") {
                @Override
                public void run() {
                    super.run(); //To change body of generated methods, choose Tools | Templates.
                    System.out.println("Going to Play Stream");
                    playStream(socket, is, bos);

                }
                
            }.start();
            System.out.println("Going to Start Stream");
            startStreaming(socket, bos);
            return true;
        } catch (Exception e) {
            addSystemMessage("Video Closed!");
            return false;
        }
    }

    /**
     * Wait for ACK
     */
    private boolean STREAM_ACK_FRAME;
    
    private static final int TYPE_STRING = 0;
    private static final int TYPE_IMAGE = 1;
    public static final int TYPE_ACK = 2;
    public static final int TYPE_AUDIO = 3;
    
    /**
     * Send Data to Stream
     * @param bos
     * @param data
     * @param type
     * @throws IOException 
     */
    synchronized public static void sendData(BufferedOutputStream bos, byte []data, int type) throws IOException {
        bos.write(type);
         if(data==null)
             data=new byte[0];
        int len=data.length;
        for (int i = 0; i < 4; i++) {
            bos.write(len%256);
            len/=256;
        }
        bos.write(data);
        bos.flush();
        System.out.println("Send "+data.length+" Hash:"+data.hashCode());
    }
    
//    synchronized public static void sendData(BufferedOutputStream bos, byte []data, int type) throws IOException {
//        int len;
//        if(data==null)
//            len = 0;
//        else len = data.length;
//        sendData(bos, data, type, len);
//    }
    
    /**
     * Get Data from Stream
     * @param bis
     * @param type type[0] will  be updated to new type
     * @return data
     * @throws IOException 
     */
    public static byte[] readData(DataInputStream bis,int type[]) throws IOException {
        System.out.println("Waiting to Rec");
        type[0] = bis.read();
        if(type[0]==-1)
        {
            new RuntimeException("Connection Closed");
        }
        System.out.println("Rec Type  "+type[0]);
        
        int len = 0;
        for (int i = 0; i < 4; i++) {
            int l = bis.read();
            if(l==-1)
                new IOException("No Length Found");
            len=len+l*(1<<(8*i));
        }
        System.out.println("Len : "+len);
        byte[] data=new byte[len];
        bis.readFully(data,0,len);
//        if(ln!=len)
//        System.out.println("ERRRRRROR : "+ln+" != "+len);
        System.out.println("Rec "+data.length+" Hash :"+data.hashCode());
        
        return data;
    }
    
    private void writeString(BufferedOutputStream bos,String str) throws IOException {
        sendData(bos,str.getBytes(),TYPE_STRING);
    }
    
    
    private String readString(DataInputStream bis) throws IOException {
        int type[]= new int[1];
        byte data[]=readData(bis, type);
        if(type[0]!=TYPE_STRING)
            new RuntimeException("Not String Type");
        return new String(data);
    }
    
    private boolean startStreaming(Socket socket, BufferedOutputStream bos) {
        IContainer container = null;
        IStreamCoder coder = null;
            
        try {
            STREAM_ACK_FRAME = false;
            writeString(bos,myname);
            //bos.writeLong(System.currentTimeMillis());
            System.out.println("Going to Stream");
            if (!IVideoResampler.isSupported(IVideoResampler.Feature.FEATURE_COLORSPACECONVERSION)) {
                throw new RuntimeException("you must install the GPL version of Xuggler (with IVideoResampler support) for this demo to work");
            }

            container = IContainer.make();
            IContainerFormat format = IContainerFormat.make();

            if (format.setInputFormat(driverName) < 0) {
                throw new IllegalArgumentException("couldn't open webcam device: " + driverName);
            }

            IMetaData params = IMetaData.make();
            params.setValue("framerate", String.valueOf(SFRAMERATE));
            params.setValue("video_size", SWIDTH + "x" + SHEIGHT);

            int ret = container.open(deviceName, IContainer.Type.READ, format, false, true, params, null);
            if (ret < 0) {
                throw new IllegalArgumentException("could not open file: " + deviceName + ";");
            }
            int numStreams = container.getNumStreams();

            int streamIndex = -1;
            for (int i = 0; i < numStreams; i++) {
                IStream steam = container.getStream(i);
                IStreamCoder innerCoder = steam.getStreamCoder();
                if (innerCoder.getCodecType() == ICodec.Type.CODEC_TYPE_VIDEO) {
                    coder = innerCoder;
                    streamIndex = i;
                    break;
                }
            }
            if (coder == null) {
                throw new RuntimeException("could not find video stream in container: " + deviceName);
            }
            if (coder.open() < 0) {
                throw new RuntimeException("could not open video decoder for container: " + deviceName);
            }

            IVideoResampler resampler = null;
            if (coder.getPixelType() != IPixelFormat.Type.BGR24) {
                resampler = IVideoResampler.make(coder.getWidth(), coder.getHeight(), IPixelFormat.Type.BGR24, coder.getWidth(), coder.getHeight(), coder.getPixelType());
                if (resampler == null) {
                    throw new RuntimeException("could not create color space resampler for: " + deviceName);

                }
            }

            IPacket packet = IPacket.make();
            int i = 0;
            //On Call
            while (isBusy && container.readNextPacket(packet) >= 0) {
                if (socket.isClosed()) {
                    break;
                }
                if (packet.getStreamIndex() == streamIndex) {
                    if(STREAM_ACK_FRAME){
                        Thread.yield();
                        continue;
                    }
                       
                    IVideoPicture picture = IVideoPicture.make(coder.getPixelType(), coder.getWidth(), coder.getHeight());
                    int offset = 0;
                    while (offset < packet.getSize()) {
                        int decoded = coder.decodeVideo(picture, packet, offset);
                        if (decoded < 0) {
                            throw new RuntimeException("got error decoding video in: " + deviceName);
                        }

                        offset += decoded;

                        if (picture.isComplete()) {
                            IVideoPicture newPic = picture;
                            if (resampler != null) {
                                newPic = IVideoPicture.make(resampler.getOutputPixelFormat(), picture.getWidth(), picture.getHeight());

                                if (resampler.resample(newPic, picture) < 0) {
                                    throw new RuntimeException("could not resample video from: " + deviceName);
                                }
                            }
                            if (newPic.getPixelType() != IPixelFormat.Type.BGR24) {
                                throw new RuntimeException("could not decode video as BGR 24 bit data in: " + deviceName);
                            }

                            BufferedImage bimage = Utils.videoPictureToImage(newPic);
                            BufferedImage image = ConverterFactory.convertToType(bimage, BufferedImage.TYPE_3BYTE_BGR);

                            int type = image.getType();
                            int w = image.getWidth();
                            int h = image.getHeight();
                            byte[] pixels = null;
                            if (type == BufferedImage.TYPE_3BYTE_BGR) {
                                pixels = (byte[]) image.getData().getDataElements(0, 0, w, h, null);
                            }
                            try {
                                BufferedImage edgesImage = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
                                edgesImage.getWritableTile(0, 0).setDataElements(0, 0, w, h, pixels);
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                ImageIO.write(image, "jpg", baos);
                                STREAM_ACK_FRAME = true;
                                sendData(bos, baos.toByteArray(),TYPE_IMAGE);
//                                bos.writeLong(System.currentTimeMillis());
                                System.out.println("Image Send");
                                try {
                                    Thread.sleep(PACKETSLEEPTIMER);
                                } catch (InterruptedException ex) {
                                    Logger.getLogger(VideoCall.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            } catch (IOException e) {
                            }

//                               mScreen.setImage(image);
                        }
                    }
                }
            }

            
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error in Streaming!!!!!!!!!!!!!");
//            JOptionPane.showMessageDialog(null, "Error in Service");
            return false;
        } finally {
            if(bos!=null)
            try {
                bos.close();
            } catch (IOException ex) {
                Logger.getLogger(VideoCall.class.getName()).log(Level.SEVERE, null, ex);
            }
            if(coder!=null)
                coder.close();
            if(container!=null)
                container.close();
            isBusy = false;
            JOptionPane.showMessageDialog(null, "Call Over");
        }
    }

    private boolean playStream(Socket socket, DataInputStream is, BufferedOutputStream bos) {
        try {
            mScreen = new VideoImage();
            mScreen.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    isBusy=false;
                }
               
            });
            System.out.println("Play Stream");
            String otherName = readString(is);
            System.out.println("Other Name : "+otherName);
       
            mScreen.setTitle("Video Call "+myname+" <--> "+otherName);
            while (isBusy && !socket.isClosed()) {
                BufferedImage image;
                int type[] = new int[1];
                byte[] buffer = readData(is, type);
                System.out.println("Received : "+type[0]);
                if(type[0]==TYPE_IMAGE) {
                    System.out.println("Imaged Received");
                    image = ImageIO.read(new ByteArrayInputStream(buffer));
                    sendData(bos,null,TYPE_ACK);
                    mScreen.setImage(image);
                } else if(type[0] == TYPE_ACK) {
                    STREAM_ACK_FRAME = false;
                } else {
                    new RuntimeException("Invalid Rec Type");
                }
            }
            return true;
        } catch (Exception ex) {
            Logger.getLogger(VideoCall.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            isBusy = false;
            if(mScreen!=null)
                mScreen.dispose();
            mScreen = null;
        }
        return false;
    }

    String stop() {
        if(!isBusy)
            return "No Video Call Running!";
        isBusy=false;
        return "Video Stopped";
    }
    

}