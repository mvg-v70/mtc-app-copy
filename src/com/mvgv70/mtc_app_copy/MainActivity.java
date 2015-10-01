package com.mvgv70.mtc_app_copy;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Properties;

import com.mvgv70.utils.IniFile;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.RelativeLayout.LayoutParams;

public class MainActivity extends Activity {
	
  private class InterfaceButton extends ImageButton {

    public String appPath;
    public String appName;
		
    public InterfaceButton(Context context) 
    {
      super(context);
    }
  }

  private final static String INI_FILE_NAME = Environment.getExternalStorageDirectory().getPath()+"/mtc-app-copy/mtc-app-copy.ini";
  private final static String INTERFACE_CFG = Environment.getExternalStorageDirectory().getPath()+"/mtc-app-copy/interface.cfg";
  private final static String APPS_LIST = "apps-list.ini";
  private final static String SECTION_INTERFACE = "interface";
  private final static String SECTION_PACKAGE = "package";
  private final static String SECTION_SETTINGS = "settings";
  private final static String SECTION_COMMAND = "command";
  private IniFile ini_file = new IniFile();
  private Properties apps_list = new Properties();
  private LinearLayout lineLayout;
  private ProgressDialog pd;
  // ���������
  private String current_interface = "";
  private String backupDir;
  private boolean isReboot = false;
  private static final String TAG = "mtc-app-copy";
  // thread
  private InterfaceButton intButton = null;
  private ArrayList<String> commands = new ArrayList<String>(); 
    
  @Override
  protected void onCreate(Bundle savedInstanceState) 
  {
    super.onCreate(savedInstanceState);
    /*
    // �������� LinearLayout
    lineLayout = new LinearLayout(this);
    // ��������� ������������ ����������
    lineLayout.setOrientation(LinearLayout.VERTICAL);
    // ������� ScrollView
    ScrollView scrollView = new ScrollView(this);
    // ������� LayoutParams  
    LayoutParams lineLayoutParam = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    scrollView.addView(lineLayout);
    // ������������� scrollView ��� �������� ������� ������ 
    setContentView(lineLayout, lineLayoutParam);
    */
    setContentView(R.layout.activity_main);
    lineLayout = (LinearLayout)findViewById(R.id.container);
    Log.d(TAG,"version="+getString(R.string.app_version_name));
    // ���������
    try 
    {
      Log.d(TAG,"INI_FILE_NAME="+INI_FILE_NAME);
      // ������ ������������ �����
	  ini_file.loadFromFile(INI_FILE_NAME);
	  // dump ����������� ini-�����
	  ini_file.LogProps(TAG);
	  // ������ ��������
      readSettings();
      // �������� ������
      createInterfaces();
    } 
    catch (IOException e) 
    {
      Log.e(TAG,e.getMessage());
    }
  }
	
  // �������� ������
  private void createInterfaces()
  {
    Log.d(TAG,"-");
    String int_name;
    String line;
    // �������� ������
    Iterator<String> names = ini_file.enumLines(SECTION_INTERFACE);
    while (names.hasNext()) 
    {
      line = names.next();
      int_name = ini_file.getStringKey(line);
      InterfaceButton button = new InterfaceButton(this);
      button.appName = int_name;
      button.appPath = ini_file.getStringValue(line);
      Log.d(TAG,"appName="+button.appName);
      Log.d(TAG,"appPath="+button.appPath);
      // ������ ��������
      Drawable img = null;
      String picturePath = button.appPath+int_name+".png";
      Log.d(TAG,"image="+picturePath);
      img = Drawable.createFromPath(picturePath);
      button.setImageDrawable(img);
      // ��������� ����������
      LayoutParams buttonView = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
      lineLayout.addView(button, buttonView);
      button.setOnClickListener(onButtonClick);
    }
  }
		
  // ������ ������
  private void runCommands(String fileName)
  {
    String command;
    String template;
    Iterator<String> cmds = ini_file.enumLines(SECTION_COMMAND);
    while (cmds.hasNext()) 
    {
      template = cmds.next();
      // �������� %1 �� ��� ����� � �������
      command = template.replace("%1",fileName);
      executeCmd(command);
    }
  }
	
