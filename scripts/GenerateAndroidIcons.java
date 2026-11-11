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
    else g.setClip(new RoundRectangle2D.Double(0,0,size,size,size*.38,size*.38));
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
    // The supplied artwork contains a white outline around its blue tile.
    // Crop inside that outline, then apply the launcher mask ourselves;
    // otherwise the white ring survives both legacy and adaptive icons.
    int trim=Math.round(source.getWidth()*0.035f);
    source=source.getSubimage(trim,trim,source.getWidth()-2*trim,source.getHeight()-2*trim);
    Path res=Path.of(args[1]);
    for(int i=0;i<DENSITIES.length;i++){
      Path dir=res.resolve("mipmap-"+DENSITIES[i]);
      int legacy=Math.round(48*SCALE[i]),adaptive=Math.round(108*SCALE[i]);
      write(dir.resolve("ic_launcher.png"),scaled(source,legacy,false));
      write(dir.resolve("ic_launcher_round.png"),scaled(source,legacy,true));
      write(dir.resolve("ic_launcher_foreground.png"),foreground(source,adaptive));
    }
    // Use the actual dark teal edge (NOT the old white canvas) behind
    // the masked icon, so Android launcher shapes never expose white rims.
    int edge=source.getRGB(1,source.getHeight()/2);
    String color=String.format("#%06X",edge&0xffffff);
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

  }
}
