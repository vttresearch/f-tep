#!/bin/sh

WORKER_DIR="/home/worker"
IN_DIR="${WORKER_DIR}/workDir/inDir"
OUT_DIR="${WORKER_DIR}/workDir/outDir"

export HOME=/config

# Workaround missing .SEN3 dir suffix
S3_PRODUCTS=""
for s3_product in $(find -L ${IN_DIR} -maxdepth 3 -path '*/S3*/xfdumanifest.xml'); do
  product_dir="$(dirname $s3_product)"
  link_dir="/tmp/$(basename $product_dir).SEN3"
  ln -s $product_dir $link_dir
  S3_PRODUCTS="${S3_PRODUCTS} $link_dir/xfdumanifest.xml"
done

INPUT_FILES="$(find -L ${IN_DIR} -maxdepth 3 -name '*MTD*.xml' -or -iname '*.tif') ${S3_PRODUCTS}"

ln -snf ${IN_DIR} /nobody/inDir
ln -snf ${OUT_DIR} /nobody/outDir

printenv
whoami

# libGL error: MESA-LOADER: failed to open swrast: /config/.snap/auxdata/gdal/gdal-3-2-1/lib/jni/libstdc++.so.6: version `GLIBCXX_3.4.26' not found (required by /lib/x86_64-linux-gnu/libLLVM-12.so.1) (search paths /usr/lib/x86_64-linux-gnu/dri:\$${ORIGIN}/dri:/usr/lib/dri, suffix _dri)
# https://forum.step.esa.int/t/snap9-error-libegl-warning-mesa-loader-failed-to-open-swrast/36702/5
# Make a dummy snap run so that /config/.snap/ folder hierarchy gets built
/opt/snap/bin/snap -v
# Remove conflicting library
rm /config/.snap/auxdata/gdal/gdal-3-2-1/lib/libstdc++.so.6
# Copy the correct one
cp /usr/lib/x86_64-linux-gnu/libstdc++.so.6.0.28 /config/.snap/auxdata/gdal/gdal-3-2-1/lib/libstdc++.so.6
# Ensure it has execution bits set
chmod +x /config/.snap/auxdata/gdal/gdal-3-2-1/lib/libstdc++.so.6

# Disable asking for updates
mkdir -p /config/.snap/system/config/Preferences/org/esa/snap/snap
echo optional.version.check.onstartup.decision=no > /config/.snap/system/config/Preferences/org/esa/snap/snap/rcp.properties

cd "${WORKER_DIR}/workDir"
/opt/snap/bin/snap --open ${INPUT_FILES}