  // ������� ��������
  private void clearPreferences(String folder, String intName)
  {
    String command;
    String package_name;
    String dir = backupDir+current_interface;
    // �������� ������� backup, ���� �����
    if (!backupDir.isEmpty())
    {
      // ������ ������� backup
      command = "rm -R -f "+dir;
      executeCmd(command);
      // �������� ������
      command = "mkdir -p "+dir;
      executeCmd(command);
    }
    Iterator<String> packages = ini_file.enumLines(SECTION_PACKAGE);
    while (packages.hasNext()) 
    {
      package_name = packages.next();
      // �������� ���������
      if (!backupDir.isEmpty())
      {
        command = "cp -a /data/data/"+package_name+" "+dir;
        executeCmd(command);
      }
      // ������ ���������
      command = "rm -f -R /data/data/"+package_name+"/*";
      executeCmd(command);
      // ����������� ���������
      if (!backupDir.isEmpty())
      {
        command = "cp -a "+dir+"/"+package_name+" /data/data";
        executeCmd(command);
      }
    }
  }
	
  // ����������� ������
  private int copyInterface(String folder, String intName)
  {
    int i = 0;
    String app_name;
    String app_path;
    String file_name;
    String cpCmd;
    Log.d(TAG,"copyInterface: name="+intName+", folder="+folder);
    // ������ ������ ����������, �������, ���� ������ ���������� �� �����
    if (readAppsList(folder) == 0) return 0;
    // ������������� �������� ������� � read/write
    executeCmd("mount -o remount,rw /system");
    try
    {
      // �������� ������ ������
      @SuppressWarnings("unchecked")
      Enumeration<String> names = (Enumeration<String>)apps_list.propertyNames();
      while (names.hasMoreElements()) 
      {
        app_name = names.nextElement();
        app_path = apps_list.getProperty(app_name);
        file_name = app_path+app_name;
        // ��������� ������� ����������� cp
        cpCmd = "cp "+folder+app_name+" "+file_name;
        // ��������� �������
        executeCmd(cpCmd);
        i++;
        // ��������� ������ ������
        runCommands(file_name);
        // ������� dalvik-���
        clearDalvikCache(app_name);
      }
      // ������� ��������
      clearPreferences(folder,intName);
      // �������� ��� �������� ����������
      writeCurrentInterface(intName);
    }
    finally
    {
      // ������������� �������� ������� � read-only
      executeCmd("mount -o remount,ro /system");
      if (isReboot)
        // ������������
        executeCmd("reboot");
    }
    return i;
  }
	
  // ������ ������ ������
  private int readAppsList(String folder)
  {
    String fileName = folder+APPS_LIST;
    Log.d(TAG,"apps_list="+fileName);
    try
    {
      apps_list.load(new FileInputStream(fileName));
    } 
    catch (Exception e) 
    {
      Log.w(TAG,e.getMessage());
    }
    Log.d(TAG,"apps count: "+apps_list.size());
    return apps_list.size();
  }
  
  // �������� dalvik-����
  private void clearDalvikCache(String fileName)
  {
    String command = "rm -f /data/dalvik-cache/*"+fileName+"@classes.dex";
    executeCmd(command);
  }
	
  // ������ ������ ��������
  private void readSettings()
  {
    Log.d(TAG,"-");
    // current_interface
    readCurrentInterface();
    if (current_interface.isEmpty())
    {
      Log.w(TAG,"�� ��������� ������� ���������");
      Toast.makeText(this, "�� ��������� ������� ���������", Toast.LENGTH_SHORT).show();
    }
    else
      Log.d(TAG,"current_interface="+current_interface);
    // backup
    backupDir = ini_file.getValue(SECTION_SETTINGS, "backup");
    if (backupDir.isEmpty())
    {
      Log.w(TAG,"�� ����� backup �������");
      Toast.makeText(this, "�� ����� backup �������", Toast.LENGTH_SHORT).show();
    }
    else
      Log.d(TAG,"backup="+backupDir);
    // reboot
    isReboot = ini_file.getValue(SECTION_SETTINGS, "reboot").equals("1");
    Log.d(TAG,"reboot="+isReboot);
  }

  // ������ �������� ����������
  private void readCurrentInterface()
  {
    try
    {
      BufferedReader br = new BufferedReader(new FileReader(INTERFACE_CFG));
      try 
      {
        current_interface = br.readLine();
      }
      finally
      {
        br.close();
      }
    }
    catch (IOException e)
    {
      Log.w(TAG,e.getMessage());
    }
  }

  // ���������� �������� ���������
  private void writeCurrentInterface(String intName)
  {
    executeCmd("echo "+intName+" > "+INTERFACE_CFG);
  }
  
  // ���������� ������� �� ������ ����������� ���������
  private OnClickListener onButtonClick = new OnClickListener()  
  {
    public void onClick(View view)
    {
	  intButton = (InterfaceButton)view;
	  copyInterfaceAsync();
    }
  };

