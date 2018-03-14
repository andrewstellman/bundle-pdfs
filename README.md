# bundle-pdfs
**Use tesseract OCR to bundle TIFF files into multi-page PDFs**

This is an unpolished program that generates a Bash script to bundle a set of image files into PDFs. It uses fuzzy text matching to identify the first page of each PDF, and lets you specify a set of configuration files to deal with exceptions.

## Setup

Install Tesseract OCR: https://github.com/tesseract-ocr (install language packs if necessary)
Install LibTiff: http://libtiff.org/ - http://www.simplesystems.org/libtiff/

Make sure the following commands are in your path:
* tesseract
* tiffcp
* tiff2pdf

## Prepare your folder

The scripts/ folder in this repository contains a script called make-process.sh. Run it on the folders that you want to bundle PDFs. The filenames in the folders must be sequential.

## Create the conf file

Add section to your application.conf with paths to the folders and exception files:

```
    survey-tiffs-women {
       // folder that contains the TIF folders
       parent-folder = "/Users/andrewstellman/Documents/Agent Orange Surveys/survey tiffs - women"
    
       // list of folders (in order) that contain the TIFs and their OCR'd files (scan_FILENAME.txt goes with FILENAME.tif)
       folders = [
          "001",
          "002",
          "003",
          "004",
          "005",
          "006",
          "007",
          "008",
          "009",
          "010",
          "011",
          "012",
          "013"
       ]
       
       // path of a file with lines to match on the first page of each PDF
       first-page-lines-file = "/Users/andrewstellman/Documents/Agent Orange Surveys/survey_tiffs_women_data/survey_tiffs_women_firstlines.txt"
       
       // path of a file that contains corrected ids for TIFF files
       corrected-ids-file = "/Users/andrewstellman/Documents/Agent Orange Surveys/survey_tiffs_women_data/survey_tiffs_women_corrected_ids.txt"
       
       // path of a file that contains corrected start pages for TIFF files
       corrected-start-pages-file = "/Users/andrewstellman/Documents/Agent Orange Surveys/survey_tiffs_women_data/survey_tiffs_women_corrected_start_pages.txt"
       
       // path of a file that contains false positive start pages for TIFF files
       false-positive-start-pages-file = "/Users/andrewstellman/Documents/Agent Orange Surveys/survey_tiffs_women_data/survey_tiffs_women_false_positive_start_pages.txt"

       // minimum average fuzzy match score to match on the first page of each PDF
       first-page-score = 70
       
       // expected bundle length (eg. # of TIFF files)
       expected-bundle-length = 11
       
       // score multiplier for TIFF files that match expected-survey-length + 1
       expected-length-first-page-multiplier = 1
       
       // path of a file with lines to match a page to skip
       // skip-page-lines-file = 
       
       // minimum average fuzzy match score to match a page to skip
       // skip-page-score = 
       
       // path of a file with IDs
       ids-file = "/Users/andrewstellman/Documents/Agent Orange Surveys/survey_tiffs_women_data/survey_tiffs_women_ids.txt"
       
       // regex to split the file to match IDs
       ids-split-regex = "\\s+"
    
       // path to create folder with output files
       output-folder = "/Users/andrewstellman/Documents/Agent Orange Surveys/output"
    }
```

Examples of the data files are in zip files in the "example-data-files" folder in this repository.

## Run the program

Execute the program, passing it the configuration section as the command-line argument:

`sbt "run survey-tiffs-women"`

This will generate two files, a shell script that will bundle the PDFS and a CSV file that shows what will be generated when it runs. Make sure the CSV file looks right, then run the shell script.

## Remove unnecessary files

Tesseract will generate .txt files and the shell script will generate .tif files in addition to bundled PDF files. Remove them.
