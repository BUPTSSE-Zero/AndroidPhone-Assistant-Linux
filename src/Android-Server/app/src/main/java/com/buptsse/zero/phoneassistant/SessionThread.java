package com.buptsse.zero.phoneassistant;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.buptsse.zero.phoneassistant.phoneinfoprovider.AppInfo;
import com.buptsse.zero.phoneassistant.phoneinfoprovider.AppInfoReader;
import com.buptsse.zero.phoneassistant.phoneinfoprovider.ContactInfo;
import com.buptsse.zero.phoneassistant.phoneinfoprovider.ContactInfoReader;
import com.buptsse.zero.phoneassistant.phoneinfoprovider.SMSInfo;
import com.buptsse.zero.phoneassistant.phoneinfoprovider.SMSInfoReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Scanner;

/**
 * Created by buptsse-zero on 11/7/15.
 */
public class SessionThread extends Thread
{
    private Socket sessionSocket;
    private PrintStream socketOutput = null;
    private Scanner socketInput = null;
    private Context context;

    private final String MESSAGE_READ_CONTACTS = "msg:read_contacts";
    private final String MESSAGE_READ_SMS = "msg:read_sms";
    private final String MESSAGE_READ_APP_INFO = "msg:read_app_info";
    private final String MESSAGE_HEADER_GET_APP_APK_FILE = "msg:get_app_apk_file";
    private final String MESSAGE_READ_PHONE_INFO = "msg:read_phone_info";

    SessionThread(Socket sessionSocket, Context context)
    {
        this.sessionSocket = sessionSocket;
        this.context = context;
    }

