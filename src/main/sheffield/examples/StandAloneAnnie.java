/*
* StandAloneAnnie.java
*
*
* Copyright(c)2000-2001,The University of Sheffield.
*
* This file is part of GATE(see http://gate.ac.uk/), and is free
* software,licenced under the GNU Library General Public License,
* Version 2,June1991.
*
* A copy of this licence is included in the distribution in the file
* licence.html,and is also available at http://gate.ac.uk/gate/licence.html.
*
* hamish,29/1/2002
*
* $Id:StandAloneAnnie.java,v1.6 2006/01/0916:43:22ian Exp $
*/

package sheffield.examples;

import gate.*;
import gate.corpora.RepositioningInfo;
import gate.creole.ANNIEConstants;
import gate.creole.SerialAnalyserController;
import gate.util.GateException;
import gate.util.Out;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

/**
 * This class illustrates how to use ANNIE as a sausage machine
 * in another application - put ingredients in one end (URLs pointing
 * to documents) and get sausages (e.g. Named Entities) out the
 * other end.
 * <P><B>NOTE:</B><BR>
 * For simplicity's sake, we don't do any exception handling.
 */
public class StandAloneAnnie {

   /**
    * The Corpus Pipeline application to contain ANNIE
    */
   private SerialAnalyserController annieController;

   /**
    * Initialise the ANNIE system. This creates a "corpus pipeline"
    * application that can be used to run sets of documents through
    * the extraction system.
    */
   public void initAnnie() throws GateException {
      Out.prln("Initialising ANNIE...");

      // create a serial analyser controller to run ANNIE with
      annieController =
            (SerialAnalyserController) Factory.createResource(
                  "gate.creole.SerialAnalyserController", Factory.newFeatureMap(),
                  Factory.newFeatureMap(), "ANNIE_"+ Gate.genSym()
            );

      // load each PR as defined in ANNIEConstants
      for (int i = 0; i < ANNIEConstants.PR_NAMES.length; i++) {
         FeatureMap params = Factory.newFeatureMap(); // use default parameters
         ProcessingResource pr = (ProcessingResource)
               Factory.createResource(ANNIEConstants.PR_NAMES[i], params);

         // add the PR to the pipeline controller
         annieController.add(pr);
      } // for each ANNIE PR

      Out.prln("...ANNIE loaded");
   } // initAnnie()

   /**
    * Tell ANNIE's controller about the corpus you want to run on
    */
   public void setCorpus(Corpus corpus) {
      annieController.setCorpus(corpus);
   } // setCorpus

   /**
    * Run ANNIE
    */
   public void execute() throws GateException {
      Out.prln("Running ANNIE...");
      annieController.execute();
      Out.prln("...ANNIE complete");
   } // execute()

