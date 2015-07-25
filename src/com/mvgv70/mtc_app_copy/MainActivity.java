package com.mvgv70.mtc_app_copy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Properties;

import com.mvgv70.utils.IniFile;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.RelativeLayout.LayoutParams;

public class MainActivity extends Activity {
	
	private final static String INI_FILE_NAME = Environment.getExternalStorageDirectory().getPath()+"/mtc-app-copy/mtc-app-copy.ini";
	private final static String INTERFACE_CFG = Environment.getExternalStorageDirectory().getPath()+"/mtc-app-copy/interface.cfg";
	private final static String APPS_LIST = Environment.getExternalStorageDirectory().getPath()+"/mtc-app-copy/%1/apps-list.ini";
	private final static String SECTION_INTERFACE = "interface";
	private final static String SECTION_PACKAGE = "package";
	private final static String SECTION_SETTINGS = "settings";
	private final static String SECTION_COMMAND = "command";
	private IniFile ini_file = new IniFile();
	private Properties apps_list = new Properties();
	private LinearLayout lineLayout;
	// ���������
	private String current_interface = "";
	private String backupDir;
	private boolean isReboot = false;
	private static final String TAG = "mtc-app-copy";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) 
	{
	  super.onCreate(savedInstanceState);
	  // �������� LinearLayout
      lineLayout = new LinearLayout(this);
      // ��������� ������������ ����������
      lineLayout.setOrientation(LinearLayout.VERTICAL);
      // ������� LayoutParams  
      LayoutParams lineLayoutParam = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT); 
      // ������������� lineLayout ��� �������� ������� ������ 
      setContentView(lineLayout, lineLayoutParam);
      // ���������
      try 
      {
    	Log.d(TAG,"INI_FILE_NAME="+INI_FILE_NAME);
    	// ������ ������������ �����
		ini_file.loadFromFile(INI_FILE_NAME);
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

	// ���������� ������� �� ������ ����������� ���������
	private OnClickListener onButtonClick = new OnClickListener()  
	{
	  public void onClick(View view)
      {
	    InterfaceButton button = (InterfaceButton)view;
	    copyInterface(button.appPath, button.appName);
      }
	};
	
	// �������� ������
	private void createInterfaces()
	{
	  String int_name;
	  String line;
	  Iterator<String> names = ini_file.enumLines(SECTION_INTERFACE);
	  // �������� ������
	  while (names.hasNext()) 
      {
		line = names.next();
		int_name = ini_file.getStringKey(line);
		InterfaceButton button = new InterfaceButton(this);
	    button.appName = int_name;
	    button.appPath = ini_file.getStringValue(line);
	    // ������ ��������
	    Drawable img = null;
	    String picturePath = button.appPath+int_name+".png";
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
	  String dir = backupDir+current_interface+"/";
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
          command = "cp -a /data/data/"+package_name+"/ "+dir;
          executeCmd(command);
        }
        // ������ ���������
        command = "rm -f -R /data/data/"+package_name+"/";
        executeCmd(command);
        // ����������� ���������
        if (!backupDir.isEmpty())
        {
          command = "cp -a "+dir+" /data/data/"+package_name+"/";
          executeCmd(command);
        }
      }
    }
	
	// ����������� ������
	private void copyInterface(String folder, String intName)
	{
	  int i = 0;
	  String app_name;
	  String app_path;
	  String file_name;
	  String cpCmd;
	  Log.d(TAG,"copyInterface: name="+intName+", folder="+folder);
      // ������ ������ ����������, �������, ���� ������ ���������� �� �����
	  if (readAppsList(intName) == 0) return;
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
	      /*
	      File f = new File(app_path);
	      if (f.isDirectory())
	    	// ������ ������� ��� �����������
	        file_name = app_path+app_name;
	      else
	    	// ������� ��� ����� ��� �����������
            file_name = app_path;
          */
	      // ��������� ������� ����������� cp
          cpCmd = "cp "+folder+app_name+" "+file_name;
          // ��������� �������
          if (executeCmd(cpCmd))
          {
            i++;
            // ��������� ������ ������
            runCommands(file_name);
            // ������� dalvik-���
            clearDalvikCache(file_name);
          }
        }
	    // ������� ��������
	    clearPreferences(folder,intName);
	    // �������� ��� �������� ����������
	    writeCurrentInterface();
	  }
	  finally
	  {
	    // ������������� �������� ������� � read-only
	    executeCmd("mount -o remount,ro /system");
	    Toast.makeText(this, "����������� "+i+" ������", Toast.LENGTH_SHORT).show();
	    if (isReboot)
	      // ������������
	      executeCmd("reboot");
	    // ��������� ���������
	    finish();
	  }
	}
	
	// ������ ������ ������
	private int readAppsList(String intName)
	{
	  String fileName = APPS_LIST.replace("%1",intName);
      Log.d(TAG,"APPS_LIST="+fileName);
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
	private void writeCurrentInterface()
	{
      try
      {
        BufferedWriter br = new BufferedWriter(new FileWriter(INTERFACE_CFG));
        try 
        {
          br.write(current_interface);
          br.newLine();
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
	
	// ���������� ������� � ������������ root
	private boolean executeCmd(String cmd)
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

}