    @Override
    public void run() {
        super.run();
        try{
            sessionSocket.setTcpNoDelay(true);
            socketOutput = new PrintStream(sessionSocket.getOutputStream());
            socketInput = new Scanner(sessionSocket.getInputStream());;
            new SessionDaemonThread(sessionSocket).start();
            while (true){
                if(sessionSocket.isClosed()){
                    Log.i("AndroidServerSession", "Closed");
                    return;
                }
                if(socketInput.hasNext()) {
                    String receiveMsg = socketInput.next();
                    Log.i("AndroidServerReceive", receiveMsg);
                    if(MESSAGE_READ_CONTACTS.equals(receiveMsg)) {
                        sendContactsList(new ContactInfoReader(context).getAllContacts());
                    }
                    else if(MESSAGE_READ_SMS.equals(receiveMsg)) {
                        sendSMSInfoList(new SMSInfoReader(context).getAllSMS());
                    }
                    else if(MESSAGE_READ_APP_INFO.equals(receiveMsg)) {
                        sendAppInfoList(new AppInfoReader(context).getAllAppInfo());
                    }
                    else if(MESSAGE_READ_PHONE_INFO.equals(receiveMsg)){
                        sendPhoneInfo();
                    }
                    else if(receiveMsg != null && receiveMsg.startsWith(MESSAGE_HEADER_GET_APP_APK_FILE)) {
                        sendFile(new AppInfoReader(context).getAppAPKFile(receiveMsg.substring(MESSAGE_HEADER_GET_APP_APK_FILE.length() + 1)));
                    }
                }
                else{
                    sleep(1000);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
            Log.i("AndroidServerSession", "Closed");
            try {
                sessionSocket.close();
            }catch (IOException ioe){
                e.printStackTrace();
            }
            return;
        }
    }

    private void sendPhoneInfo()
    {
        socketOutput.print("PhoneManufacturer=" + Build.MANUFACTURER);
        waitReply();
        socketOutput.print("PhoneModel=" + Build.MODEL);
        waitReply();
        socketOutput.print("AndroidVersion=" + Build.VERSION.RELEASE);
        waitReply();
        socketOutput.print("Product=" + Build.PRODUCT);
        waitReply();
        socketOutput.print("AndroidSDK=" + Build.VERSION.SDK_INT);
        waitReply();
    }

    private void sendContactsList(ArrayList<ContactInfo> contactInfoList) {
        socketOutput.print("ContactListSize=" + contactInfoList.size());
        waitReply();
        for(int i = 0; i < contactInfoList.size(); i++)
        {
            socketOutput.print("ContactID=" + contactInfoList.get(i).getContactID());
            waitReply();
            socketOutput.print("DisplayName=" + contactInfoList.get(i).getDisplayName());
            waitReply();
            socketOutput.print("PhoneNumber=" + contactInfoList.get(i).getPhoneNumber());
            waitReply();
        }
    }

    private void waitReply()
    {
        while(true)
        {
            if(socketInput.hasNext()) {
                socketInput.next();
                return;
            }else{
                try {
                    sleep(300);
                }catch (InterruptedException e){
                    e.printStackTrace();
                }
            }
        }
    }

    private void sendSMSInfoList(ArrayList<SMSInfo> SMSInfoList)
    {
        socketOutput.print("SMSListSize=" + SMSInfoList.size());
        waitReply();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for(int i = 0; i < SMSInfoList.size(); i++)
        {
            socketOutput.print("Date=" + SMSInfoList.get(i).getDate());
            waitReply();
            Date d = new Date(SMSInfoList.get(i).getDate());
            String dateStr = dateFormat.format(d);
            socketOutput.print("DateStr=" + dateStr);
            waitReply();
            socketOutput.print("PhoneNumber=" + SMSInfoList.get(i).getPhoneNumber());
            waitReply();
            socketOutput.print("SMSBody=" + SMSInfoList.get(i).getSMSBody());
            waitReply();
            socketOutput.print("SMSType=" + SMSInfoList.get(i).getType());
            waitReply();
        }
    }

    private void sendAppInfoList(ArrayList<AppInfo> AppInfoList) throws IOException
    {
        socketOutput.print("AppListSize=" + AppInfoList.size());
        waitReply();
        for(int i = 0; i < AppInfoList.size(); i++)
        {
            socketOutput.print("AppName=" + AppInfoList.get(i).getAppName());
            waitReply();
            socketOutput.print("AppPackage=" + AppInfoList.get(i).getAppPackageName());
            waitReply();
            socketOutput.print("AppVersion=" + AppInfoList.get(i).getAppVersion());
            waitReply();
            socketOutput.print("AppSystemFlag=" + AppInfoList.get(i).isSystemApp());
            waitReply();
            socketOutput.print("AppSize=" + AppInfoList.get(i).getAppSize());
            waitReply();
            if (AppInfoList.get(i).getAppIconBytes() == null || AppInfoList.get(i).getAppIconBytes().length == 0) {
                socketOutput.print("AppIconBytesLength=0");
                waitReply();
            } else {
                socketOutput.print("AppIconBytesLength=" + AppInfoList.get(i).getAppIconBytes().length);
                waitReply();
                try {
                    socketOutput.write(AppInfoList.get(i).getAppIconBytes());
                }catch (IOException e) {
                    throw e;
                }
                waitReply();
            }
        }
    }

    private void sendFile(File file) throws IOException
    {
        long fileLength = 0;
        if(file != null && file.exists())
            fileLength = file.length();
        if(fileLength <= 0)
        {
            socketOutput.print("FileLength=0");
            waitReply();
            return;
        }
        socketOutput.print("FileLength=" + fileLength);
        waitReply();
        long sendBytes = 0;
        try{
            FileInputStream fis = new FileInputStream(file);
            byte[] buf = new byte[20480 - 10];
            while (true){
                int readBytes = fis.read(buf);
                socketOutput.write(buf, 0, readBytes);
                sendBytes += readBytes;
                if(sendBytes >= fileLength)
                    break;
            }
        }catch (IOException e){
            waitReply();
            throw e;
        }
        waitReply();
    }

}
