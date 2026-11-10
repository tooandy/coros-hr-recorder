package fun.aniss.coroshr;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;

/** App-scoped appearance. Never changes system settings or foreground HR service. */
final class Appearance {
  static final int FOLLOW_SYSTEM=AppearanceModePolicy.SYSTEM,
    LIGHT=AppearanceModePolicy.LIGHT, DARK=AppearanceModePolicy.DARK;
  private static final String PREFS="appearance_v1", KEY="mode";
  private Appearance(){}
  static int mode(Context context){
    int mode=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
      .getInt(KEY,FOLLOW_SYSTEM);
    return AppearanceModePolicy.sanitize(mode);
  }
  static void save(Context context,int mode){
    if(mode<FOLLOW_SYSTEM||mode>DARK)throw new IllegalArgumentException("invalid appearance mode");
    context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putInt(KEY,mode).apply();
  }
  static boolean dark(int mode,int configurationUiMode){
    return AppearanceModePolicy.isDark(mode,
      (configurationUiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);
  }
  static boolean dark(Context context){
    return dark(mode(context),context.getResources().getConfiguration().uiMode);
  }
  static Palette palette(Context context){return new Palette(dark(context));}
  static void apply(Activity activity){
    boolean dark=dark(activity);
    activity.setTheme(dark?R.style.CorosThemeDark:R.style.CorosThemeLight);
    Window window=activity.getWindow();
    Palette colors=new Palette(dark);
    window.setStatusBarColor(colors.background);
    window.setNavigationBarColor(colors.surface);
    int flags=window.getDecorView().getSystemUiVisibility();
    flags&=~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
    if(Build.VERSION.SDK_INT>=26)flags&=~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
    if(!dark){
      flags|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
      if(Build.VERSION.SDK_INT>=26)flags|=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
    }
    window.getDecorView().setSystemUiVisibility(flags);
    if(Build.VERSION.SDK_INT>=29){
      window.setStatusBarContrastEnforced(false);
      window.setNavigationBarContrastEnforced(false);
    }
  }
  static final class Palette {
    final boolean dark;
    final int background,surface,text,muted,accent,softButton,selected,positiveAction,
      negativeAction,lowBattery,chartBackground,grid,axis,cursor;
    Palette(boolean dark){
      this.dark=dark;
      background=Color.parseColor(dark?"#111A22":"#F1F5F7");
      surface=Color.parseColor(dark?"#1D2B35":"#FFFFFF");
      text=Color.parseColor(dark?"#E9F2F6":"#18313D");
      muted=Color.parseColor(dark?"#AABFCC":"#60737A");
      accent=Color.parseColor(dark?"#48D4C5":"#007E78");
      softButton=Color.parseColor(dark?"#2A3F4B":"#E3ECEF");
      selected=Color.parseColor(dark?"#244B48":"#D8EFEA");
      positiveAction=Color.parseColor(dark?"#007E78":"#007E78");
      negativeAction=Color.parseColor(dark?"#B9493F":"#B9493F");
      lowBattery=Color.parseColor(dark?"#FF998C":"#C1493F");
      chartBackground=Color.parseColor(dark?"#15212A":"#F8FAFC");
      grid=Color.parseColor(dark?"#354A55":"#E1E8EC");
      axis=Color.parseColor(dark?"#B5C6CF":"#647684");
      cursor=Color.parseColor(dark?"#E0EAF0":"#424F62");
    }
  }
}
