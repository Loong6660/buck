/*
 * Copyright 2014-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.cxx;

import com.facebook.buck.testutil.integration.DebuggableTemporaryFolder;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.util.environment.Platform;

import static org.hamcrest.Matchers.is;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assume.assumeThat;

import java.io.IOException;

public class CxxLibraryIntegrationTest {

  @Rule
  public DebuggableTemporaryFolder tmp = new DebuggableTemporaryFolder();

  @Test
  public void exportedPreprocessorFlagsApplyToBothTargetAndDependents() throws IOException {
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this, "exported_preprocessor_flags", tmp);
    workspace.setUp();
    workspace.runBuckBuild("//:main").assertSuccess();
  }

  @Test
  public void appleLibraryBuildsOnApplePlatform() throws IOException {
    assumeThat(Platform.detect(), is(Platform.MACOS));

    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this, "apple_cxx_library", tmp);
    workspace.setUp();
    workspace.runBuckBuild("//:main#iphonesimulator-i386").assertSuccess();
  }

}