   /**
    * Run from the command-line, with a list of URLs as argument.
    * <P><B>NOTE:</B><BR>
    * This code will run with all the documents in memory - if you
    * want to unload each from memory after use, add code to store
    * the corpus in a DataStore.
    */
   public static void main(String args[])
         throws GateException, IOException {
      // initialise the GATE library
      Out.prln("Initialising GATE...");
      Gate.init();

      // Load ANNIE plugin
      File gateHome = Gate.getGateHome();
      File pluginsHome = new File(gateHome, "plugins");
      Gate.getCreoleRegister().registerDirectories(new File(pluginsHome, "ANNIE").toURL());
      Out.prln("...GATE initialised");

      // initialise ANNIE (this may take several minutes)
      StandAloneAnnie annie = new StandAloneAnnie();
      annie.initAnnie();

      // create a GATE corpus and add a document for each command-line
      // argument
      Corpus corpus = (Corpus) Factory.createResource("gate.corpora.CorpusImpl");
      for (int i = 0; i < args.length; i++) {
         URL u = new URL(args[i]);
         FeatureMap params = Factory.newFeatureMap();
         params.put("sourceUrl", u);
         params.put("preserveOriginalContent", true);
         params.put("collectRepositioningInfo", true);
         Out.prln("Creating doc for " + u);
         Document doc = (Document)
               Factory.createResource("gate.corpora.DocumentImpl", params);
         corpus.add(doc);
      } // for each of args

      // tell the pipeline about the corpus and run it
      annie.setCorpus(corpus);
      annie.execute();

      // for each document, get an XML document with the
      // person and location names added
      Iterator iter = corpus.iterator();
      int count = 0;
      String startTagPart_1 = "<span GateID=\"";
      String startTagPart_2 = "\" title=\"";
      String startTagPart_3 = "\" style=\"background:Red;\">";
      String endTag = "</span>";

      while (iter.hasNext()) {
         Document doc = (Document) iter.next();
         AnnotationSet defaultAnnotSet = doc.getAnnotations();
         Set annotTypesRequired = new HashSet();
         annotTypesRequired.add("Person");
         annotTypesRequired.add("Location");
         Set<Annotation> peopleAndPlaces = new HashSet<Annotation>(defaultAnnotSet.get(annotTypesRequired));

         FeatureMap features = doc.getFeatures();
         String originalContent = (String)
               features.get(GateConstants.ORIGINAL_DOCUMENT_CONTENT_FEATURE_NAME);
         RepositioningInfo info = (RepositioningInfo)
               features.get(GateConstants.DOCUMENT_REPOSITIONING_INFO_FEATURE_NAME);

         ++count;
         File file = new File("StANNIE_" + count + ".HTML");
         Out.prln("File name: '" + file.getAbsolutePath() + "'");
         if (originalContent != null && info != null) {
            Out.prln("OrigContent and reposInfo existing. Generate file...");

            Iterator it = peopleAndPlaces.iterator();
            Annotation currAnnot;
            SortedAnnotationList sortedAnnotations = new SortedAnnotationList();

            while (it.hasNext()) {
               currAnnot = (Annotation) it.next();
               sortedAnnotations.addSortedExclusive(currAnnot);
            } // while

            StringBuffer editableContent = new StringBuffer(originalContent);
            long insertPositionEnd;
            long insertPositionStart;
            // insert annotation tags backward
            Out.prln("Unsorted annotations count:" + peopleAndPlaces.size());
            Out.prln("Sorted annotations count:" + sortedAnnotations.size());
            for (int i = sortedAnnotations.size() - 1; i >= 0; --i) {
               currAnnot = (Annotation) sortedAnnotations.get(i);
               insertPositionStart = currAnnot.getStartNode().getOffset();
               insertPositionStart = info.getOriginalPos(insertPositionStart);
               insertPositionEnd = currAnnot.getEndNode().getOffset();
               insertPositionEnd = info.getOriginalPos(insertPositionEnd, true);
               if (insertPositionEnd != -1 && insertPositionStart != -1) {
                  editableContent.insert((int) insertPositionEnd, endTag);
                  editableContent.insert((int) insertPositionStart, startTagPart_3);
                  editableContent.insert((int) insertPositionStart,
                        currAnnot.getType());
                  editableContent.insert((int) insertPositionStart, startTagPart_2);
                  editableContent.insert((int) insertPositionStart,
                        currAnnot.getId().toString());
                  editableContent.insert((int) insertPositionStart, startTagPart_1);
               } // if
            } // for

            FileWriter writer = new FileWriter(file);
            writer.write(editableContent.toString());
            writer.close();
         } // if - should generate
         else if (originalContent != null) {
            Out.prln("OrigContent existing.Generate file...");

            Iterator it = peopleAndPlaces.iterator();
            Annotation currAnnot;
            SortedAnnotationList sortedAnnotations = new SortedAnnotationList();

            while (it.hasNext()) {
               currAnnot = (Annotation) it.next();
               sortedAnnotations.addSortedExclusive(currAnnot);
            } // while

            StringBuffer editableContent = new StringBuffer(originalContent);
            long insertPositionEnd;
            long insertPositionStart;
            // insert anotation tags backward
            Out.prln("Unsorted annotations count:" + peopleAndPlaces.size());
            Out.prln("Sorted annotations count:" + sortedAnnotations.size());
            for (int i = sortedAnnotations.size() - 1; i >= 0; --i) {
               currAnnot = (Annotation) sortedAnnotations.get(i);
               insertPositionStart = currAnnot.getStartNode().getOffset();
               insertPositionEnd = currAnnot.getEndNode().getOffset();
               if (insertPositionEnd != -1 && insertPositionStart != -1) {
                  editableContent.insert((int) insertPositionEnd, endTag);
                  editableContent.insert((int) insertPositionStart, startTagPart_3);
                  editableContent.insert((int) insertPositionStart,
                        currAnnot.getType());
                  editableContent.insert((int) insertPositionStart, startTagPart_2);
                  editableContent.insert((int) insertPositionStart,
                        currAnnot.getId().toString());
                  editableContent.insert((int) insertPositionStart, startTagPart_1);
               } // if
            } // for

            FileWriter writer = new FileWriter(file);
            writer.write(editableContent.toString());
            writer.close();
         } else {
            Out.prln("Content:" + originalContent);
            Out.prln("Repositioning:" + info);
         }

         String xmlDocument = doc.toXml(peopleAndPlaces, false);
         String fileName = "StANNIE_toXML_" + count + ".HTML";
         FileWriter writer = new FileWriter(fileName);
         writer.write(xmlDocument);
         writer.close();
      } // for each doc
   } // main

   /**
    *
    */
   public static class SortedAnnotationList extends Vector {
      public SortedAnnotationList() {
         super();
      } // SortedAnnotationList

      public boolean addSortedExclusive(Annotation annot) {
         Annotation currAnot;

         // overlapping check
         for (int i = 0; i < size(); ++i) {
            currAnot = (Annotation) get(i);
            if (annot.overlaps(currAnot)) {
               return false;
            } // if
         } // for

         long annotStart = annot.getStartNode().getOffset();
         long currStart;
         // insert
         for (int i = 0; i < size(); ++i) {
            currAnot = (Annotation) get(i);
            currStart = currAnot.getStartNode().getOffset();
            if (annotStart < currStart) {
               insertElementAt(annot, i);
               return true;
            } // if
         } // for

         int size = size();
         insertElementAt(annot, size);
         return true;
      } // addSorted
   } // SortedAnnotationList
} // class StandAloneAnnie
