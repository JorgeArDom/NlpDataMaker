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

package opennlp.tools.cmdline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import opennlp.tools.cmdline.ArgumentParser.OptionalParameter;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;

import org.junit.Test;

public class ArgumentParserTest {

  interface ZeroMethods {
  }
  
  @Test(expected = IllegalArgumentException.class)
  public void testZeroMethods() {
    ArgumentParser.createUsage(ZeroMethods.class);
  }
  
  interface InvalidMethodName {
    String invalidMethodName();
  }
  
  @Test(expected = IllegalArgumentException.class)
  public void testInvalidMethodName() {
    ArgumentParser.createUsage(InvalidMethodName.class);
  }
  
  interface InvalidReturnType {
    Exception getTest();
  }
  
  @Test(expected = IllegalArgumentException.class)
  public void testInvalidReturnType() {
    ArgumentParser.createUsage(InvalidReturnType.class);
  }
  
  interface SimpleArguments {
    
    @ParameterDescription(valueName = "charset")
    String getEncoding();
    
    @ParameterDescription(valueName = "num")
    @OptionalParameter(defaultValue = "100")
    Integer getIterations();
    
    @OptionalParameter
    Integer getCutoff();
    
    @ParameterDescription(valueName = "true|false")
    @OptionalParameter(defaultValue = "true")
    Boolean getAlphaNumOpt();
  }
  
  @Test
  public void testSimpleArguments() {
    
    String argsString = "-encoding UTF-8 -alphaNumOpt false";
    
    SimpleArguments args = ArgumentParser.parse(argsString.split(" "), SimpleArguments.class);
    
    assertEquals("UTF-8", args.getEncoding());
    assertEquals(Integer.valueOf(100), args.getIterations());
    assertEquals(null, args.getCutoff());
    assertEquals(false, args.getAlphaNumOpt());
  }
  
  @Test
  public void testSimpleArgumentsMissingEncoding() {
    String argsString = "-alphaNumOpt false";
    
    assertFalse(ArgumentParser.validateArguments(argsString.split(" "), SimpleArguments.class));
    assertNull(ArgumentParser.parse(argsString.split(" "), SimpleArguments.class));
  }
  
  @Test
  public void testSimpleArgumentsUsage() {
    
    String usage = "-encoding charset [-iterations num] [-alphaNumOpt true|false]";
    assertEquals(usage, ArgumentParser.createUsage(SimpleArguments.class));
  }
}
