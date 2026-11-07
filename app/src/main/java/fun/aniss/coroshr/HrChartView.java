package fun.aniss.coroshr;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Native, touch-driven, landscape-friendly HR comparison chart. */
public final class HrChartView extends View {
  public interface CursorListener { void onCursor(long coordinate); }
  static final int[] COLORS={
    0xFF008F89,0xFFE6673B,0xFF386AC6,0xFFB24496,0xFFAE8623,
    0xFF6B59C7,0xFF27834D,0xFFBD4B56,0xFF467C92,0xFF8F5B34
  };
  private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path line=new Path();
  private final RectF plot=new RectF();
  private final ScaleGestureDetector scaleDetector;
  private final GestureDetector gestureDetector;
  private List<HrSeries> shown=Collections.emptyList();
  private int mode=HrSeries.ABSOLUTE;
  private long domainMin=0,domainMax=60000,viewMin=0,viewMax=60000;
  private Long cursor;
  private CursorListener listener;
  private boolean scrub=false,scaling=false;
  private float density;
  private int hrMin=50,hrMax=200;

  public HrChartView(Context context){
    super(context);
    density=getResources().getDisplayMetrics().density;
    setLayerType(View.LAYER_TYPE_SOFTWARE,null);
    scaleDetector=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
      @Override public boolean onScaleBegin(ScaleGestureDetector d){scaling=true;return true;}
      @Override public boolean onScale(ScaleGestureDetector d){
        if(plot.width()<=0)return false;
        float relative=clamp((d.getFocusX()-plot.left)/plot.width(),0,1);
        zoom(d.getScaleFactor(),relative);return true;
      }
      @Override public void onScaleEnd(ScaleGestureDetector d){scaling=false;}
    });
    gestureDetector=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){
      @Override public boolean onDown(MotionEvent e){return true;}
      @Override public boolean onSingleTapUp(MotionEvent e){pick(e.getX());return true;}
      @Override public void onLongPress(MotionEvent e){scrub=true;pick(e.getX());}
      @Override public boolean onDoubleTap(MotionEvent e){zoom(2f,clamp((e.getX()-plot.left)/plot.width(),0,1));return true;}
      @Override public boolean onScroll(MotionEvent from,MotionEvent to,float distanceX,float distanceY){
        if(scaling)return false;
        if(scrub){pick(to.getX());return true;}
        pan(distanceX);return true;
      }
    });
  }
  private float dp(float v){return v*density;}
  private float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}
  public void setCursorListener(CursorListener l){listener=l;}
  public void setSeries(List<HrSeries> selected,int newMode,boolean overlap){
    shown=new ArrayList<>(selected);
    mode=newMode;
    domainMin=Long.MAX_VALUE;domainMax=Long.MIN_VALUE;
    hrMin=255;hrMax=0;
    for(HrSeries s:shown){
      domainMin=Math.min(domainMin,s.firstX(mode));
      domainMax=Math.max(domainMax,s.lastX(mode));
      for(int hr:s.heartRate){hrMin=Math.min(hrMin,hr);hrMax=Math.max(hrMax,hr);}
    }
    if(shown.isEmpty()){domainMin=0;domainMax=60000;hrMin=50;hrMax=200;}
    hrMin=Math.max(30,hrMin-12);hrMax=Math.min(255,hrMax+12);
    if(hrMax<=hrMin)hrMax=hrMin+20;
    if(domainMax-domainMin<10000)domainMax=domainMin+10000;
    fit(overlap);
  }
  public boolean fit(boolean overlap){
    long lo=domainMin,hi=domainMax;
    if(overlap&&shown.size()>1){
      for(HrSeries s:shown){
        lo=Math.max(lo,s.firstX(mode));
        hi=Math.min(hi,s.lastX(mode));
      }
      if(hi<=lo)return false;
    }
    if(hi-lo<10000){long mid=lo+(hi-lo)/2;lo=mid-5000;hi=mid+5000;}
    viewMin=lo;viewMax=hi;cursor=null;notifyCursor();
    invalidate();return true;
  }
  public void zoom(float factor,float anchor){
    if(shown.isEmpty()||plot.width()<=0)return;
    factor=clamp(factor,0.3f,4f);
    long span=Math.max(1000,viewMax-viewMin);
    long full=Math.max(10000,domainMax-domainMin);
    long next=Math.max(10000,Math.min(full,(long)(span/factor)));
    double pivot=viewMin+(double)span*anchor;
    long start=(long)(pivot-next*anchor);
    setViewport(start,next);
  }
  private void pan(float pixels){
    if(shown.isEmpty()||plot.width()<=0)return;
    long shift=(long)(pixels/plot.width()*(viewMax-viewMin));
    setViewport(viewMin+shift,viewMax-viewMin);
  }
  private void setViewport(long min,long span){
    long full=Math.max(10000,domainMax-domainMin);
    span=Math.max(10000,Math.min(span,full));
    min=Math.max(domainMin,Math.min(min,domainMax-span));
    viewMin=min;viewMax=min+span;
    invalidate();notifyCursor();
  }
  public long visibleStart(){return viewMin;}
  public long visibleEnd(){return viewMax;}
  public int alignment(){return mode;}
  public List<HrSeries> visibleSeries(){return new ArrayList<>(shown);}
  private void pick(float px){
    if(shown.isEmpty()||plot.width()<=0)return;
    float frac=clamp((px-plot.left)/plot.width(),0,1);
    cursor=viewMin+(long)(frac*(viewMax-viewMin));
    invalidate();notifyCursor();
  }
  private void notifyCursor(){if(listener!=null)listener.onCursor(cursor==null?(viewMin+viewMax)/2:cursor);}
  public String displayX(long x){
    if(mode==HrSeries.ABSOLUTE)
      return new SimpleDateFormat("MM-dd HH:mm:ss",Locale.getDefault()).format(new Date(x));
    if(x<0)return "-"+duration(-x);
    return duration(x);
  }
  private String duration(long ms){
    long sec=ms/1000;
    long h=sec/3600,m=sec/60%60,s=sec%60;
    if(mode==HrSeries.CLOCK)return String.format(Locale.getDefault(),"%02d:%02d:%02d",h,m,s);
    if(h>0)return String.format(Locale.getDefault(),"%d:%02d:%02d",h,m,s);
    return String.format(Locale.getDefault(),"%02d:%02d",sec/60,s);
  }
  private float yOf(int heart){return plot.bottom-(heart-hrMin)*(plot.height()/(float)(hrMax-hrMin));}
  private float xOf(long ms){return plot.left+(ms-viewMin)*plot.width()/(float)(viewMax-viewMin);}
  @Override protected void onDraw(Canvas c){
    super.onDraw(c);
    c.drawColor(0xFFF8FAFC);
    plot.set(dp(48),dp(18),getWidth()-dp(20),getHeight()-dp(39));
    if(plot.width()<=50||plot.height()<=50)return;
    p.setStyle(Paint.Style.FILL);p.setColor(0xFFFFFFFF);c.drawRoundRect(plot,dp(9),dp(9),p);
    p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
    p.setTextSize(dp(11));
    for(int i=0;i<=4;i++){
      int hr=hrMin+(hrMax-hrMin)*i/4;float y=yOf(hr);
      p.setColor(0xFFE1E8EC);p.setStrokeWidth(dp(1));c.drawLine(plot.left,y,plot.right,y,p);
      p.setColor(0xFF5D7180);c.drawText(""+hr,dp(6),y+dp(4),p);
    }
    for(int i=0;i<=5;i++){
      float x=plot.left+plot.width()*i/5;
      p.setColor(0xFFE6EBEF);p.setStrokeWidth(dp(1));c.drawLine(x,plot.top,x,plot.bottom,p);
      p.setColor(0xFF647684);
      String tick=displayX(viewMin+(viewMax-viewMin)*i/5);
      p.setTextSize(dp(10));
      float textW=p.measureText(tick);
      c.drawText(tick,Math.max(0,Math.min(getWidth()-textW,x-textW/2)),plot.bottom+dp(16),p);
    }
    c.save();c.clipRect(plot);
    for(int seriesIndex=0;seriesIndex<shown.size();seriesIndex++){
      HrSeries s=shown.get(seriesIndex);
      int idx=Arrays.binarySearch(s.time,viewMin+s.baseline(mode));
      if(idx<0)idx=-idx-1;
      idx=Math.max(0,idx-1);
      int last=(int)Math.min(s.time.length,idx+1);
      while(last<s.time.length&&s.sampleX(last,mode)<=viewMax)last++;
      int stride=Math.max(1,(last-idx)/(Math.max(1,(int)plot.width())*3));
      line.reset();boolean pen=false;int previousIndex=-1;
      for(int i=idx;i<last;i+=stride){
        long xValue=s.sampleX(i,mode);
        if(xValue>viewMax)break;
        float x=xOf(xValue),y=yOf(s.heartRate[i]);
        boolean gap=false;
        if(previousIndex>=0){
          for(int j=previousIndex+1;j<=i;j++){
            if(s.time[j]-s.time[j-1]>8000){gap=true;break;}
          }
        }
        if(!pen||gap)line.moveTo(x,y);
        else line.lineTo(x,y);
        previousIndex=i;pen=true;
      }
      p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2.2f));
      p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeCap(Paint.Cap.ROUND);
      p.setColor(COLORS[seriesIndex%COLORS.length]);c.drawPath(line,p);
      p.setStyle(Paint.Style.FILL);
    }
    if(cursor!=null&&cursor>=viewMin&&cursor<=viewMax){
      float x=xOf(cursor);
      p.setColor(0xFF424F62);p.setStrokeWidth(dp(1.5f));
      c.drawLine(x,plot.top,x,plot.bottom,p);
      for(int i=0;i<shown.size();i++){
        int bpm=shown.get(i).nearestBpm(cursor,mode,3000);
        if(bpm<0)continue;
        p.setStyle(Paint.Style.FILL);p.setColor(COLORS[i%COLORS.length]);
        c.drawCircle(x,yOf(bpm),dp(4),p);
      }
    }
    c.restore();
    if(shown.isEmpty()){
      p.setStyle(Paint.Style.FILL);p.setColor(0xFF697A85);p.setTextSize(dp(17));
      c.drawText(getContext().getString(R.string.ui_select_a_session_or_import_fit_data),plot.left+dp(22),plot.centerY(),p);
    }
  }
  @Override public boolean onTouchEvent(MotionEvent e){
    scaleDetector.onTouchEvent(e);
    if(e.getPointerCount()>1){scaling=true;return true;}
    if(e.getActionMasked()==MotionEvent.ACTION_DOWN){scrub=false;scaling=false;}
    if(!scaling)gestureDetector.onTouchEvent(e);
    if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){
      scrub=false;scaling=false;
    }
    return true;
  }
}
