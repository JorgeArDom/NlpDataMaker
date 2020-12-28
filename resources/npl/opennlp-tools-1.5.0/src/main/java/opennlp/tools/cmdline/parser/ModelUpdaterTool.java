/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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

package opennlp.tools.cmdline.parser;

import java.io.File;
import java.io.IOException;

import opennlp.tools.cmdline.BasicTrainingParameters;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.ParserModel;
import opennlp.tools.util.ObjectStream;

/** 
 * Abstract base class for tools which update the parser model.
 */
abstract class ModelUpdaterTool implements CmdLineTool {

  protected abstract ParserModel trainAndUpdate(ParserModel originalModel,
      ObjectStream<Parse> parseSamples, BasicTrainingParameters parameters)
      throws IOException;

  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " -data training.file -model parser.model";
  }
  
  public final void run(String[] args) {

    if (args.length < 8) {
      System.out.println(getHelp());
      throw new TerminateToolException(1);
    }
    
    BasicTrainingParameters parameters = new BasicTrainingParameters(args);
    
    // Load model to be updated
    File modelFile = new File(CmdLineUtil.getParameter("-model", args));
    ParserModel originalParserModel = new ParserModelLoader().load(modelFile);

    ObjectStream<Parse> parseSamples = ParserTrainerTool.openTrainingData(new File(CmdLineUtil.getParameter("-data", args)), 
        parameters.getEncoding());
    
    ParserModel updatedParserModel;
    try {
      updatedParserModel = trainAndUpdate(originalParserModel,
          parseSamples, parameters);
    }
    catch (IOException e) {
      CmdLineUtil.printTrainingIoError(e);
      throw new TerminateToolException(-1);
    }
    finally {
      try {
        parseSamples.close();
      } catch (IOException e) {
        // sorry that this can fail
      }
    }
    
    CmdLineUtil.writeModel("parser", modelFile, updatedParserModel);
  }
}
