/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package opennlp.tools.parser.chunking;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import opennlp.model.AbstractModel;
import opennlp.model.MaxentModel;
import opennlp.model.TwoPassDataIndexer;
import opennlp.tools.chunker.ChunkSample;
import opennlp.tools.chunker.Chunker;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.parser.AbstractBottomUpParser;
import opennlp.tools.parser.ChunkContextGenerator;
import opennlp.tools.parser.ChunkSampleStream;
import opennlp.tools.parser.HeadRules;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.ParseSampleStream;
import opennlp.tools.parser.ParserChunkerSequenceValidator;
import opennlp.tools.parser.ParserEventTypeEnum;
import opennlp.tools.parser.ParserModel;
import opennlp.tools.parser.ParserType;
import opennlp.tools.parser.PosSampleStream;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSSample;
import opennlp.tools.postag.POSTagger;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.util.HashSumEventStream;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.model.ModelType;
import opennlp.tools.util.model.ModelUtil;

/**
 * Class for a shift reduce style parser based on Adwait Ratnaparkhi's 1998 thesis.
 */
public class Parser extends AbstractBottomUpParser {

  private MaxentModel buildModel;
  private MaxentModel checkModel;

  private BuildContextGenerator buildContextGenerator;
  private CheckContextGenerator checkContextGenerator;

  private double[] bprobs;
  private double[] cprobs;

  private static final String TOP_START = START + TOP_NODE;
  private int topStartIndex;
  private Map<String, String> startTypeMap;
  private Map<String, String> contTypeMap;

  private int completeIndex;
  private int incompleteIndex;

  public Parser(ParserModel model, int beamSize, double advancePercentage) {
    this(model.getBuildModel(), model.getCheckModel(),
        new POSTaggerME(model.getParserTaggerModel(), 10, 0),
        new ChunkerME(model.getParserChunkerModel(),
            ChunkerME.DEFAULT_BEAM_SIZE,
            new ParserChunkerSequenceValidator(model.getParserChunkerModel()),
            new ChunkContextGenerator(ChunkerME.DEFAULT_BEAM_SIZE)),
            model.getHeadRules(), beamSize, advancePercentage);
  }
  
  public Parser(ParserModel model) {
    this(model, defaultBeamSize, defaultAdvancePercentage);
  }

  /**
   * Creates a new parser using the specified models and head rules.
   * @param buildModel The model to assign constituent labels.
   * @param checkModel The model to determine a constituent is complete.
   * @param tagger The model to assign pos-tags.
   * @param chunker The model to assign flat constituent labels.
   * @param headRules The head rules for head word perculation.
   */
  @Deprecated
  public Parser(MaxentModel buildModel, MaxentModel checkModel, POSTagger tagger, Chunker chunker, HeadRules headRules) {
  	this(buildModel,checkModel,tagger,chunker,headRules,defaultBeamSize,defaultAdvancePercentage);
  }

  /**
   * Creates a new parser using the specified models and head rules using the specified beam size and advance percentage.
   * @param buildModel The model to assign constituent labels.
   * @param checkModel The model to determine a constituent is complete.
   * @param tagger The model to assign pos-tags.
   * @param chunker The model to assign flat constituent labels.
   * @param headRules The head rules for head word perculation.
   * @param beamSize The number of different parses kept during parsing.
   * @param advancePercentage The minimal amount of probability mass which advanced outcomes must represent.
   * Only outcomes which contribute to the top "advancePercentage" will be explored.
   */
  @Deprecated
  public Parser(MaxentModel buildModel, MaxentModel checkModel, POSTagger tagger, Chunker chunker, HeadRules headRules, int beamSize, double advancePercentage) {
    super(tagger, chunker, headRules, beamSize, advancePercentage);
    this.buildModel = buildModel;
    this.checkModel = checkModel;
    bprobs = new double[buildModel.getNumOutcomes()];
    cprobs = new double[checkModel.getNumOutcomes()];
    this.buildContextGenerator = new BuildContextGenerator();
    this.checkContextGenerator = new CheckContextGenerator();
    startTypeMap = new HashMap<String, String>();
    contTypeMap = new HashMap<String, String>();
    for (int boi = 0, bon = buildModel.getNumOutcomes(); boi < bon; boi++) {
      String outcome = buildModel.getOutcome(boi);
      if (outcome.startsWith(START)) {
        //System.err.println("startMap "+outcome+"->"+outcome.substring(START.length()));
        startTypeMap.put(outcome, outcome.substring(START.length()));
      }
      else if (outcome.startsWith(CONT)) {
        //System.err.println("contMap "+outcome+"->"+outcome.substring(CONT.length()));
        contTypeMap.put(outcome, outcome.substring(CONT.length()));
      }
    }
    topStartIndex = buildModel.getIndex(TOP_START);
    completeIndex = checkModel.getIndex(COMPLETE);
    incompleteIndex = checkModel.getIndex(INCOMPLETE);
  }

