package fun.aniss.coroshr.scripts;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.io.*;
import java.nio.file.*;
import javax.imageio.ImageIO;

/**
 * Generate standard Android density launchers and safe-zone adaptive foregrounds.
 * Usage: java -Djava.awt.headless=true scripts/GenerateAndroidIcons.java <logo.png> <res-dir>
 */
public final class GenerateAndroidIcons {
  private static final String[] DENSITIES={"mdpi","hdpi","xhdpi","xxhdpi","xxxhdpi"};
  private static final float[] SCALE={1f,1.5f,2f,3f,4f};
  private static BufferedImage img(int size){
    return new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
  }
  private static Graphics2D graphics(BufferedImage destination){
    Graphics2D g=destination.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
    return g;
  }
  private static BufferedImage scaled(BufferedImage source,int size,boolean round){
    BufferedImage destination=img(size);
    Graphics2D g=graphics(destination);
    if(round)g.setClip(new Ellipse2D.Double(0,0,size,size));
    g.drawImage(source,0,0,size,size,null);g.dispose();
    return destination;
  }
  private static BufferedImage foreground(BufferedImage source,int size){
    BufferedImage destination=img(size);
    Graphics2D g=graphics(destination);
    // The adaptive icon viewport is 108dp with a ~72dp safe center. Artwork
    // stays in that safe region on circular and squircle launchers alike.
    int art=Math.round(size*0.65f);
    int start=(size-art)/2;
    g.setClip(new RoundRectangle2D.Double(start,start,art,art,art*.19,art*.19));
    g.drawImage(source,start,start,art,art,null);
    g.dispose();
    return destination;
  }
  private static void write(Path path,BufferedImage image)throws IOException{
    Files.createDirectories(path.getParent());
    if(!ImageIO.write(image,"png",path.toFile()))
      throw new IOException("PNG encoder unavailable");
  }
  public static void main(String[] args)throws Exception{
    if(args.length!=2)throw new IllegalArgumentException("Usage: logo.png res-dir");
    BufferedImage source=ImageIO.read(Path.of(args[0]).toFile());
    if(source==null||source.getWidth()<256||source.getHeight()<256||
        source.getWidth()!=source.getHeight())throw new IOException("Expected square 256+px logo");
    Path res=Path.of(args[1]);
    for(int i=0;i<DENSITIES.length;i++){
      Path dir=res.resolve("mipmap-"+DENSITIES[i]);
      int legacy=Math.round(48*SCALE[i]),adaptive=Math.round(108*SCALE[i]);
      write(dir.resolve("ic_launcher.png"),scaled(source,legacy,false));
      write(dir.resolve("ic_launcher_round.png"),scaled(source,legacy,true));
      write(dir.resolve("ic_launcher_foreground.png"),foreground(source,adaptive));
    }
    // Solid background closely matching original corner palette prevents a
    // visible square edge when the image is rendered on a masked adaptive icon.
    int[] c={
      source.getRGB(0,0),source.getRGB(source.getWidth()-1,0),
      source.getRGB(0,source.getHeight()-1),
      source.getRGB(source.getWidth()-1,source.getHeight()-1)};
    int red=0,green=0,blue=0;
    for(int value:c){red+=(value>>16)&255;green+=(value>>8)&255;blue+=value&255;}
    String color=String.format("#%02X%02X%02X",red/4,green/4,blue/4);
    Path values=res.resolve("values/ic_launcher_background.xml");
    Files.createDirectories(values.getParent());
    Files.writeString(values,"<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"+
      "<resources><color name=\"ic_launcher_background\">"+color+"</color></resources>\n");
    Path v26=res.resolve("mipmap-anydpi-v26");
    Files.createDirectories(v26);
    String adaptiveIcon="<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"+
      "<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"+
      "  <background android:drawable=\"@color/ic_launcher_background\"/>\n"+
      "  <foreground android:drawable=\"@mipmap/ic_launcher_foreground\"/>\n"+
      "</adaptive-icon>\n";
    Files.writeString(v26.resolve("ic_launcher.xml"),adaptiveIcon);
    Files.writeString(v26.resolve("ic_launcher_round.xml"),adaptiveIcon);
    System.out.println("Generated 5 density icons + adaptive foreground, size="+
      source.getWidth()+"x"+source.getHeight()+", corner background="+color);
    for(int j=0;j<4;j++)System.out.println("Corner"+j+"=#"+String.format("%06X",c[j]&0xffffff));
  }
}
