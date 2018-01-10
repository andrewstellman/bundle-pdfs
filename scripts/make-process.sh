#!/bin/sh

if [ $# -eq 0 ]; then
    echo "usage: make-process.sh folder1 folder2 folder3 ... folderN"
    exit 1
fi

echo "#!/bin/sh" > process-all.sh

for folder in "$@"
do
   echo Making $folder/process.sh
   ls $folder/*.tif | cut -f1 -d\. | perl -ne 'chomp; s/^.*?\/(.*)/$1/; print "echo processing $_.tif in `pwd` ; tesseract --user-words ../women_ids.txt $_.tif scan_$_ >& log_$_.log\n";' > $folder/process.sh
   chmod 775 $folder/process.sh
   echo "pushd . ; cd $folder ; ./process.sh ; popd" >> process-all.sh
done

chmod 755 process-all.sh

