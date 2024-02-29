FROM jlesage/baseimage-gui:ubuntu-20.04

ENV APP_NAME="QGIS-3.24.3"
ENV DISPLAY_WIDTH=1920
ENV DISPLAY_HEIGHT=1080
ENV USER_ID=996
ENV GROUP_ID=1009

# Install tools
RUN apt-get update && apt-get install -y\
 wget\
 gnupg\
 zip\
 libqt5gui5\
 binutils\
 software-properties-common\
 && apt-get clean && rm -rf /var/lib/apt/lists/*

# This fix is needed because F-TEP workers run on Linux Kernel 3.10
RUN strip --remove-section=.note.ABI-tag /usr/lib/x86_64-linux-gnu/libQt5Core.so.5

# Install key
RUN wget -qO - https://qgis.org/downloads/qgis-2021.gpg.key | gpg --no-default-keyring --keyring gnupg-ring:/etc/apt/trusted.gpg.d/qgis-archive.gpg --import
RUN chmod a+r /etc/apt/trusted.gpg.d/qgis-archive.gpg

# Add repo
RUN add-apt-repository "deb https://qgis.org/ubuntu $(lsb_release -c -s) main"

# Install qgis and supporting packages
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y\
 qgis-providers\
 qgis\
 qgis-plugin-grass\
 python-qgis\
 && apt-get clean && rm -rf /var/lib/apt/lists/*

# Disable QGIS settings import prompt
RUN mkdir -p /config/xdg/data/QGIS/QGIS3/profiles/default/QGIS \
 && echo -e "[migration]\nfirstRunVersionFlag=99999" >>/config/xdg/data/QGIS/QGIS3/profiles/default/QGIS/QGIS3.ini

COPY startapp.sh /startapp.sh
