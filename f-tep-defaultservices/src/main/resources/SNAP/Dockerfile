FROM jlesage/baseimage-gui:ubuntu-20.04

ENV APP_NAME="SNAP9-Sentinel-Toolbox"
ENV DISPLAY_WIDTH=1920
ENV DISPLAY_HEIGHT=1080
ENV USER_ID=996
ENV GROUP_ID=1009
ENV DEBIAN_FRONTEND=noninteractive

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

ENV \
  TBX="esa-snap_sentinel_unix_9_0_0.sh" \
  STB_URL="https://download.esa.int/step/snap/9.0/installers" 

# Install tools
RUN apt-get update && apt-get install -y\
 wget\
 libfreetype6\
 libxext6\
 libxrender1\
 libqt5gui5\
 zip\
 binutils\ 
 software-properties-common\
 nano\
 && apt-get clean && rm -rf /var/lib/apt/lists/*

# Download SNAP installer
RUN wget $STB_URL/$TBX && chmod +x $TBX

# Create installation parameter file
# Template created by manually running the installer in console mode
# ./$TBX -c
# /opt/snap/.install4j/response.varfile
RUN echo -e "deleteSnapDir=ALL\nexecuteLauncherAction\$Boolean=false\nforcePython\$Boolean=false\nsys.adminRights\$Boolean=true\nsys.component.RSTB\$Boolean=true\nsys.component.S1TBX\$Boolean=true\nsys.component.S2TBX\$Boolean=true\nsys.component.S3TBX\$Boolean=true\nsys.component.SNAP\$Boolean=true\nsys.installationDir=/opt/snap\nsys.languageId=en\nsys.programGroupDisabled\$Boolean=false\nsys.symlinkDir=/usr/local/bin\n" >/tmp/snap.varfile

# Run the installer in unattended mode
RUN ./$TBX -q -varfile /tmp/snap.varfile

# Disable update checking
RUN echo "snap.versionCheck.interval=NEVER" >>/opt/snap/etc/snap.properties \
  && sed 's/^\(default_options.*\)"/\1 -J-Dplugin.manager.check.interval=NEVER -J-Dsnap.home=\/opt\/snap -J-Dsnap.userdir=\/config\/.snap"/' -i /opt/snap/etc/snap.conf

#RUN echo -e '#!/bin/sh\n/opt/snap/bin/snap --nosplash --nogui --modules --update-all 2>&1 | while read -r line; do\n    echo "$line"\n    [ "$line" = "updates=0" ] && sleep 2 && pkill -TERM -f "snap/jre/bin/java"\ndone\n' >/opt/snap/bin/update_plugins.sh && chmod +x /opt/snap/bin/update_plugins.sh && /opt/snap/bin/update_plugins.sh

# Fix for
# libGL error: MESA-LOADER: failed to open swrast: /usr/lib/dri/swrast_dri.so: cannot open shared object file: No such file or directory (search paths /usr/lib/x86_64-linux-gnu/dri:\$${ORIGIN}/dri:/usr/lib/dri, suffix _dri)
# https://forum.step.esa.int/t/snap9-error-libegl-warning-mesa-loader-failed-to-open-swrast/36702/5
RUN mkdir /usr/lib/dri
RUN ln -s /usr/lib/x86_64-linux-gnu/dri/swrast_dri.so /usr/lib/dri

# Copy X app start script to right location
COPY startapp.sh /startapp.sh

ENV PATH=/opt/snap/bin:$PATH

