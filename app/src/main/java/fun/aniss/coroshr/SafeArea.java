package fun.aniss.coroshr;

import android.app.Activity;
import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;

/** Keep both portrait controls and landscape charts clear of system bars/camera cutouts. */
final class SafeArea {
  private SafeArea(){}
  static void apply(Activity activity,View root){
    final int left=root.getPaddingLeft(),top=root.getPaddingTop();
    final int right=root.getPaddingRight(),bottom=root.getPaddingBottom();
    if(Build.VERSION.SDK_INT>=28){
      WindowManager.LayoutParams params=activity.getWindow().getAttributes();
      params.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
      activity.getWindow().setAttributes(params);
    }
    root.setOnApplyWindowInsetsListener((v,insets)->{
      int l,t,r,b;
      if(Build.VERSION.SDK_INT>=30){
        Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        l=safe.left;t=safe.top;r=safe.right;b=safe.bottom;
      }else{
        l=insets.getSystemWindowInsetLeft();t=insets.getSystemWindowInsetTop();
        r=insets.getSystemWindowInsetRight();b=insets.getSystemWindowInsetBottom();
        if(Build.VERSION.SDK_INT>=28&&insets.getDisplayCutout()!=null){
          l=Math.max(l,insets.getDisplayCutout().getSafeInsetLeft());
          t=Math.max(t,insets.getDisplayCutout().getSafeInsetTop());
          r=Math.max(r,insets.getDisplayCutout().getSafeInsetRight());
          b=Math.max(b,insets.getDisplayCutout().getSafeInsetBottom());
        }
      }
      v.setPadding(left+l,top+t,right+r,bottom+b);
      return insets;
    });
    root.requestApplyInsets();
  }
}
