
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

    static ZMQ.Context ctx;
    static boolean end = false;
    
    static float pixelWidth = 1.f,
                pixelHeight = 1.f;
    
    
    static void fatal(String msg)
    {
        
    
    }
    
    static void dump(byte[] data)
    {
        System.out.print(data.length+"_{");
        for (int i = 0; i < data.length; i++)
            System.out.print(" "+data[i]);
        System.out.println("}");
    }
    
    public static int getInt(byte[] b, int offset) 
    {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int shift = (4 - 1 - i) * 8;
            value += (b[i+offset] & 0x000000FF) << shift;
        }
        return value;
    }
    public static void putInt(int a, byte[] ret, int offset)
    {
        //byte[] ret = new byte[4];
        ret[offset+0] = (byte) (a & 0xFF);   
        ret[offset+1] = (byte) ((a >> 8) & 0xFF);   
        ret[offset+2] = (byte) ((a >> 16) & 0xFF);   
        ret[offset+3] = (byte) ((a >> 24) & 0xFF);
    }
    
    public static class MyOutputStream extends OutputStream {
        
        private byte[] data;
        private int writingAt = 0;
        public MyOutputStream(int size)
        {
            data = new byte[size];
        }
        public void     resetTo(int offset) {writingAt = offset; assert(writingAt >= 0 && writingAt < data.length);}

        public void     write(Socket s)
        {
            //dump(data);
            s.send(data,data.length);
        }
        
        @Override
        public void	close() {};
        @Override
        public void	flush() {};
        @Override
        public void	write(byte[] b)
        {
            System.out.print("writing raw ");
            dump(b);
            for (int i = 0; i < b.length && writingAt < data.length; i++)
                data[writingAt++] = b[i];
        }
        @Override
        public void	write(byte[] b, int off, int len)
        {
            System.out.print("writing offsetted "+off);
            dump(b);
            
            for (int i = 0; i < len && writingAt < data.length; i++)
                data[writingAt++] = b[off + i];
        }
        @Override
        public void	write(int b)
        {
            //System.out.println("writing byte "+b);
            data[writingAt++] = (byte)b;
        }
    }
    
    public static class MyInputStream extends InputStream {
        
        private byte[] data;
        private int readingAt = 0;
        public MyInputStream(int size)
        {
            data = new byte[size];
        }
        
        public void     resetToBeginning() {readingAt = 0;}
        public void     fill(Socket s)
        {
            int rc = s.recv(data,0,data.length,0);
            assert(rc == data.length);
            //dump(data);
            readingAt = 0;
        }
        
        
        @Override
        public int	available() {
            System.out.println("requested available data at "+(data.length - readingAt));
            return data.length - readingAt;
        }
        @Override
        public void	close() {System.out.println("close"); }
        @Override
        public void	mark(int readlimit)
        {System.out.println("mark"); }
        @Override
        public boolean	markSupported() {System.out.println("requested mark"); return false;}
        @Override
        public int	read()  {
            System.out.println("read(),"+readingAt+"/"+data.length);
            byte rs = data[readingAt++];
            System.out.println("read(),"+readingAt+"/"+data.length+"="+rs);
            return (int) rs;
        }
        
        public int      readInt() throws EOFException
        {
            if (readingAt+4 > data.length)
                throw new EOFException();
            return data[readingAt++] << 24 | (data[readingAt++] & 0xFF) << 16 | (data[readingAt++] & 0xFF) << 8 | (data[readingAt++] & 0xFF);
        }
        
        public float    readFloat() throws EOFException
        {
            return Float.intBitsToFloat(readInt());
        }
        
        
        @Override
        public int	read(byte[] b)
        {
            System.out.println("read field"); 
            int written = 0;
                 
            for (int i = 0; i+readingAt < data.length && i < b.length; i++)
            {
                b[i] = data[readingAt+i];
                written++;
            }
            readingAt += written;
            return written;
        }
        @Override
        public int	read(byte[] b, int off, int len)
        {
            System.out.println("read offsetted field"); 
            int written = 0;
                 
            for (int i = 0; i+readingAt < data.length && i < len; i++)
            {
                b[off + i] = data[readingAt+i];
                written++;
            }
            readingAt += written;
            return written;
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
   
        private static void color(int iterations, int maxIterations, float[] rgb)
        {
            float linear = (float)iterations / (float)maxIterations;
            rgb[0] = linear;
            rgb[1] = linear;
            rgb[2] = linear;
        }
        
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
            while (!end)
            {
                //int rc = pull.recv(data,8,0,0);
                stream.fill(pull);
                 if (!end)
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
                                     rs +=  iterate(x + fx, y + fy, 1000);
                                 }
                             
                             
                             //DataOutputStream psh = new DataOutputStream(pushData);
                             outStream.resetTo(0);
                             psh.writeFloat(x);
                             psh.writeFloat(y);
                             psh.writeFloat((float)(rs) / (float)(numSamples*numSamples) / 1000.f);
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
    
    public static float lerp(float v0, float v1, float x)
    {
        return v0 + (v1 - v0) * x;
    }
    
    public static class Reader extends Thread
    {
        int resX,resY;
        float minX,maxX,minY,maxY;
        public Reader(int resX, int resY, float minX, float maxX, float minY, float maxY)
        {
            this.resX = resX;
            this.resY = resY;
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
        }
        
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

                    float rel = (float)Math.log(1.f + it * 4.f);
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
        int resX = 2500;
        float minX = -2.f;
        float maxX = 1.f;
        float minY = -1.2f;
        float maxY = 1.2f;
        


        int resY = (int)Math.round((double)resX * (maxY - minY) / (maxX - minX));
        
        
        
        ctx =   ZMQ.context(2);
        Socket push = ctx.socket(ZMQ.PUSH);
        push.bind("inproc://workIn");
        //push.bind("tcp://*:10100");

        
        
        
        System.out.println("creating workers...");

        //Worker workers[] = new Worker[2];
        Worker workers[] = new Worker[4];
        for (int i = 0; i < workers.length; i++)
        {
            workers[i] = new Worker();
            workers[i].start();
        }
        Reader reader = new Reader(resX,resY,(float)minX,(float)maxX,(float)minY,(float)maxY);
        reader.start();
        
        float aspect = (float)resX / (float)resY;
        //byte[] pack = new byte[8];
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
                
                //System.out.println("sending pixel "+fx+", "+fy+" ...");
                pushData.resetTo(4);
                psh.writeFloat(fy);
                psh.flush();
                
                pushData.write(push);
                numPixels++;
            }
            //System.out.println("processing col "+x);
        }
        
        reader.join();
        
        
        
        System.out.println("done...");
        System.in.read();
    }
}
