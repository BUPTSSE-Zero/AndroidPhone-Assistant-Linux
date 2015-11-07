#include "ADBTools.h"
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include "../tools/CommandTools.h"
#include "../tools/SocketTools.h"
#include <unistd.h>
#define MAX_SIZE 1024

using namespace std;

const char* ADBTools::ADB_PATH  = "./AndroidTools/adb";
const char* ADBTools::BUILD_PROP_PATH = "/system/build.prop";

ADBTools::ADBTools()
{
    is_running = false;
    connected_flag = false;
}

string ADBTools::parse_value(const string& key_val_pair)
{
    int start, end;
    int i;
    for(i = key_val_pair.length() - 1; i >= 0 && key_val_pair[i] == ' '; i--);
    end = i;
    for(; i >= 0; i--)
    {
        if(key_val_pair[i] == '=')
        {
            for(start = i + 1; key_val_pair[start] == ' '; start++);
            //printf("value=%s\n", key_val_pair.substr(start, end - start + 1).c_str());
            return key_val_pair.substr(start, end - start + 1);
        }
    }
    return "";
}

string ADBTools::parse_key(const string& key_val_pair)
{
    int start,end;
    int i;
    for(i = 0; i < key_val_pair.length() && key_val_pair[i] == ' '; i++);
    start = i;
    for(; i < key_val_pair.length(); i++)
    {
        if(key_val_pair[i] == '=')
        {
            for(end = i - 1; key_val_pair[end] == ' '; end--);
            return key_val_pair.substr(start, end - start + 1);
        }
    }
    return "";
}

void ADBTools::exec_adb_server_startup(ADBTools* data)
{
    char startup_command[MAX_SIZE];
    sprintf(startup_command, "%s %s", ADB_PATH, "start-server");
    vector<string> test_result;
    if(CommandTools::exec_command(startup_command, test_result))
        data->is_running = true;
    else
        data->is_running = false;
    g_cond_signal(&data->task_cond);
}

ADBTools::ADBStartError ADBTools::start_adb_server()
{
    GMutex task_mutex;
    g_mutex_init(&task_mutex);
    g_cond_init(&task_cond);
    GThread* task_thread = g_thread_new("ADB-startup", (GThreadFunc)exec_adb_server_startup, this);
    gboolean ret = g_cond_wait_until(&task_cond, &task_mutex, g_get_monotonic_time() + STARTUP_TIME_OUT * G_TIME_SPAN_SECOND);
    g_thread_unref(task_thread);
    task_thread = NULL;
    if(!ret)
    {
        printf("ADB Server startup timeout.\n");
        return ADB_START_TIMEOUT;
    }
    else if(is_running)
        return ADB_START_SUCCESSFULLY;

    if(!SocketTools::is_local_port_available(5037)){
        printf("5037 port is unavailable.\n");
        return ADB_START_PORT_UNAVAILABLE;
    }
    return ADB_START_UNKNOWN_ERROR;
}

void ADBTools::stop_adb_server()
{
    if(connected_flag)
        close(connect_socket);
    char command[MAX_SIZE];
    sprintf(command, "%s kill-server", ADB_PATH);
    system(command);
    is_running = false;
    connected_flag = false;
}

bool ADBTools::install_apk(const char* apk_file_path)
{
    if(!is_running)
        return false;
    char install_command[MAX_SIZE];
    sprintf(install_command, "%s %s %s", ADB_PATH, "install", apk_file_path);
    if(system(install_command) == 0)
        return true;
    return false;
}

string ADBTools::get_phone_manufacturer()
{
    if(!is_running)
        return "";
    vector<string> result;
    char command[MAX_SIZE];
    sprintf(command, "%s shell cat %s|grep ro.product.manufacturer", ADB_PATH, BUILD_PROP_PATH);
    if(!CommandTools::exec_command(command, result))
        return "";
    int i;
    for(i = 0; i < result.size(); i++)
    {
        string key = parse_key(result[i]);
        if(key == "ro.product.manufacturer")
            return parse_value(result[i]);
    }
    return "";
}

string ADBTools::get_phone_model()
{
    if(!is_running)
        return "";
    vector<string> result;
    char command[MAX_SIZE];
    sprintf(command, "%s shell cat %s|grep ro.product.model", ADB_PATH, BUILD_PROP_PATH);
    if(!CommandTools::exec_command(command, result))
        return "";
    int i;
    for(i = 0; i < result.size(); i++)
    {
        string key = parse_key(result[i]);
        if(key == "ro.product.model")
            return parse_value(result[i]);
    }
    return "";
}

bool ADBTools::connect_to_phone()
{
    if(!is_running)
        return false;
    if(connected_flag)
        return true;
    int i;
    for(i = 20000; i <= 65535; i++)
    {
        if(!SocketTools::is_local_port_available(i))
            continue;
        printf("Available port:%d\n", i);
        break;
    }
    if(i > 65535)
        return false;
    char command[MAX_SIZE];
    sprintf(command, "%s forward tcp:%d tcp:%d", ADB_PATH, i, ANDROID_SERVER_PORT);
    if(system(command) != 0)
        return false;
    connect_socket = SocketTools::create_socket();
    if(connect_socket < 0)
        return false;
    if(!SocketTools::connect_to_server(connect_socket, "127.0.0.1", i))
        return false;
    if(!SocketTools::send_msg(connect_socket, "Hello,Phone!"))
    {
        close(connect_socket);
        return false;
    }
    string hello_msg;
    if(!SocketTools::receive_msg(connect_socket, hello_msg))
    {
        close(connect_socket);
        return false;
    }
    printf("Receive from phone:%s\n", hello_msg.c_str());
    connected_flag = true;
    g_thread_new("Phone-Assistant-Daemon", (GThreadFunc)exec_socket_daemon, this);
    return true;
}

void ADBTools::exec_socket_daemon(ADBTools* data)
{
    while(true)
    {
        if(!SocketTools::send_urgent_data(data->connect_socket))
        {
            close(data->connect_socket);
            data->connected_flag = false;
            return;
        }
        sleep(2);
    }
}