  // ����������� ������ � ����������� ������
  private void copyInterfaceAsync()
  {
    commands.clear();
    // progress dialog
    pd = new ProgressDialog(this);
    pd.setMessage("����������� ������");
    pd.setIndeterminate(true);
    pd.setCancelable(false);
    pd.setCanceledOnTouchOutside(false);
    pd.show();
    // thread
    thread.start();
  }

  Thread thread = new Thread()
  {
    public void run()
    {
      copyInterface(intButton.appPath, intButton.appName);
      // ������ ������ �� ����������
      runAllCmds();
      // ������� progress dialog � UI ������
      intButton.post(endExecution);
    }
  };
    
  private Runnable endExecution = new Runnable()
  {
    public void run() 
    {
      pd.dismiss();
      Log.d(TAG,"copying end");
    }
  };
  
  // ���������� ������� � ������ ����������
  private void executeCmd(String cmd)
  {
    commands.add(cmd);
  }
  
  // ���������� ������ � ������ � ������������ root
  private void runAllCmds()
  {
    // su (as root)
    Process process = null;
    InputStream err = null;
    try 
    {
      // ��������� su & exit
      commands.add(0,"su");
      commands.add("exit");
      // log
      for (String cmd : commands)
        Log.d(TAG,"> "+cmd);
      ProcessBuilder pb = new ProcessBuilder(commands);
      // pb.redirectErrorStream(true);
      process = pb.start();
      err = process.getErrorStream();
      // ������� ����������
      process.waitFor();
      Log.d(TAG,"waitFor");
      // ������ ������
      byte[] buffer = new byte[1024];
      int len = err.read(buffer);
      Log.d(TAG,"err.read");
      if (len > 0)
      {
        String errmsg = new String(buffer,0,len);
        Log.e(TAG,errmsg);
      }
      err.close();
      Log.d(TAG,"err.close");
    } 
    catch (IOException e) 
    {
      Log.e(TAG,"IOException: "+e.getMessage());
    }
    catch (InterruptedException e) 
   	{
      Log.e(TAG,"InterruptedException: "+e.getMessage());
    }
  }
  
  /*
  // ���������� ������ � ������ � ������������ root
  private void runAllCmds()
  {
    // su (as root)
    Process process = null;
    DataOutputStream os = null;
    InputStream err = null;
    try 
    {
      process = Runtime.getRuntime().exec("su");
      Log.d(TAG,"su running");
      os = new DataOutputStream(process.getOutputStream());
      err = process.getErrorStream();
      for (String cmd : commands)
      {
        os.writeBytes(cmd+" \n");
        Log.d(TAG,"> "+cmd);
      }
      os.writeBytes("exit \n");
      os.flush();
      Log.d(TAG,"os.flush");
      os.close();
      Log.d(TAG,"os.close");
      // ������ ������
      byte[] buffer = new byte[1024];
      int len = err.read(buffer);
      if (len > 0)
      {
        String errmsg = new String(buffer,0,len);
        Log.e(TAG,errmsg);
      }
      err.close();
      Log.d(TAG,"err.close");
      process.waitFor();
      Log.d(TAG,"waitFor");
    } 
    catch (IOException e) 
    {
      Log.e(TAG,"IOException: "+e.getMessage());
    }
    catch (InterruptedException e) 
   	{
      Log.e(TAG,"InterruptedException: "+e.getMessage());
    }
  }
  */
  
  /*
  // ���������� ������ � ������
  private void runAllCmds()
  {
    for (String cmd : commands) 
      runCmd(cmd);
  }
    
  //���������� ������� � ������������ root
  private boolean runCmd(String cmd)
  {
    Log.d(TAG,"> "+cmd);
    // su (as root)
    Process process = null;
    DataOutputStream os = null;
    InputStream err = null;
    boolean errflag = true;
   	try 
    {
      process = Runtime.getRuntime().exec("su");
      os = new DataOutputStream(process.getOutputStream());
      err = process.getErrorStream();
      os.writeBytes(cmd+" \n");
      os.writeBytes("exit \n");
      os.flush();
      os.close();
      process.waitFor();
      // ������ ������
      byte[] buffer = new byte[1024];
      int len = err.read(buffer);
      if (len > 0)
      {
        String errmsg = new String(buffer,0,len);
        Log.e(TAG,errmsg);
      } 
      else
        errflag = false;
    } 
   	catch (IOException e) 
   	{
      Log.e(TAG,"IOException: "+e.getMessage());
    }
    catch (InterruptedException e) 
   	{
      Log.e(TAG,"InterruptedException: "+e.getMessage());
    }
   	return (!errflag);
  }
  */
    
}
