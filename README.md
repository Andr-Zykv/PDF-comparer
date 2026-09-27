PDF-comparer
=======

This program for comparing PDFs runs comparisons in 4 stages:
 - Structure (skeleton of the document: amount of pages, font families, annotations)
 - Metadata (information about the creation, the creator, header info)
 - Text (visible text of two documents, page by page, at word granularity)
 - Visuals (shared pages as rendered images)

The results then are output in stdout and written in a .json file

Running the program is done via terminal:\
mvn package\
java -jar [generated jar]

There you can also find out more about the flags
