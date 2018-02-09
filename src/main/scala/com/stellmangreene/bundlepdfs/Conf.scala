package com.stellmangreene.bundlepdfs

import com.typesafe.config.{ Config, ConfigFactory }
import scala.collection.JavaConverters._
import better.files._

class Conf(args: Seq[String]) {

  def printHelpAndExit = {
    println("""bundle-pdfs
read OCR text from TIF files and generate script to bundle into multi-page PDFs

usage: bundle-pdfs --test-first-page-scores --test-skip-page-scores config-name
specify configuration-name to use a section in application.conf
specify test options to do a dry run and test the scores or bundles

input files are generated with tessearct
% tesseract -l eng+spa --user-words id_list.txt 2468.tif scan_2468 >& log_2468.log

output is a shell script that calls tiffcp to bundle TIF files into a multi-page 
file and tiff2pdf to convert it to a PDF, using a best guess at an identifier from 
an ID list file as the filename

(c) 2018 Stellman & Greene Consulting LLC""")
    sys.exit
  }

  val strippedArgs = args.filter(!_.startsWith("--"))
  if (strippedArgs.size != 1) {
    System.err.println("ERROR: Please specify a configuration name")
    printHelpAndExit
  }

  val configArg = strippedArgs.head
  val configName = s"bundle-pdfs.${configArg}"

  val config =
    try {
      ConfigFactory.load().getConfig(configName)
    } catch {
      case t: Throwable =>
        val configList = ConfigFactory.load().entrySet().asScala.map(_.getKey)
          .filter(_.startsWith("bundle-pdfs."))
          .map(_.split("\\."))
          .filter(_.size > 2)
          .map(_.tail.head)
          .toSeq
          .sorted
          .distinct

        if (configList.isEmpty) System.err.println("WARNING: No configurations found in application.conf")
        else {
          System.err.println(s"Configuration '$configArg' was not found under 'bundle-pdfs' in application.conf\nPlease specify config on the command line. Valid configs:")
          configList.foreach(System.err.println)
        }
        printHelpAndExit
    }

  val firstPageLinesFile = config.getString("first-page-lines-file")
  val firstPageScore = config.getInt("first-page-score")
  val testFirstPageLines = args.contains("--test-first-page-scores")

  val (skipPageLinesFile, skipPageScore) =
    try {
      (Some(config.getString("skip-page-lines-file")), config.getInt("skip-page-score"))
    } catch {
      case _: Throwable => {
        System.err.println("No skip-page-lines-file found in config, not skipping any files")
        (None, Int.MaxValue)
      }
    }
  val testSkipPageLines = args.contains("--test-skip-page-scores")
  if (skipPageLinesFile.isEmpty && testSkipPageLines) {
    System.err.println("ERROR: --test-skip-page-scores argument used but no skip-page-lines-file defined in config")
    printHelpAndExit
  }
  
  val idsFile = config.getString("ids-file")

  val folders = config.getStringList("folders").iterator.asScala.toSeq
  
  val outputFolder = config.getString("output-folder").toFile

}
