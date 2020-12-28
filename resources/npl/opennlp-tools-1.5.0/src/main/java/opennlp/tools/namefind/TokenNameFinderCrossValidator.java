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


package opennlp.tools.namefind;

import java.io.IOException;
import java.util.Collections;

import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.eval.CrossValidationPartitioner;
import opennlp.tools.util.eval.FMeasure;

public class TokenNameFinderCrossValidator {

  private final String languageCode;
  private final int cutoff;
  private final int iterations;
  private FMeasure fmeasure = new FMeasure();
  
  public TokenNameFinderCrossValidator(String languageCode, int cutoff, int iterations) {
    this.languageCode = languageCode;
    this.cutoff = cutoff;
    this.iterations = iterations;
  }
  
  public void evaluate(ObjectStream<NameSample> samples, int nFolds) throws IOException,
      InvalidFormatException, IOException {
    CrossValidationPartitioner<NameSample> partitioner = 
        new CrossValidationPartitioner<NameSample>(samples, nFolds);
    
    while (partitioner.hasNext()) {
      
      CrossValidationPartitioner.TrainingSampleStream<NameSample> trainingSampleStream =
          partitioner.next();
      
      TokenNameFinderModel model = NameFinderME.train(languageCode, null, trainingSampleStream,
          Collections.<String, Object>emptyMap(), cutoff, iterations);
       
       // do testing
       TokenNameFinderEvaluator evaluator = new TokenNameFinderEvaluator(
           new NameFinderME(model));

       evaluator.evaluate(trainingSampleStream.getTestSampleStream());
       
       fmeasure.mergeInto(evaluator.getFMeasure());
     }
  }
  
  public FMeasure getFMeasure() {
    return fmeasure;
  }
}
