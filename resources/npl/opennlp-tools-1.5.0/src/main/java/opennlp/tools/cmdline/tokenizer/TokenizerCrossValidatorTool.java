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

package opennlp.tools.cmdline.tokenizer;

import java.io.File;
import java.io.IOException;

import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.tokenize.TokenizerCrossValidator;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.eval.FMeasure;

public final class TokenizerCrossValidatorTool implements CmdLineTool {

  public String getName() {
    return "TokenizerCrossValidator";
  }
  
  public String getShortDescription() {
    return "10-fold cross validator for the learnable tokenizer";
  }
  
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " " + TrainingParameters.getParameterUsage() +
        " -data trainData\n" + 
        TrainingParameters.getDescription() + "\n"+
        "-data trainingData      training data used for cross validation";
  }

  public void run(String[] args) {
    if (args.length < 6) {
      System.out.println(getHelp());
      throw new TerminateToolException(1);
    }
    
    TrainingParameters parameters = new TrainingParameters(args);
    
    if(!parameters.isValid()) {
      System.out.println(getHelp());
      throw new TerminateToolException(1);
    }
    
    File trainingDataInFile = new File(CmdLineUtil.getParameter("-data", args));
    CmdLineUtil.checkInputFile("Training Data", trainingDataInFile);
    
    ObjectStream<TokenSample> sampleStream =
        TokenizerTrainerTool.openSampleData("Training Data",
        trainingDataInFile, parameters.getEncoding());
    
    TokenizerCrossValidator validator =
        new opennlp.tools.tokenize.TokenizerCrossValidator(
        parameters.getLanguage(), parameters.isAlphaNumericOptimizationEnabled());
      
    try {
      validator.evaluate(sampleStream, 10);
    }
    catch (IOException e) {
      CmdLineUtil.printTrainingIoError(e);
      throw new TerminateToolException(-1);
    }
    finally {
      try {
        sampleStream.close();
      } catch (IOException e) {
        // sorry that this can fail
      }
    }
    
    FMeasure result = validator.getFMeasure();
    
    System.out.println(result.toString());
  }
}
