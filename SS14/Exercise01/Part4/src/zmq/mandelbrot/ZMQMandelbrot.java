
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package zmq.mandelbrot;


import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import org.zeromq.ZContext;
import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.PollItem;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMsg;
import org.zeromq.ZMQ.Poller;

/**
 *
 * @author IronFox
 */
public class ZMQMandelbrot {

    static ZMQ.Context ctx;             //!< Global ZMQ context (initialized in main())
    
    static float    pixelWidth = 1.f,   //!< Coordinate-space horizontal sample size (for multisampling)
                    pixelHeight = 1.f;  //!< Coordinate-space vertical sample size
    
    static int      resX = 2500,   //!< Horizontal image resolution in pixels
                    resY;          //!< Vertical image resolution in pixels (derived in main())
    static float    minX = -2.f,   //!< Horizontal minimum coordinate
                    maxX = 1.f,    //!< Horizontal maximum coordinate
                    minY = -1.2f,  //!< Vertical minimum coordinate
                    maxY = 1.2f;   //!< Vertical maximum coordinate
    
    
    /**
     * Prints a byte array to the console
     * @param data Array to print
     */
    static void dump(byte[] data)
    {
        System.out.print(data.length+"_{");
        for (int i = 0; i < data.length; i++)
            System.out.print(" "+data[i]);
        System.out.println("}");
    }
    
    /**
     * Custom output stream used for fast resettable stream output
     */
    public static class MyOutputStream extends OutputStream {
        
        private byte[] data;
        private int writingAt = 0;
        
        /**
         * Initializes a new object using the specified number of bytes
         * @param size Size of the internal buffer in bytes
         */
        public MyOutputStream(int size)
        {
            data = new byte[size];
        }
        /**
         * Allows to reset the buffer to a specific byte address within the buffer.
         * @param offset Byte offset to continue writing from, relative to the beginning of the buffer (0 = first byte)
         */
        public void     resetTo(int offset) {writingAt = offset; assert(writingAt >= 0 && writingAt < data.length);}

        /**
         * Pushes the entire local data onto the specified ZMQ socket
         * @param s Socket to write to.
         */
        public void     write(Socket s)
        {
            //dump(data);
            s.send(data,data.length);
        }
        
        @Override
        public void	write(int b)
        {
            data[writingAt++] = (byte)b;
        }
    }
    
    /**
     * Custom input stream used for fast resettable stream input
     */
    public static class MyInputStream extends InputStream {
        
        private byte[] data;
        private int readingAt = 0;
        
        
        /**
         * Initializes a new object using the specified number of bytes
         * @param size Size of the internal buffer in bytes
         */
        public MyInputStream(int size)
        {
            data = new byte[size];
        }

        /**
         * Resets buffer output to the first byte
         */
        public void     resetToBeginning() {readingAt = 0;}
        
        /**
         * Fills the entire local buffer from the specified ZMQ socket
         * @param s Socket to read from
         */
        public void     fill(Socket s)
        {
            int rc = s.recv(data,0,data.length,0);
            assert(rc == data.length);
            //dump(data);
            readingAt = 0;
        }
        
        
        @Override
        public int	available() {
            return data.length - readingAt;
        }
        @Override
        public int	read() throws EOFException  {
            if (readingAt >= data.length)
                throw new EOFException();
            byte rs = data[readingAt++];
            return (int) rs;
        }
        
        /**
         * Reads a single 4-byte integer from the local buffer at the current read-address. Advances the read-address by 4 bytes
         * @return Decoded integer
         * @throws EOFException 
         */
        public int      readInt() throws EOFException
        {
            if (readingAt+4 > data.length)
                throw new EOFException();
            return    (int)data[readingAt++] << 24 
                    | (int)(data[readingAt++]&0xFF) << 16 
                    | (int)(data[readingAt++]&0xFF) << 8 
                    | (int)(data[readingAt++]&0xFF);
        }
        
        /**
         * Reads a single 4-byte floating point value from the local buffer at the current read-address. Advances the read-address by 4 bytes
         * @return
         * @throws EOFException 
         */
        public float    readFloat() throws EOFException
        {
            return Float.intBitsToFloat(readInt());
        }
        
        
                
        @Override
        public long	skip(long n)
        {
            System.out.println("skipping "+n); 
            int skp = Math.min(available(),(int)n);
            readingAt += skp;
            return skp;
        }
    }