  protected void advanceTop(Parse p) {
    buildModel.eval(buildContextGenerator.getContext(p.getChildren(), 0), bprobs);
    p.addProb(Math.log(bprobs[topStartIndex]));
    checkModel.eval(checkContextGenerator.getContext(p.getChildren(), TOP_NODE, 0, 0), cprobs);
    p.addProb(Math.log(cprobs[completeIndex]));
    p.setType(TOP_NODE);
  }

  protected Parse[] advanceParses(final Parse p, double probMass) {
    double q = 1 - probMass;
    /** The closest previous node which has been labeled as a start node. */
    Parse lastStartNode = null;
    /** The index of the closest previous node which has been labeled as a start node. */
    int lastStartIndex = -1;
    /** The type of the closest previous node which has been labeled as a start node. */
    String lastStartType = null;
    /** The index of the node which will be labeled in this iteration of advancing the parse. */
    int advanceNodeIndex;
    /** The node which will be labeled in this iteration of advancing the parse. */
    Parse advanceNode=null;
    Parse[] originalChildren = p.getChildren();
    Parse[] children = collapsePunctuation(originalChildren,punctSet);
    int numNodes = children.length;
    if (numNodes == 0) {
      return null;
    }
    //determines which node needs to be labeled and prior labels.
    for (advanceNodeIndex = 0; advanceNodeIndex < numNodes; advanceNodeIndex++) {
      advanceNode = children[advanceNodeIndex];
      if (advanceNode.getLabel() == null) {
        break;
      }
      else if (startTypeMap.containsKey(advanceNode.getLabel())) {
        lastStartType = startTypeMap.get(advanceNode.getLabel());
        lastStartNode = advanceNode;
        lastStartIndex = advanceNodeIndex;
        //System.err.println("lastStart "+i+" "+lastStart.label+" "+lastStart.prob);
      }
    }
    int originalAdvanceIndex = mapParseIndex(advanceNodeIndex,children,originalChildren);
    List<Parse> newParsesList = new ArrayList<Parse>(buildModel.getNumOutcomes());
    //call build
    buildModel.eval(buildContextGenerator.getContext(children, advanceNodeIndex), bprobs);
    double bprobSum = 0;
    while (bprobSum < probMass) {
      // The largest unadvanced labeling.
      int max = 0;
      for (int pi = 1; pi < bprobs.length; pi++) { //for each build outcome
        if (bprobs[pi] > bprobs[max]) {
          max = pi;
        }
      }
      if (bprobs[max] == 0) {
        break;
      }
      double bprob = bprobs[max];
      bprobs[max] = 0; //zero out so new max can be found
      bprobSum += bprob;
      String tag = buildModel.getOutcome(max);
      //System.out.println("trying "+tag+" "+bprobSum+" lst="+lst);
      if (max == topStartIndex) { // can't have top until complete
        continue;
      }
      //System.err.println(i+" "+tag+" "+bprob);
      if (startTypeMap.containsKey(tag)) { //update last start
        lastStartIndex = advanceNodeIndex;
        lastStartNode = advanceNode;
        lastStartType = startTypeMap.get(tag);
      }
      else if (contTypeMap.containsKey(tag)) {
        if (lastStartNode == null || !lastStartType.equals(contTypeMap.get(tag))) {
          continue; //Cont must match previous start or continue
        }
      }
      Parse newParse1 = (Parse) p.clone(); //clone parse
      if (createDerivationString) newParse1.getDerivation().append(max).append("-");
      newParse1.setChild(originalAdvanceIndex,tag); //replace constituent being labeled to create new derivation
      newParse1.addProb(Math.log(bprob));
      //check
      //String[] context = checkContextGenerator.getContext(newParse1.getChildren(), lastStartType, lastStartIndex, advanceNodeIndex);
      checkModel.eval(checkContextGenerator.getContext(collapsePunctuation(newParse1.getChildren(),punctSet), lastStartType, lastStartIndex, advanceNodeIndex), cprobs);
      //System.out.println("check "+lastStartType+" "+cprobs[completeIndex]+" "+cprobs[incompleteIndex]+" "+tag+" "+java.util.Arrays.asList(context));
      Parse newParse2 = newParse1;
      if (cprobs[completeIndex] > q) { //make sure a reduce is likely
        newParse2 = (Parse) newParse1.clone();
        if (createDerivationString) newParse2.getDerivation().append(1).append(".");
        newParse2.addProb(Math.log(cprobs[completeIndex]));
        Parse[] cons = new Parse[advanceNodeIndex - lastStartIndex + 1];
        boolean flat = true;
        //first
        cons[0] = lastStartNode;
        flat &= cons[0].isPosTag();
        //last
        cons[advanceNodeIndex - lastStartIndex] = advanceNode;
        flat &= cons[advanceNodeIndex - lastStartIndex].isPosTag();
        //middle
        for (int ci = 1; ci < advanceNodeIndex - lastStartIndex; ci++) {
          cons[ci] = children[ci + lastStartIndex];
          flat &= cons[ci].isPosTag();
        }
        if (!flat) { //flat chunks are done by chunker
          if (lastStartIndex == 0 && advanceNodeIndex == numNodes-1) { //check for top node to include end and begining punctuation
            //System.err.println("ParserME.advanceParses: reducing entire span: "+new Span(lastStartNode.getSpan().getStart(), advanceNode.getSpan().getEnd())+" "+lastStartType+" "+java.util.Arrays.asList(children));
            newParse2.insert(new Parse(p.getText(), p.getSpan(), lastStartType, cprobs[1], headRules.getHead(cons, lastStartType)));
          }
          else {
            newParse2.insert(new Parse(p.getText(), new Span(lastStartNode.getSpan().getStart(), advanceNode.getSpan().getEnd()), lastStartType, cprobs[1], headRules.getHead(cons, lastStartType)));
          }
          newParsesList.add(newParse2);
        }
      }
      if (cprobs[incompleteIndex] > q) { //make sure a shift is likely
        if (createDerivationString) newParse1.getDerivation().append(0).append(".");
        if (advanceNodeIndex != numNodes - 1) { //can't shift last element
          newParse1.addProb(Math.log(cprobs[incompleteIndex]));
          newParsesList.add(newParse1);
        }
      }
    }
    Parse[] newParses = new Parse[newParsesList.size()];
    newParsesList.toArray(newParses);
    return newParses;
  }

