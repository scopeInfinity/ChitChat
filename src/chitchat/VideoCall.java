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
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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

    private int SWIDTH = 640;
    private int SHEIGHT = 480;

    private int SFRAMERATE = 25;
    private int PACKETSLEEPTIMER = 40;

    private VideoImage mScreen;

    private String driverName = "v4l2";
    private String deviceName = "/dev/video0";

    private int VIDEO_PORT = 7575;
    private boolean anyCallRunning = false;
   
    private String myname;

    public VideoCall(String myname) {
        this.myname = myname;
        System.out.println("Call Server Status : "+makeCallServer());
    }

    public boolean makeCallServer() {
        try {
            ServerSocket server = new ServerSocket(VIDEO_PORT);
            new Thread("Call Server") {
                @Override
                public void run() {
                    while (true) {
                        try {
                            Socket socket = server.accept();
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

    public void connectToCall(String IP) {
        try {
            Socket socket = new Socket(IP, VIDEO_PORT);
            System.out.println("Going to Connect");
            startCall(socket);
        } catch (IOException ex) {
            Logger.getLogger(VideoCall.class.getName()).log(Level.SEVERE, null, ex);
            JOptionPane.showMessageDialog(null, "Can't connect for Video Call");
        }

    }
    
    private boolean startCall(Socket socket) {
        try {

            BufferedOutputStream bos = new BufferedOutputStream(socket.getOutputStream());
            startStreaming(socket, new ObjectOutputStream(bos));
            ObjectInputStream is = new ObjectInputStream(socket.getInputStream());
            new Thread("Play Stream") {
                @Override
                public void run() {
                    super.run(); //To change body of generated methods, choose Tools | Templates.
                    playStream(socket, is);
                }
                
            }.start();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean startStreaming(Socket socket, ObjectOutputStream bos) {
        try {
            bos.writeObject(myname);
            System.out.println("Going to Stream");
            if (!IVideoResampler.isSupported(IVideoResampler.Feature.FEATURE_COLORSPACECONVERSION)) {
                throw new RuntimeException("you must install the GPL version of Xuggler (with IVideoResampler support) for this demo to work");
            }

            IContainer container = IContainer.make();
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
            IStreamCoder coder = null;
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

            while (container.readNextPacket(packet) >= 0) {
                if (socket.isClosed()) {
                    break;
                }
                if (packet.getStreamIndex() == streamIndex) {
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
                                bos.writeInt(baos.size());
                                baos.writeTo(bos);
                                bos.flush();
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

            bos.close();
            coder.close();
            JOptionPane.showMessageDialog(null, "Terminated");

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error in Streaming!!!!!!!!!!!!!");
//            JOptionPane.showMessageDialog(null, "Error in Service");
            return false;
        }
    }

    private boolean playStream(Socket socket, ObjectInputStream is) {
        try {
            anyCallRunning=true;
            mScreen = new VideoImage();
            System.out.println("Play Stream");
            String otherName = (String) is.readObject();
            mScreen.setTitle("Video Call "+myname+" <--> "+otherName);
            while (!socket.isClosed()) {
                BufferedImage image;
                int size = is.readInt();
                byte[] buffer = new byte[size];
                is.readFully(buffer);
                
                System.out.println("Imaged Received");
                image = ImageIO.read(new ByteArrayInputStream(buffer));
                mScreen.setImage(image);
            }
            mScreen.dispose();
            mScreen = null;
            return true;
        } catch (IOException ex) {
            Logger.getLogger(VideoCall.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(VideoCall.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            anyCallRunning = false;
        }
        return false;
    }

}