    /**
     * Calculation worker thread.
     * Any number of worker objects may be created, although in practice there should not be more than processor cores available.
     */
    public static class Worker extends Thread
    {

        private static float iterate(float cx, float cy, int maxIteration)
        {
            float sqr = 0.0f;
            int iteration = 0;
            float x = 0.0f;
            float y = 0.0f;
 
            
            while ( sqr <= 4.f  &&  iteration < maxIteration ){
                float xt = x * x - y * y + cx;
                float yt = 2 * x * y + cy;
                x = xt;
                y = yt;
                iteration++;
                sqr = x * x + y * y;
            }
            if (iteration == maxIteration)
                iteration = 0;
            else
            {
                float zn = (float)Math.sqrt( x*x + y*y );
                float nu = (float)(Math.log( Math.log(zn) / Math.log(2) ) / Math.log(2));
                // Rearranging the potential function.
                // Could remove the sqrt and multiply log(zn) by 1/2, but less clear.
                // Dividing log(zn) by log(2) instead of log(N = 1<<8)
                // because we want the entire palette to range from the
                // center to radius 2, NOT our bailout radius.
                return (float)iteration + 1.f - nu;
            }
            return iteration;
        }
   
        
        @Override
        public void run() {
             Socket pull = ctx.socket(ZMQ.PULL),
                     push = ctx.socket(ZMQ.PUSH);
            pull.connect("inproc://workIn");
            //pull.connect("tcp://localhost:10100");
            push.connect("inproc://workOut");
            //push.connect("tcp://localhost:10101");
             
            // byte pushData[] = new byte[12];
             //byte pullData[] = new byte[8];
             
            MyInputStream stream = new MyInputStream(8);
            MyOutputStream outStream = new MyOutputStream(12);
            DataOutputStream psh = new DataOutputStream(outStream);
            //ByteArrayInputStream pullData = new ByteArrayInputStream(data);
             
                //System.out.println("working ...");
            int cnt = 0;
            for (;;)
            {
                //int rc = pull.recv(data,8,0,0);
                stream.fill(pull);
                 {
                     cnt ++;
                     /*
                    assert(rc == 8);
                    if (rc != 8)
                    {
                        fatal("Invalid package length received: "+rc);
                        continue;
                    }
                    else*/
                    {
                         try {
                             float   x = stream.readFloat();
                             float   y = stream.readFloat();
                             //System.out.println("processing pixel "+x+","+y+"...");
                             
                             int numSamples = 4;
                             float rs = 0;
                             for (int sx = 0; sx < numSamples; sx++)
                                 for (int sy = 0; sy < numSamples; sy++)
                                 {
                                     float fx = (float)sx / (float)numSamples * pixelWidth;
                                     float fy = (float)sy / (float)numSamples * pixelHeight;
                                     rs +=  iterate(x + fx, y + fy, 100);
                                 }
                             
                             
                             //DataOutputStream psh = new DataOutputStream(pushData);
                             outStream.resetTo(0);
                             psh.writeFloat(x);
                             psh.writeFloat(y);
                             psh.writeFloat((float)(rs) / (float)(numSamples*numSamples) / 100.f);
                             psh.flush();
                             outStream.write(push);
                         } catch (IOException ex) {
                             Logger.getLogger(ZMQMandelbrot.class.getName()).log(Level.SEVERE, null, ex);
                         }
                    }
                    //if (0==(cnt % 1000))
                      //  System.out.println("processed pixel #"+cnt);
                 }
             }
         }
    
    }
    
    /**
     * Calculates the linear interpolation between @a v0 and @a v1 at @a x
     * @param v0 Lower value to interpolate between
     * @param v1 Upper value to interpolate between
     * @param x Linear location in the range [0,1]
     * @return Linear interpolation
     */
    public static float lerp(float v0, float v1, float x)
    {
        return v0 + (v1 - v0) * x;
    }
    
    /**
     * Reader thread used to collect calculation results and fill them into an image for output.
     * 
     * Only one instance may be created of this class
     */
    public static class Reader extends Thread
    {
        