  @Deprecated
  public static AbstractModel train(opennlp.model.EventStream es, int iterations, int cut) throws java.io.IOException {
    return opennlp.maxent.GIS.trainModel(iterations, new TwoPassDataIndexer(es, cut));
  }

  public static ParserModel train(String languageCode, ObjectStream<Parse> parseSamples, HeadRules rules, int iterations, int cut)
      throws IOException {
    
    System.err.println("Building dictionary");
    Dictionary mdict = buildDictionary(parseSamples, rules, cut);
    
    parseSamples.reset();
    
    Map<String, String> manifestInfoEntries = new HashMap<String, String>();
    ModelUtil.addCutoffAndIterations(manifestInfoEntries, cut, iterations);
    
    // build
    System.err.println("Training builder");
    opennlp.model.EventStream bes = new ParserEventStream(parseSamples, rules, ParserEventTypeEnum.BUILD, mdict);
    HashSumEventStream hsbes = new HashSumEventStream(bes);
    AbstractModel buildModel = train(hsbes, iterations, cut);
    manifestInfoEntries.put("Training-Builder-Eventhash", 
        hsbes.calculateHashSum().toString(16));
    
    parseSamples.reset();
    
    // tag
    POSModel posModel = POSTaggerME.train(languageCode, new PosSampleStream(parseSamples), 
        ModelType.MAXENT, null, null, cut, iterations);
    
    parseSamples.reset();
    
    // chunk
    ChunkerModel chunkModel = ChunkerME.train(languageCode, 
        new ChunkSampleStream(parseSamples), cut, iterations,
        new ChunkContextGenerator());
    
    parseSamples.reset();
    
    // check
    System.err.println("Training checker");
    opennlp.model.EventStream kes = new ParserEventStream(parseSamples, rules, ParserEventTypeEnum.CHECK);
    HashSumEventStream hskes = new HashSumEventStream(kes);
    AbstractModel checkModel = train(hskes, iterations, cut);
    manifestInfoEntries.put("Training-Checker-Eventhash", 
        hskes.calculateHashSum().toString(16));
    
    // TODO: Remove cast for HeadRules
    return new ParserModel(languageCode, buildModel, checkModel,
        posModel, chunkModel, (opennlp.tools.parser.lang.en.HeadRules) rules,
        ParserType.CHUNKING, manifestInfoEntries);
  }

  @Deprecated
  private static void usage() {
    System.err.println("Usage: Parser -[dict|tag|chunk|build|check|fun] trainingFile parserModelDirectory [iterations cutoff]");
    System.err.println();
    System.err.println("Training file should be one sentence per line where each line consists of a Penn Treebank Style parse");
    System.err.println("-dict Just build the dictionaries.");
    System.err.println("-tag Just build the tagging model.");
    System.err.println("-chunk Just build the chunking model.");
    System.err.println("-build Just build the build model");
    System.err.println("-check Just build the check model");
    System.err.println("-fun Predict function tags");
  }



