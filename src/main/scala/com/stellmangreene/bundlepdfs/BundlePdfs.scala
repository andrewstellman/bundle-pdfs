package com.stellmangreene.bundlepdfs

import org.apache.commons.text.similarity.FuzzyScore
import java.util.Locale
import better.files._
import java.util.Calendar
import java.text.SimpleDateFormat
import java.nio.file.attribute.PosixFilePermission

object BundlePdfs extends App {

  val conf = new Conf(args) // Note: This line also works for "object Main extends App"

  val folders = conf.folders.map(conf.parentFolder / _)

  val timestamp = new SimpleDateFormat("yy-MM-dd_HH-mm-ss").format(Calendar.getInstance.getTime)

  if (!conf.outputFolder.exists) {
    conf.outputFolder.createDirectory
  }

  val outputFolder = conf.outputFolder / s"${conf.configName}.$timestamp"
  outputFolder.createDirectory
  println(s"Writing scripts and logs to $outputFolder")

  val scriptFile = outputFolder / s"${conf.configName}_bundle_pdfs.sh"
  scriptFile.createFile

  Set(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_EXECUTE).foreach(scriptFile.addPermission)
  println(s"Writing script to ${scriptFile.pathAsString}")

  val csvFile = outputFolder / s"${conf.configName}_bundle_pdfs.csv"
  csvFile.createFile
  println(s"Writing CSV log to ${csvFile.pathAsString}")