        @Override
        public void run() {
            Socket pull = ctx.socket(ZMQ.PULL);
            pull.bind("inproc://workOut");
            //pull.bind("tcp://*:10101");
            
            int numPixels = resX * resY;
            System.out.println("reading "+numPixels+" pixels ...");
        
            BufferedImage bi = new BufferedImage(resX,resY,BufferedImage.TYPE_INT_RGB);

            MyInputStream pullStream = new MyInputStream(12);
            
            
            float r0 = 1.0f;
            float g0 = 0.6f;
            float b0 = 0.1f;
            float r1 = 0.6f;
            float g1 = 0.8f;
            float b1 = 1.f;
            
            for (int i = 0; i < numPixels; i++)
            {
                try {
                    pullStream.fill(pull);
                    //System.out.println("got pixel ...");

                    float fx = pullStream.readFloat();
                    float fy = pullStream.readFloat();
                    float it = pullStream.readFloat();

                    int x = (int)Math.round((fx - minX) / (maxX - minX) * (resX-1));
                    int y = (int)Math.round((fy - minY) / (maxY - minY) * (resY-1));

                    float rel = it;
                            //(float)Math.log(1.f + it * 4.f);
                            //(float)Math.sqrt(it);
                    
                    //rel = (float)Math.sqrt(rel);
                    float r,g,b;
                    float d0 = 0.2f, d1 = 0.4f;

                    {
                        float _x = (rel - d0) / (d1 - d0);
                        _x = (float)Math.min(1.f,Math.max(0.f,_x));
                        r = lerp(r0,r1,_x);
                        g = lerp(g0,g1,_x);
                        b = lerp(b0,b1,_x);
                    }
                    r *= rel*4.f;
                    g *= rel*4.f;
                    b *= rel*4.f;
                            
                    
                    bi.setRGB(x,y,colorToRGB(r,g,b));
                } catch (EOFException ex) {
                    Logger.getLogger(ZMQMandelbrot.class.getName()).log(Level.SEVERE, null, ex);
                }
                if (0 == (i % 100000))
                    System.out.println("passed "+i+" pixels");
            }

            File outputfile = new File("saved.png");
            try {
                ImageIO.write(bi, "png", outputfile);
            } catch (IOException ex) {
                Logger.getLogger(ZMQMandelbrot.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    
    /**
     * Converts a floating point RGB color into a byte-encoded integer value for image output
     * @param r Red component in the range [0,1]
     * @param g Green component in the range [0,1]
     * @param b Blue component in the range [0,1]
     * @return Integer-encoded color value
     */
    public static int colorToRGB(float r, float g, float b)
    {
        int red = (int)(Math.max(0.f,Math.min(r,1.f)) * 255.f);
        int green = (int)(Math.max(0.f,Math.min(g,1.f)) * 255.f);
        int blue = (int)(Math.max(0.f,Math.min(b,1.f)) * 255.f);
        return (red << 16) | (green << 8) | blue;
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        
        resY = (int)Math.round((double)resX * (maxY - minY) / (maxX - minX));
        
        
        
        ctx =   ZMQ.context(2);
        Socket push = ctx.socket(ZMQ.PUSH);
        push.bind("inproc://workIn");
        //push.bind("tcp://*:10100");

        
        
        
        System.out.println("creating workers...");

        Worker workers[] = new Worker[4];
        for (int i = 0; i < workers.length; i++)
        {
            workers[i] = new Worker();
            workers[i].start();
        }
        Reader reader = new Reader();
        reader.start();
        
        System.out.println("issuing instructions...");
        MyOutputStream pushData = new MyOutputStream(8);
        DataOutputStream psh = new DataOutputStream(pushData);
        int numPixels = 0;
        pixelWidth = (maxX - minX) / (float)resX;
        pixelHeight = (maxY - minY) / (float)resY;
        for (int x = 0; x < resX; x++)
        {
            float fx = (float)((float)x / (float)(resX-1) * (maxX - minX) + minX);
            pushData.resetTo(0);
            psh.writeFloat(fx);
            for (int y = 0; y < resY; y++)
            {
                float fy = (float)((float)y / (float)(resY-1) * (maxY - minY) + minY);
                
                pushData.resetTo(4);
                psh.writeFloat(fy);
                psh.flush();
                
                pushData.write(push);
                numPixels++;
            }
        }
        
        reader.join();
        
        
        
        System.out.println("done...");
        System.in.read();
    }
}