  @Deprecated
  public static void main(String[] args) throws IOException, InvalidFormatException {
    if (args.length < 2) {
      usage();
      System.exit(1);
    }
    boolean dict = false;
    boolean tag = false;
    boolean chunk = false;
    boolean build = false;
    boolean check = false;
    boolean fun = false;
    boolean all = true;
    int argIndex = 0;
    while (args[argIndex].startsWith("-")) {
      all = false;
      if (args[argIndex].equals("-dict")) {
        dict = true;
      }
      else if (args[argIndex].equals("-tag")) {
        tag = true;
      }
      else if (args[argIndex].equals("-chunk")) {
        chunk = true;
      }
      else if (args[argIndex].equals("-build")) {
        build = true;
      }
      else if (args[argIndex].equals("-check")) {
        check = true;
      }
      else if (args[argIndex].equals("-fun")) {
        fun = true;
      }
      else if (args[argIndex].equals("--")) {
        argIndex++;
        break;
      }
      else {
        System.err.println("Invalid option " + args[argIndex]);
        usage();
        System.exit(1);
      }
      argIndex++;
    }
    java.io.File inFile = new java.io.File(args[argIndex++]);
    String modelDirectory = args[argIndex++];
    HeadRules rules = new opennlp.tools.parser.lang.en.HeadRules(modelDirectory+"/head_rules");
    java.io.File dictFile = new java.io.File(modelDirectory+"/dict.bin.gz");
    java.io.File tagFile = new java.io.File(modelDirectory+"/tag.bin.gz");
    java.io.File chunkFile = new java.io.File(modelDirectory+"/chunk.bin.gz");
    java.io.File buildFile = new java.io.File(modelDirectory+"/build.bin.gz");
    java.io.File checkFile = new java.io.File(modelDirectory+"/check.bin.gz");
    int iterations = 100;
    int cutoff = 5;
    if (args.length > argIndex) {
      iterations = Integer.parseInt(args[argIndex++]);
      cutoff = Integer.parseInt(args[argIndex++]);
    }
    // TODO: This option is missing in the current CLI tools,
    // and it is not thread safe ...
    if (fun) {
      Parse.useFunctionTags(true);
    }
    
    if (dict || all) {
      System.err.println("Building dictionary");
      ObjectStream<Parse> data = new ParseSampleStream(new PlainTextByLineStream(new FileReader(inFile)));
      Dictionary mdict = buildDictionary(data, rules, cutoff);
      System.out.println("Saving the dictionary");
      mdict.serialize(new FileOutputStream(dictFile));
    }
    
    if (tag || all) {
      System.err.println("Training tagger");
      ObjectStream<POSSample> tes = new PosSampleStream(new ParseSampleStream(new PlainTextByLineStream(new java.io.FileReader(inFile))));
      POSModel posModel = POSTaggerME.train("en", tes, ModelType.MAXENT, null, null, cutoff, 100);
      System.out.println("Saving the tagger model as: " + tagFile);
      OutputStream posOutputStream = new FileOutputStream(tagFile);
      posModel.serialize(posOutputStream);
      posOutputStream.close();
    }

    if (chunk || all) {
      System.err.println("Training chunker");
      ObjectStream<ChunkSample> ces = new ChunkSampleStream(new ParseSampleStream(new PlainTextByLineStream(new java.io.FileReader(inFile))));
      ChunkerModel chunkModel = ChunkerME.train("en", ces, cutoff, iterations, 
          new ChunkContextGenerator());
      System.out.println("Saving the chunker model as: " + chunkFile);
      OutputStream chunkOutputStream = new FileOutputStream(chunkFile);
      chunkModel.serialize(chunkOutputStream);
      chunkOutputStream.close();
    }

    if (build || all) {
      System.err.println("Loading Dictionary");
      Dictionary tridict = new Dictionary(new FileInputStream(dictFile.toString()),true);
      System.err.println("Training builder");
      opennlp.model.EventStream bes = new ParserEventStream(new ParseSampleStream(new PlainTextByLineStream(new java.io.FileReader(inFile))), rules, ParserEventTypeEnum.BUILD,tridict);
      AbstractModel buildModel = train(bes, iterations, cutoff);
      System.out.println("Saving the build model as: " + buildFile);
      new opennlp.maxent.io.SuffixSensitiveGISModelWriter(buildModel, buildFile).persist();
    }

    if (check || all) {
      System.err.println("Training checker");
      opennlp.model.EventStream kes = new ParserEventStream(new ParseSampleStream(new PlainTextByLineStream(new java.io.FileReader(inFile))), rules, ParserEventTypeEnum.CHECK);
      AbstractModel checkModel = train(kes, iterations, cutoff);
      System.out.println("Saving the check model as: " + checkFile);
      new opennlp.maxent.io.SuffixSensitiveGISModelWriter(checkModel, checkFile).persist();
    }
  }
}