  scriptFile.appendLines("#!/bin/sh")
  scriptFile.appendLines(s"# created by bundle_pdfs <https://github.com/andrewstellman/bundle-pdfs> $timestamp")
  scriptFile.appendLines("")
  scriptFile.appendLines(s"""cd "${conf.parentFolder.pathAsString}"""")
  scriptFile.appendLines("")

  csvFile.appendLines(""""pdf_filename","page_number","image_filename","id"""")

  val notFound = folders.filter(!_.exists)
  if (!notFound.isEmpty) {
    System.err.println("unable to run -- the following folders were not found:")
    notFound.foreach(folder => System.err.println(s"  ${folder.pathAsString}"))
    conf.printHelpAndExit
    sys.exit
  }

  val firstPageLines = readLinesFromFile(conf.firstPageLinesFile)
  val skipPageLines =
    if (conf.skipPageLinesFile.isDefined) readLinesFromFile(conf.skipPageLinesFile.get)
    else Seq()

  val ids: Map[String, Boolean] = readLinesFromFile(conf.idsFile).map(id => id -> true).toMap

  val correctedIds: Map[String, String] = readLinesFromFile(conf.correctedIdsFile)
    .map(line => line.split(","))
    .map(line => line.head -> line.last)
    .toMap

  val correctedStartPages: Seq[String] = readLinesFromFile(conf.correctedStartPagesFile)

  val falsePositiveStartPages: Seq[String] = readLinesFromFile(conf.falsePositiveStartPagesFile)

  val fuzzyScore = new FuzzyScore(Locale.ENGLISH)

  val files: Seq[File] = folders.map(getFilesFromFolder).flatten

  val fileRegex = "^(.*)scan_(.*)\\.txt$".r

  processFiles(files)

  scriptFile.appendLines(s"""echo "wrote log to ${conf.configName}_bundle_pdfs.log"""")

  /** calculate the average for a set of scores */
  def average(scores: Seq[Integer]) =
    if (scores.size == 0) 0
    else scores.foldRight(1)((i, j) => i + j) / scores.size

  /** extract the files from a folder */
  def getFilesFromFolder(folder: File): Seq[File] = {
    folder.children
      .toSeq
      .sortBy(_.name)
      .map(file => {
        if (file.isRegularFile && file.name.endsWith(".txt")) {
          Some(file)
        } else None
      })
      .filter(_.isDefined)
      .map(_.get)
  }

  /** read a folder and  */
  def processFiles(files: Seq[File]) = {

    var currentBundle = Seq[File]()

    var bundleLastPageFile = files.head

    files.foreach(file => {
      val fileContents = file.contentAsString

      if (conf.testFirstPageLines || conf.testSkipPageLines) {
        testFile(file, fileContents)

      } else {

        val probableFirstPage = ((currentBundle.size + 1) == conf.expectedBundleLength)

        if (isFirstPage(file, probableFirstPage, fileContents)) {
          if (!currentBundle.isEmpty) {
            generateBundle(currentBundle, bundleLastPageFile)
          }

          currentBundle = Seq(file)

        } else {
          currentBundle ++= Seq(file)

        }

        bundleLastPageFile = file
      }
    })

    // Generate the last bundle
    if (!currentBundle.isEmpty) {
      generateBundle(currentBundle, bundleLastPageFile)
    }
  }

  /** generate a bundle */
  def generateBundle(bundle: Seq[File], bundleLastPageFile: File) = {

    val filenameBase = generateFilename(bundle.last, bundleLastPageFile)
    val pdfFilename = s"${filenameBase}.pdf"
    val tifFilename = s"${filenameBase}.tif"

    def textFilename(f: File) = f.pathAsString.split("/").takeRight(2).mkString("/")
    if (bundle.size == 1) {
      csvFile.appendLines(s""""$pdfFilename",1,"${getTifFilename(bundle.head)}","${findId(bundle.head, bundleLastPageFile).getOrElse("")}"""")

    } else {
      csvFile.appendLines(s""""$pdfFilename",1,"${getTifFilename(bundle.head)}","${findId(bundle.head, bundleLastPageFile).getOrElse("")}"""")
      bundle.drop(1).dropRight(1).zipWithIndex.foreach(e => {
        val (file, index) = e
        csvFile.appendLines(s""""$pdfFilename",${index + 2},"${getTifFilename(file)}","${findId(file, bundleLastPageFile).getOrElse("")}"""")
      })
      csvFile.appendLines(s""""$pdfFilename",${bundle.length},"${getTifFilename(bundle.last)}","${findId(bundle.last, bundleLastPageFile).getOrElse("")}"""")
    }

    val filename = s"${findId(bundle.last, bundleLastPageFile)}.tif"

    scriptFile.appendLines("")

    scriptFile.appendLines(s"""echo "generating $pdfFilename from ${bundle.length} files"""")
    scriptFile.appendLines(s"""echo "generating $pdfFilename from ${bundle.length} files" >> ${conf.configName}_bundle_pdfs.log""")

    scriptFile.appendText("tiffcp ")
    scriptFile.appendText(bundle.map(getTifFilename).mkString(" "))
    scriptFile.appendLines(s""" "$outputFolder/$tifFilename"""")

    scriptFile.appendLines(s"""tiff2pdf "$outputFolder/$tifFilename" > "$outputFolder/$pdfFilename"""")

    scriptFile.appendLines("")
  }

  /** extract a tif filename to include in a command line from a File */
  def getTifFilename(f: File) = {
    val m = fileRegex.findFirstMatchIn(f.pathAsString)
    if (m.isEmpty)
      throw new IllegalStateException(s"Invalid filename: ${f.pathAsString}")
    //s"'${m.get.group(1).replaceAll("'", "\\'")}${m.get.group(2)}.tif'"
    s"${m.get.group(1).split("/").last}/${m.get.group(2)}.tif"
  }

  var unknownIdCount = 0

  /**
   * finds an ID in a file if it exists
   */
  def findId(file: File, bundleLastPageFile: File): Option[String] = {
    val probableLastPage = (file == bundleLastPageFile)
    val correctedId = correctedIds.find(_._1 == getTifFilename(file)).map(_._2)
    if (correctedId.isDefined) correctedId
    else {
      val words = file.contentAsString.split(conf.idsSplitRegex)
      val containsId = words.find(ids.isDefinedAt)
      if (containsId.isDefined || !probableLastPage) containsId
      else {
        // for performance reasons, only do a full-text search of the page contents for probable last pages that didn't find an ID using the regex split
        ids.find(e => file.contentAsString.contains(e._1)).map(_._1)
      }
    }
  }

  /**
   * returns a filename based on an ID in a file, or unknown_id_* if no ID found
   */
  def generateFilename(file: File, bundleLastPageFile: File): String = {
    findId(file, bundleLastPageFile).getOrElse({
      unknownIdCount += 1
      f"unknown_id_$unknownIdCount%04d"
    })
  }

  /*
   * keep track of the previous file's hasCorrectedId -- if it did have a corrected ID,
   * then the next file is a start page
   */
  var hasCorrectedId: Boolean = false

  /**
   * check if a file contains the first page
   */
  def isFirstPage(file: better.files.File, probableFirstPage: Boolean, fileContents: String): Boolean = {
    val isFalsePositiveStartPage = falsePositiveStartPages.contains(getTifFilename(file))
    if (isFalsePositiveStartPage) false
    else {
      val isCorrectedStartPage = correctedStartPages.contains(getTifFilename(file))
      val previousPageHadCorrectedId = hasCorrectedId
      hasCorrectedId = correctedIds.isDefinedAt(getTifFilename(file))

      val multiplier =
        if (probableFirstPage) conf.expectedLengthFirstPageMultiplier
        else 1
      val firstPageScores = firstPageLines.map(fuzzyScore.fuzzyScore(fileContents, _))
      val firstPageAverage = average(firstPageScores) * multiplier

      val skipPageScores = skipPageLines.map(fuzzyScore.fuzzyScore(fileContents, _))
      val skipPageAverage = average(skipPageScores)

      isCorrectedStartPage || (firstPageAverage > conf.firstPageScore)
    }
  }

  /** Test a file and print its scores */
  def testFile(file: File, fileContents: String) = {
    if (conf.testFirstPageLines) {
      testLines(firstPageLines, conf.firstPageScore, file, fileContents, "first page")
    }

    if (conf.testSkipPageLines) {
      testLines(skipPageLines, conf.skipPageScore, file, fileContents, "skip page")
    }
  }

  /** Test a set of lines by printing the fuzzy score for each line */
  def testLines(lines: Seq[String], score: Int, file: File, fileContents: String, name: String) = {
    val fileContents = file.contentAsString
    println(s"Testing $name: ${file.pathAsString}")
    val averageScore = average(lines.map(fuzzyScore.fuzzyScore(fileContents, _)))
    val marker = if (averageScore > score) s"${name.substring(0, 1)} " else ""
    lines.foreach(line => {
      val score = fuzzyScore.fuzzyScore(fileContents, line)
      println(s"$marker$score\t$line")
    })
    println(s"${marker}Average $name score: ${averageScore}")
    println("---------------------------")
    println()
  }

  /** read the lines from a file */
  def readLinesFromFile(filename: String): Seq[String] = {
    if (!filename.toFile.exists) {
      System.err.println(s"unable to run -- unable to find file $filename")
      conf.printHelpAndExit
      sys.exit
    }
    val file = filename.toFile
    if (file.isEmpty) Seq()
    else {
      file.contentAsString.replaceAll("\\r\\n", "\n").split("\n")
    }
  }

}
