/*
 * Copyright 2015-present Facebook, Inc.
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

import static org.hamcrest.MatcherAssert.assertThat;

import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.FakeBuildRule;
import com.facebook.buck.rules.SourcePathResolver;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import org.hamcrest.Matchers;
import org.junit.Test;

public class NativeLinkablesTest {

  private static class FakeNativeLinkable extends FakeBuildRule implements NativeLinkable {

    private final NativeLinkableInput nativeLinkableInput;
    private final Optional<Linker.LinkableDepType> preferredLinkage;

    public FakeNativeLinkable(
        String target,
        SourcePathResolver resolver,
        NativeLinkableInput nativeLinkableInput,
        Optional<Linker.LinkableDepType> preferredLinkage,
        BuildRule... deps) {
      super(target, resolver, deps);
      this.nativeLinkableInput = nativeLinkableInput;
      this.preferredLinkage = preferredLinkage;
    }

    @Override
    public NativeLinkableInput getNativeLinkableInput(
        CxxPlatform cxxPlatform,
        Linker.LinkableDepType type) {
      return nativeLinkableInput;
    }

    @Override
    public Optional<Linker.LinkableDepType> getPreferredLinkage(CxxPlatform cxxPlatform) {
      return preferredLinkage;
    }

  }

  /**
   * Consider the following graph of C/C++ library dependencies of a python binary rule.  In this
   * case we dynamically link all C/C++ library deps except for ones that request static linkage
   * via the `force_static` parameter (e.g. library `C` in this example):
   *
   *           Python Binary
   *                |
   *                |
   *           A (shared)
   *               / \
   *              /   \
   *             /     \
   *            /       \
   *       B (shared)   ...
   *         /    \
   *        /      \
   *       /      ...
   *   C (static)
   *       \
   *        \
   *         \
   *          \
   *        D (shared)
   *
   * Handling this force static dep is tricky -- we need to make sure we *only* statically link it
   * into the shared lib `B` and that it does *not* contribute to the link line formed for `A`.
   * What's more, we need to make sure `D` still contributes to the link for `A`.
   *
   */
  @Test
  public void doNotPullInStaticLibsAcrossSharedLibs() {
    SourcePathResolver resolver = new SourcePathResolver(new BuildRuleResolver());

    BuildRule d = new FakeNativeLinkable(
        "//:d",
        resolver,
        NativeLinkableInput.builder()
            .addArgs("d")
            .build(),
        Optional.<Linker.LinkableDepType>absent());

    BuildRule c = new FakeNativeLinkable(
        "//:c",
        resolver,
        NativeLinkableInput.builder()
            .addArgs("c")
            .build(),
        Optional.of(Linker.LinkableDepType.STATIC),
        d);

    BuildRule b = new FakeNativeLinkable(
        "//:b",
        resolver,
        NativeLinkableInput.builder()
            .addArgs("b")
            .build(),
        Optional.<Linker.LinkableDepType>absent(),
        c);

    BuildRule a = new FakeNativeLinkable(
        "//:a",
        resolver,
        NativeLinkableInput.builder()
            .addArgs("a")
            .build(),
        Optional.<Linker.LinkableDepType>absent(),
        b);

    // Collect the transitive native linkable input for the top-level rule (e.g. the imaginary
    // python binary rule) and verify that we do *not* pull in input from `C`.
    NativeLinkableInput inputForTop =
        NativeLinkables.getTransitiveNativeLinkableInput(
            CxxPlatformUtils.DEFAULT_PLATFORM,
            ImmutableList.of(a),
            Linker.LinkableDepType.SHARED,
            /* reverse */ false);
    assertThat(inputForTop.getArgs(), Matchers.containsInAnyOrder("a", "b", "d"));
    assertThat(inputForTop.getArgs(), Matchers.not(Matchers.contains("c")));

    // However, when collecting the transitive native linkable input for `B`, we *should* have
    // input from `C`.
    NativeLinkableInput inputForB =
        NativeLinkables.getTransitiveNativeLinkableInput(
            CxxPlatformUtils.DEFAULT_PLATFORM,
            ImmutableList.of(c),
            Linker.LinkableDepType.SHARED,
            /* reverse */ false);
    assertThat(inputForB.getArgs(), Matchers.containsInAnyOrder("c", "d"));
  }

}
