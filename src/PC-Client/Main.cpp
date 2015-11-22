#include "GUI/MainWindow.h"
#include <QMessageBox>
#include <QProgressDialog>
#include <QIcon>
#include <stdio.h>
#include "RES_Icon.h"
#include "tools/GTKTools.h"

int main(int argc, char *argv[])
{
	QApplication application(argc, argv);
	ADBTools* adb_tools = new ADBTools();
	
	Q_INIT_RESOURCE(Icon);
	string theme_name = GTKTools::get_current_theme_name();
	if(!theme_name.empty())
		QIcon::setThemeName(QString::fromStdString(GTKTools::get_current_theme_name()));
	else
		QIcon::setThemeName("hicolor");
	
	MainWindow main_window(adb_tools);
	main_window.show();
    return application.exec();
}
