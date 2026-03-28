/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package biz.paluch.dap;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.VersionOption;
import biz.paluch.dap.state.Cache;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;

/**
 * @author Mark Paluch
 */
abstract class SuggestionProviderUtil {

	static List<SuggestionProviderUtil.ArtifactVersion> findOptions(ArtifactId artifactId, Cache cache) {

		List<VersionOption> versions = cache.getVersionOptions(artifactId, false);
		List<SuggestionProviderUtil.ArtifactVersion> result = new java.util.ArrayList<>();

		for (VersionOption versionOption : versions) {
			result.add(new ArtifactVersion(artifactId, versionOption));
		}

		return result;
	}

	static void addSuggestions(Collection<SuggestionProviderUtil.ArtifactVersion> versions, CompletionResultSet result,
			Function<ArtifactId, String> toString) {

		double priority = versions.size();

		for (SuggestionProviderUtil.ArtifactVersion option : versions) {

			String typeText = toString.apply(option.artifactId());
			VersionOption version = option.version();

			LookupElementBuilder element = LookupElementBuilder.create(version.version().toString()).withTypeText(typeText)
					.withBoldness(version.isReleaseVersion() && !version.isPreview());

			if (version.releaseDate() != null) {
				element = element.withTailText(" (" + version.releaseDate().toLocalDate() + ")", true);
			}

			result.addElement(PrioritizedLookupElement.withPriority(element, priority--));
		}
	}

	record ArtifactVersion(ArtifactId artifactId, VersionOption version) implements Comparable<ArtifactVersion> {

		@Override
		public int compareTo(ArtifactVersion o) {
			return version.compareTo(o.version());
		}
	}
}
