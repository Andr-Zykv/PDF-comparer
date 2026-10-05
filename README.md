PDF-comparer
=======

This is a program for comparing PDFs. It runs comparisons in 4 stages:
 - Structure (skeleton of the document: number of pages, font families, annotations)
 - Metadata (information about the creation, the creator, header info)
 - Text (visible text of two documents, page by page, at word granularity)
 - Visuals (shared pages as rendered images)

The results then are output in stdout and written in a .json file

Running the program is done via terminal:
```shell
mvn package 
java -jar [generated jar] <expected.pdf> <actual.pdf> [options]
```

### options:
    --json <path>     write the JSON report here (default: <actual>.diff.json)
    --no-json         skip writing a JSON report
    --fail-at LEVEL   exit 1 if any difference reaches this severity (default: ERROR) 
                      LEVEL is one of INFO, WARNING, ERROR
    --fail-fast       stop after the first stage that reaches --fail-at; skips later, 
                      more expensive stages once the documents are already known to 
                      differ enough to fail