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
	// настройки
	private String current_interface = "";
	private String backupDir;
	private boolean isReboot = false;
	private static final String TAG = "mtc-app-copy";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) 
	{
	  super.onCreate(savedInstanceState);
	  // создание LinearLayout
      lineLayout = new LinearLayout(this);
      // установим вертикальную ориентацию
      lineLayout.setOrientation(LinearLayout.VERTICAL);
      // создаем LayoutParams  
      LayoutParams lineLayoutParam = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT); 
      // устанавливаем lineLayout как корневой элемент экрана 
      setContentView(lineLayout, lineLayoutParam);
      // настройки
      try 
      {
    	Log.d(TAG,"INI_FILE_NAME="+INI_FILE_NAME);
    	// чтение настроечного файла
		ini_file.loadFromFile(INI_FILE_NAME);
		// чтение настроек
	    readSettings();
        // создание кнопок
	    createInterfaces();
      } 
      catch (IOException e) 
      {
		Log.e(TAG,e.getMessage());
      }
	}

	// обработчик нажатия на кнопку копирования интерфеса
	private OnClickListener onButtonClick = new OnClickListener()  
	{
	  public void onClick(View view)
      {
	    InterfaceButton button = (InterfaceButton)view;
	    copyInterface(button.appPath, button.appName);
      }
	};
	
	// создание кнопок
	private void createInterfaces()
	{
	  String int_name;
	  String line;
	  Iterator<String> names = ini_file.enumLines(SECTION_INTERFACE);
	  // создание кнопок
	  while (names.hasNext()) 
      {
		line = names.next();
		int_name = ini_file.getStringKey(line);
		InterfaceButton button = new InterfaceButton(this);
	    button.appName = int_name;
	    button.appPath = ini_file.getStringValue(line);
	    // грузим картинку
	    Drawable img = null;
	    String picturePath = button.appPath+int_name+".png";
	    img = Drawable.createFromPath(picturePath);
	    button.setImageDrawable(img);
	    // параметры размещения
	    LayoutParams buttonView = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
	    lineLayout.addView(button, buttonView);
	    button.setOnClickListener(onButtonClick);
      }
	}
		
	// запуск команд
	private void runCommands(String fileName)
	{
	  String command;
	  String template;
	  Iterator<String> cmds = ini_file.enumLines(SECTION_COMMAND);
	  while (cmds.hasNext()) 
      {
		template = cmds.next();
		// земеняем %1 на имя файлы в команде
	    command = template.replace("%1",fileName);
	    executeCmd(command);
      }
	}
	
	// очистка настроек
	private void clearPreferences(String folder, String intName)
	{
	  String command;
	  String package_name;
	  String dir = backupDir+current_interface+"/";
	  // создадим каталог backup, если нужно
      if (!backupDir.isEmpty())
	  {
		// удалим каталог backup
    	command = "rm -R -f "+dir;
		executeCmd(command);
		// создадим заново
		command = "mkdir -p "+dir;
        executeCmd(command);
      }
      Iterator<String> packages = ini_file.enumLines(SECTION_PACKAGE);
      while (packages.hasNext()) 
      {
        package_name = packages.next();
        // сохраним настройки
        if (!backupDir.isEmpty())
        {
          command = "cp -a /data/data/"+package_name+"/ "+dir;
          executeCmd(command);
        }
        // удалим настройки
        command = "rm -f -R /data/data/"+package_name+"/";
        executeCmd(command);
        // восстановим настройки
        if (!backupDir.isEmpty())
        {
          command = "cp -a "+dir+" /data/data/"+package_name+"/";
          executeCmd(command);
        }
      }
    }
	
	// копирование файлов
	private void copyInterface(String folder, String intName)
	{
	  int i = 0;
	  String app_name;
	  String app_path;
	  String file_name;
	  String cpCmd;
	  Log.d(TAG,"copyInterface: name="+intName+", folder="+folder);
      // чтение списка приложений, выходим, если список приложений не задан
	  if (readAppsList(intName) == 0) return;
	  // перемонтируем файловую систему в read/write
	  executeCmd("mount -o remount,rw /system");
	  try
	  {
	    // копируем список файлов
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
	    	// указан каталог для копирования
	        file_name = app_path+app_name;
	      else
	    	// указано имя файла для копирования
            file_name = app_path;
          */
	      // формируем команду копирования cp
          cpCmd = "cp "+folder+app_name+" "+file_name;
          // выполняем команду
          if (executeCmd(cpCmd))
          {
            i++;
            // выполняем список команд
            runCommands(file_name);
            // удаляем dalvik-кеш
            clearDalvikCache(file_name);
          }
        }
	    // очистка настроек
	    clearPreferences(folder,intName);
	    // запомним тип текущего интерфейса
	    writeCurrentInterface();
	  }
	  finally
	  {
	    // перемонтируем файловую систему в read-only
	    executeCmd("mount -o remount,ro /system");
	    Toast.makeText(this, "скопировано "+i+" файлов", Toast.LENGTH_SHORT).show();
	    if (isReboot)
	      // перезагрузка
	      executeCmd("reboot");
	    // закрываем программу
	    finish();
	  }
	}
	
	// чтение списка файлов
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
	
	// удаление dalvik-кеша
	private void clearDalvikCache(String fileName)
	{
	  String command = "rm -f /data/dalvik-cache/*"+fileName+"@classes.dex";
	  executeCmd(command);
	}
	
	// чтение списка настроек
	private void readSettings()
	{
      // current_interface
	  readCurrentInterface();
      if (current_interface.isEmpty())
      {
    	Log.w(TAG,"не определен текущий интерфейс");
        Toast.makeText(this, "не определен текущий интерфейс", Toast.LENGTH_SHORT).show();
      }
      else
    	Log.d(TAG,"current_interface="+current_interface);
      // backup
      backupDir = ini_file.getValue(SECTION_SETTINGS, "backup");
      if (backupDir.isEmpty())
      {
    	Log.w(TAG,"не задан backup каталог");
        Toast.makeText(this, "не задан backup каталог", Toast.LENGTH_SHORT).show();
      }
      else
      	Log.d(TAG,"backup="+backupDir);
      // reboot
      isReboot = ini_file.getValue(SECTION_SETTINGS, "reboot").equals("1");
      Log.d(TAG,"reboot="+isReboot);
	}
	
	// чтение текущего интерфейса
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
	
	// сохранение текущего интерфеса
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
	
	// выполнение команды с привилегиями root
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
	    // анализ ошибок
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
