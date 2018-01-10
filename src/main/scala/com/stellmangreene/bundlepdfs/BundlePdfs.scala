package com.stellmangreene.bundlepdfs

import org.apache.commons.text.similarity.FuzzyScore
import java.util.Locale
import better.files._

object BundlePdfs extends App {

  val conf = new Conf(args) // Note: This line also works for "object Main extends App"

  val folders = conf.folders.map(_.toFile)

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

  val fuzzyScore = new FuzzyScore(Locale.ENGLISH)

  folders.foreach(processFolder)

  /** calculate the average for a set of scores */
  def average(scores: Seq[Integer]) =
    if (scores.size == 0) 0
    else scores.foldRight(1)((i, j) => i + j) / scores.size

  /** read a folder and  */
  def processFolder(folder: File) = {

    var currentBundle = Seq[File]()

    folder.children
      .toSeq
      .sortBy(_.name)
      .foreach(file => {
        if (file.name.endsWith(".txt")) {
          val fileContents = file.contentAsString

          if (conf.testFirstPageLines || conf.testSkipPageLines) {
            testFile(file, fileContents)

          } else {

            if (isFirstPage(file, fileContents)) {
              if (!currentBundle.isEmpty) {
                generateBundle(currentBundle)
              }

              currentBundle = Seq(file)

            } else {
              currentBundle ++= Seq(file)

            }

          }
        }
      })
  }

  /** generate a bundle */
  def generateBundle(bundle: Seq[File]) = {

    val filename = s"${findId(bundle.last)}.pdf"

    if (conf.testBundles) {
      println(bundle.size, bundle.head.pathAsString.split("/").dropRight(1).last, filename, bundle.map(_.name))

    } else {

      //TODO: Generate the bundle script

    }

  }

  var unknownIdCount = 0
  
  /**
   * find the ID in a file
   */
  def findId(file: File): String = {
    val words = file.contentAsString.split("\\s+")
    words.find(ids.isDefinedAt).getOrElse({
      unknownIdCount += 1
      s"unknown_id_$unknownIdCount"
    })
  }

  /**
   * check if a file contains the first page
   */
  def isFirstPage(file: better.files.File, fileContents: String): Boolean = {
    val firstPageScores = firstPageLines.map(fuzzyScore.fuzzyScore(fileContents, _))
    val firstPageAverage = average(firstPageScores)

    val skipPageScores = skipPageLines.map(fuzzyScore.fuzzyScore(fileContents, _))
    val skipPageAverage = average(skipPageScores)

    firstPageAverage > conf.firstPageScore
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
